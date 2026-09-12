package com.sulfuro.salati.core.alerts

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sulfuro.salati.MainActivity
import com.sulfuro.salati.R
import com.sulfuro.salati.core.audio.AdhanPlaybackService

/**
 * The plain "it is time for this prayer" notification.
 *
 * It lives here rather than inside [AlarmReceiver] because two callers need to be able to
 * post it. The receiver posts it directly when no adhan is configured; and
 * [com.sulfuro.salati.core.audio.AdhanPlaybackService] posts it when a recitation
 * was meant to play but could not - the alert must not disappear because the audio failed.
 *
 * The whole description travels as intent extras rather than being rebuilt from the prayer
 * key, so that the text the user sees is resolved once, in the receiver, against the
 * resources that were current at the time.
 */
object PrayerAlertNotification {

    private const val TAG = "PrayerAlertNotification"

    const val EXTRA_NOTIFICATION_ID = "fallback_notification_id"
    const val EXTRA_CHANNEL_ID = "fallback_channel_id"
    const val EXTRA_TITLE = "fallback_title"
    const val EXTRA_TEXT = "fallback_text"
    const val EXTRA_SOUND_ENABLED = "fallback_sound_enabled"
    const val EXTRA_VIBRATE_ENABLED = "fallback_vibrate_enabled"
    const val EXTRA_PLAY_ADHAN_ID = "fallback_play_adhan_id"
    const val EXTRA_PLAY_PRAYER_LABEL = "fallback_play_prayer_label"

    /**
     * Everything needed to post the alert, so a caller that only forwards it (the playback
     * service) never has to know how the text was chosen.
     */
    data class Content(
        val notificationId: Int,
        val channelId: String,
        val title: String,
        val text: String,
        val soundEnabled: Boolean,
        val vibrateEnabled: Boolean,
        /** When set, the notification offers a Play action that starts adhan playback. */
        val playAdhanId: String? = null,
        val playPrayerLabel: String? = null
    ) {
        // Every field travels. Leaving two of them out made this a value object whose
        // round trip silently returned something different from what went in.
        fun writeTo(intent: Intent): Intent = intent.apply {
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_SOUND_ENABLED, soundEnabled)
            putExtra(EXTRA_VIBRATE_ENABLED, vibrateEnabled)
            putExtra(EXTRA_PLAY_ADHAN_ID, playAdhanId)
            putExtra(EXTRA_PLAY_PRAYER_LABEL, playPrayerLabel)
        }

        companion object {
            /** Reads back what [writeTo] wrote, or null when the intent carries no alert. */
            fun readFrom(intent: Intent?): Content? {
                val channelId = intent?.getStringExtra(EXTRA_CHANNEL_ID) ?: return null
                val title = intent.getStringExtra(EXTRA_TITLE) ?: return null
                val text = intent.getStringExtra(EXTRA_TEXT) ?: return null
                if (!intent.hasExtra(EXTRA_NOTIFICATION_ID)) return null
                return Content(
                    notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0),
                    channelId = channelId,
                    title = title,
                    text = text,
                    soundEnabled = intent.getBooleanExtra(EXTRA_SOUND_ENABLED, false),
                    vibrateEnabled = intent.getBooleanExtra(EXTRA_VIBRATE_ENABLED, true),
                    playAdhanId = intent.getStringExtra(EXTRA_PLAY_ADHAN_ID),
                    playPrayerLabel = intent.getStringExtra(EXTRA_PLAY_PRAYER_LABEL)
                )
            }
        }
    }

    /** Posts [content], swallowing the permission race the way the receiver always has. */
    fun post(context: Context, content: Content) {
        PrayerNotificationChannels.create(context)

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, content.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val playAdhanId = content.playAdhanId
        if (!playAdhanId.isNullOrBlank()) {
            val playIntent = Intent(context, AdhanPlaybackService::class.java).apply {
                putExtra(AdhanPlaybackService.EXTRA_ADHAN_ID, playAdhanId)
                putExtra(
                    AdhanPlaybackService.EXTRA_PRAYER_LABEL,
                    content.playPrayerLabel.orEmpty()
                )
            }
            val playPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    content.notificationId,
                    playIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    context,
                    content.notificationId,
                    playIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            builder.addAction(0, context.getString(R.string.adhan_play), playPending)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Pre-O has no channels, so sound and vibration are properties of the
            // notification itself.
            builder.setSound(
                if (content.soundEnabled) {
                    android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_NOTIFICATION
                    )
                } else {
                    null
                }
            )
            builder.setVibrate(
                if (content.vibrateEnabled) PrayerNotificationChannels.VIBRATION_PATTERN
                else longArrayOf(0L)
            )
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(content.notificationId, builder.build())
            Log.d(TAG, "Notification shown: ${content.title} on channel ${content.channelId}")
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Notification permission changed before delivery", securityException)
        }
    }
}
