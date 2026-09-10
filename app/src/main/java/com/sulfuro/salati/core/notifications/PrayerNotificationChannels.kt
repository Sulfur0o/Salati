package com.sulfuro.salati.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.sulfuro.salati.R

object PrayerNotificationChannels {
    const val CHANNEL_ID_VIBRATE = "salati_prayer_alerts_vibrate"
    const val CHANNEL_ID_SILENT = "salati_prayer_alerts_silent"
    const val CHANNEL_ID_SOUND_VIBRATE = "salati_prayer_alerts_sound_vibrate"
    const val CHANNEL_ID_SOUND_ONLY = "salati_prayer_alerts_sound_only"

    /**
     * Carries the ongoing notification while a downloaded adhan plays. Low importance and
     * silent on purpose: the recording is the alert, so the notification must not add a
     * second one on top of it.
     */
    const val CHANNEL_ID_ADHAN_PLAYBACK = "salati_adhan_playback"
    val VIBRATION_PATTERN = longArrayOf(0, 500, 200, 500)

    /**
     * The channel definitions, kept as data so the create-and-relabel pass below has a
     * single place to read from.
     *
     * @param importance only takes effect the first time a channel is registered; after
     *   that the user owns it, and Android ignores an app trying to change it.
     */
    private data class ChannelSpec(
        val id: String,
        val nameRes: Int,
        val descriptionRes: Int,
        val importance: Int,
        val vibrate: Boolean,
        val sound: Boolean
    )

    private val specs = listOf(
        ChannelSpec(
            id = CHANNEL_ID_VIBRATE,
            nameRes = R.string.notification_channel_vibrate_name,
            descriptionRes = R.string.notification_channel_vibrate_description,
            importance = NotificationManager.IMPORTANCE_HIGH,
            vibrate = true,
            sound = false
        ),
        ChannelSpec(
            id = CHANNEL_ID_SILENT,
            nameRes = R.string.notification_channel_silent_name,
            descriptionRes = R.string.notification_channel_silent_description,
            importance = NotificationManager.IMPORTANCE_LOW,
            vibrate = false,
            sound = false
        ),
        ChannelSpec(
            id = CHANNEL_ID_SOUND_VIBRATE,
            nameRes = R.string.notification_channel_sound_vibrate_name,
            descriptionRes = R.string.notification_channel_sound_vibrate_description,
            importance = NotificationManager.IMPORTANCE_HIGH,
            vibrate = true,
            sound = true
        ),
        ChannelSpec(
            id = CHANNEL_ID_SOUND_ONLY,
            nameRes = R.string.notification_channel_sound_name,
            descriptionRes = R.string.notification_channel_sound_description,
            importance = NotificationManager.IMPORTANCE_HIGH,
            vibrate = false,
            sound = true
        ),
        ChannelSpec(
            id = CHANNEL_ID_ADHAN_PLAYBACK,
            nameRes = R.string.notification_channel_adhan_name,
            descriptionRes = R.string.notification_channel_adhan_description,
            importance = NotificationManager.IMPORTANCE_LOW,
            vibrate = false,
            sound = false
        )
    )

    /**
     * Registers the prayer channels, and re-applies their labels every time.
     *
     * The labels are re-applied deliberately. A channel's name and description are the
     * only parts of it an app may still change after creation, and they are what the user
     * reads in Android's own notification settings - so leaving them at whatever language
     * was in use when the app first launched would strand that screen in English while
     * the rest of the app spoke Arabic, French or Dutch. Everything else here is
     * first-registration only, because after that those choices belong to the user.
     *
     * @param context supplies the locale the labels are resolved in, so pass an Activity
     *   or another context that carries the app's chosen language.
     */
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        for (spec in specs) {
            manager.createNotificationChannel(
                NotificationChannel(
                    spec.id,
                    context.getString(spec.nameRes),
                    spec.importance
                ).apply {
                    description = context.getString(spec.descriptionRes)
                    enableVibration(spec.vibrate)
                    if (spec.vibrate) {
                        vibrationPattern = VIBRATION_PATTERN
                    }
                    if (spec.sound) {
                        setSound(defaultSoundUri, audioAttributes)
                    } else {
                        setSound(null, null)
                    }
                }
            )
        }
    }

    fun channelFor(vibrateEnabled: Boolean, soundEnabled: Boolean): String {
        return when {
            soundEnabled && vibrateEnabled -> CHANNEL_ID_SOUND_VIBRATE
            soundEnabled -> CHANNEL_ID_SOUND_ONLY
            vibrateEnabled -> CHANNEL_ID_VIBRATE
            else -> CHANNEL_ID_SILENT
        }
    }
}
