package com.sulfuro.salati.core.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sulfuro.salati.MainActivity
import com.sulfuro.salati.R
import com.sulfuro.salati.core.alerts.PrayerAlertNotification
import com.sulfuro.salati.core.alerts.PrayerNotificationChannels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays a downloaded adhan through to its end.
 *
 * A notification channel sound cannot do this job: the platform cuts those off after a
 * few seconds, which would clip a call to prayer mid-sentence. So the recording is played
 * by a foreground service instead, which also gives the user an ongoing notification with
 * a Stop button - important, because an adhan runs for minutes and someone in a meeting
 * needs to be able to end it in one tap.
 *
 * The service is started from [com.sulfuro.salati.core.alarms.AlarmReceiver]
 * while it is handling an exact alarm, which is one of the situations Android still allows
 * a background app to start a foreground service in.
 */
class AdhanPlaybackService : Service() {

    private var player: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null

    /**
     * The plain prayer notification to post if this recitation never reaches the speaker.
     *
     * The alarm receiver has already returned by the time playback can fail, so nothing
     * else is left to notice. Cleared the moment audio actually starts - and null for a
     * settings preview, which has no alert to fall back to.
     */
    private var fallbackAlert: PrayerAlertNotification.Content? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || intent?.action == ACTION_STOP_LEGACY) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        val adhanId = intent?.getStringExtra(EXTRA_ADHAN_ID).orEmpty()
        val prayerLabel = intent?.getStringExtra(EXTRA_PRAYER_LABEL).orEmpty()
        fallbackAlert = PrayerAlertNotification.Content.readFrom(intent)

        // The notification has to go up before anything else can fail, or the system
        // kills the service for starting foreground too slowly.
        if (!startForegroundCompat(buildNotification(prayerLabel))) {
            return START_NOT_STICKY
        }

        val file = AdhanAudioStore.fileFor(this, adhanId)
        if (adhanId.isBlank() || !file.isFile) {
            Log.w(TAG, "No downloaded recording for '$adhanId'; nothing to play")
            failToNotification()
            return START_NOT_STICKY
        }

        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus denied; not playing the adhan over whatever holds it")
            failToNotification()
            return START_NOT_STICKY
        }

        val accepted = runCatching {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(alarmAudioAttributes())
                setDataSource(file.absolutePath)
                setOnCompletionListener { stopSelfCleanly() }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Playback error ($what/$extra)")
                    // Before the first frame this is still a failed alert; after it, the
                    // user has heard the adhan and a second notification would be noise.
                    failToNotification()
                    true
                }
                setOnPreparedListener { prepared ->
                    // Audio is now actually coming out: the service's own notification is
                    // the alert, so the fallback is spent.
                    fallbackAlert = null
                    playingId.value = adhanId
                    prepared.start()
                }
                // Async: preparing reads and parses the file, and this runs on the main
                // thread. A blocking prepare here would stall the UI of an app the user
                // may well have open at prayer time.
                prepareAsync()
            }
            true
        }.getOrElse { cause ->
            Log.w(TAG, "Could not play ${file.name}", cause)
            false
        }

        if (!accepted) {
            failToNotification()
        }
        // Not sticky: a prayer alert that the system revived an hour later would be worse
        // than one that simply did not come back.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releasePlayer()
        abandonAudioFocus()
        playingId.value = null
        super.onDestroy()
    }

    /**
     * Playback is not going to happen. Posts the alert the caller entrusted to this
     * service, then shuts down - so a prayer whose adhan failed still notifies.
     */
    private fun failToNotification() {
        fallbackAlert?.let { alert ->
            fallbackAlert = null
            PrayerAlertNotification.post(this, alert)
        }
        stopSelfCleanly()
    }

    private fun stopSelfCleanly() {
        playingId.value = null
        fallbackAlert = null
        releasePlayer()
        abandonAudioFocus()
        stopForegroundCompat()
        stopSelf()
    }

    private fun releasePlayer() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
    }

    private fun alarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        // The adhan is an alarm, not media: it should ride the alarm volume slider and
        // not be silenced by the user pausing their music.
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun requestAudioFocus(): Boolean {
        val manager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        audioManager = manager
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(alarmAudioAttributes())
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS) stopSelfCleanly()
                }
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            val listener = AudioManager.OnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) stopSelfCleanly()
            }
            legacyFocusListener = listener
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                listener,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            legacyFocusListener?.let { manager.abandonAudioFocus(it) }
        }
        focusRequest = null
        legacyFocusListener = null
        audioManager = null
    }

    private fun buildNotification(prayerLabel: String): Notification {
        PrayerNotificationChannels.create(this)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, AdhanPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (prayerLabel.isNotBlank()) {
            getString(R.string.notification_prayer_title, prayerLabel)
        } else {
            getString(R.string.app_name)
        }

        return NotificationCompat.Builder(this, PrayerNotificationChannels.CHANNEL_ID_ADHAN_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(getString(R.string.adhan_playing))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.adhan_stop), stop)
            .build()
    }

    private fun startForegroundCompat(notification: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed; falling back to alert notification", e)
            fallbackAlert?.let { alert ->
                fallbackAlert = null
                PrayerAlertNotification.post(this, alert)
            }
            stopSelfCleanly()
            false
        }
    }

    private fun stopForegroundCompat() {
        // STOP_FOREGROUND_REMOVE has existed since API 24, which is this app's minimum.
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val TAG = "AdhanPlaybackService"
        private const val NOTIFICATION_ID = 400

        private val playingId = MutableStateFlow<String?>(null)

        /**
         * The recording currently playing, or null.
         *
         * Playback ends on its own when the adhan finishes, and the settings picker has
         * no other way to learn that - without this it would go on showing a stop button
         * for a recording that ended minutes ago.
         */
        val nowPlayingId: StateFlow<String?> = playingId.asStateFlow()

        const val ACTION_STOP = "com.sulfuro.salati.action.STOP_ADHAN"
        private const val ACTION_STOP_LEGACY = "io.github.sulfuro25.salati.action.STOP_ADHAN"
        const val EXTRA_ADHAN_ID = "adhan_id"
        const val EXTRA_PRAYER_LABEL = "prayer_label"

        /**
         * Hands a recitation to the service, returning false only when the platform
         * refuses to let a background app start a foreground service at all.
         *
         * A true return means the request was accepted, not that audio will be heard:
         * focus can still be denied and the file can still fail to decode, both of them
         * after this call and after the alarm receiver has finished. That is what
         * [alert] is for - the service posts it if the recitation never starts, so the
         * prayer is never left with no notification. Callers with nothing to fall back
         * on, such as the settings preview, pass null and the failure is silent.
         */
        fun start(
            context: Context,
            adhanId: String,
            prayerLabel: String,
            alert: PrayerAlertNotification.Content? = null
        ): Boolean {
            val intent = Intent(context, AdhanPlaybackService::class.java).apply {
                putExtra(EXTRA_ADHAN_ID, adhanId)
                putExtra(EXTRA_PRAYER_LABEL, prayerLabel)
                alert?.writeTo(this)
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (cause: Exception) {
                // ForegroundServiceStartNotAllowedException on API 31+, among others.
                Log.w(TAG, "Could not start adhan playback", cause)
                false
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, AdhanPlaybackService::class.java))
            }
        }
    }
}
