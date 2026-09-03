package io.github.sulfuro25.salati.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object PrayerNotificationChannels {
    const val CHANNEL_ID_VIBRATE = "salati_prayer_alerts_vibrate"
    const val CHANNEL_ID_SILENT = "salati_prayer_alerts_silent"
    const val CHANNEL_ID_SOUND_VIBRATE = "salati_prayer_alerts_sound_vibrate"
    const val CHANNEL_ID_SOUND_ONLY = "salati_prayer_alerts_sound_only"
    val VIBRATION_PATTERN = longArrayOf(0, 500, 200, 500)

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (manager.getNotificationChannel(CHANNEL_ID_VIBRATE) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_VIBRATE,
                    "Prayer Reminders (Vibrate)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for prayer times with vibration alerts"
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                    setSound(null, null)
                }
            )
        }

        if (manager.getNotificationChannel(CHANNEL_ID_SILENT) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_SILENT,
                    "Prayer Reminders (Silent)",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Silent notifications for prayer times"
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }

        if (manager.getNotificationChannel(CHANNEL_ID_SOUND_VIBRATE) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_SOUND_VIBRATE,
                    "Prayer Reminders (Sound + Vibrate)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for prayer times with sound and vibration alerts"
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                    setSound(defaultSoundUri, audioAttributes)
                }
            )
        }

        if (manager.getNotificationChannel(CHANNEL_ID_SOUND_ONLY) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_SOUND_ONLY,
                    "Prayer Reminders (Sound)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for prayer times with a sound alert"
                    enableVibration(false)
                    setSound(defaultSoundUri, audioAttributes)
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
