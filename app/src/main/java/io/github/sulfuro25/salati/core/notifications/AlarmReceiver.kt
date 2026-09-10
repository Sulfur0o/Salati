package io.github.sulfuro25.salati.core.notifications

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.core.audio.AdhanPlaybackService

class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "onReceive: action=$action")

        if (action != AlarmScheduler.ACTION_PRAYER_ALARM ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) {
            return
        }

        val prayerName = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER_NAME) ?: ""
            val kind = intent.getStringExtra(AlarmScheduler.EXTRA_NOTIFICATION_KIND)
            val isPreReminder = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_PRE_REMINDER, false)
            val vibrateEnabled = intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE_ENABLED, true)
            val soundEnabled = intent.getBooleanExtra(AlarmScheduler.EXTRA_SOUND_ENABLED, false)
            val adhanSoundId = intent.getStringExtra(AlarmScheduler.EXTRA_ADHAN_SOUND_ID)
            val alarmTime = intent.getLongExtra(
                AlarmScheduler.EXTRA_ALARM_TIME,
                System.currentTimeMillis()
            )
            val alarmRequestCode = intent.getIntExtra(
                AlarmScheduler.EXTRA_ALARM_REQUEST_CODE,
                prayerName.hashCode()
            )

            val resolvedKind = kind ?: when {
                prayerName == "white_days" -> AlarmScheduler.KIND_WHITE_DAYS
                isPreReminder -> AlarmScheduler.KIND_PRE_PRAYER
                else -> AlarmScheduler.KIND_PRAYER
            }

            if (prayerName.isNotEmpty()) {
                if (resolvedKind == AlarmScheduler.KIND_PRAYER) {
                    PrayerSilentModeScheduler.scheduleForPrayer(
                        context = context.applicationContext,
                        prayerRequestCode = alarmRequestCode,
                        prayerAtMillis = alarmTime,
                        enabled = intent.getBooleanExtra(
                            AlarmScheduler.EXTRA_SILENT_MODE_AUTOMATION_ENABLED,
                            false
                        ),
                        minutesAfterAdhan = intent.getIntExtra(
                            AlarmScheduler.EXTRA_SILENT_MODE_MINUTES_AFTER_ADHAN,
                            0
                        ),
                        durationMinutes = intent.getIntExtra(
                            AlarmScheduler.EXTRA_SILENT_MODE_DURATION_MINUTES,
                            20
                        )
                    )
                }
                showNotification(
                    context = context,
                    prayerName = prayerName,
                    kind = resolvedKind,
                    vibrateEnabled = vibrateEnabled,
                    soundEnabled = soundEnabled,
                    adhanSoundId = adhanSoundId
                )
            val widgetPendingResult = goAsync()
            runCatching {
                io.github.sulfuro25.salati.widget.SalatiAppWidgetProvider.updateAllWidgets(context, widgetPendingResult)
            }.onFailure {
                widgetPendingResult.finish()
            }
        }
    }

    internal fun localizedPrayerName(context: Context, key: String): String {
        val resId = when (key.lowercase(java.util.Locale.ROOT)) {
            "fajr" -> R.string.prayer_fajr
            "sunrise" -> R.string.prayer_sunrise
            "dhuhr" -> R.string.prayer_dhuhr
            "asr" -> R.string.prayer_asr
            "maghrib" -> R.string.prayer_maghrib
            "isha" -> R.string.prayer_isha
            "white_days" -> R.string.event_white_day
            else -> null
        }
        return if (resId != null) {
            context.getString(resId)
        } else {
            key.replaceFirstChar { it.uppercase(java.util.Locale.ROOT) }
        }
    }

    private fun showNotification(
        context: Context,
        prayerName: String,
        kind: String,
        vibrateEnabled: Boolean,
        soundEnabled: Boolean,
        adhanSoundId: String? = null
    ) {
        PrayerNotificationChannels.create(context)
        if (!canPostNotifications(context)) {
            Log.w(TAG, "Notification permission is unavailable; alarm notification suppressed")
            return
        }

        val displayPrayerName = localizedPrayerName(context, prayerName)

        val channelId = PrayerNotificationChannels.channelFor(vibrateEnabled, soundEnabled)

        val title = when (kind) {
            AlarmScheduler.KIND_WHITE_DAYS -> context.getString(R.string.notification_white_days_title)
            AlarmScheduler.KIND_PRE_PRAYER -> context.getString(R.string.notification_pre_prayer_title, displayPrayerName)
            AlarmScheduler.KIND_PRAYER -> context.getString(R.string.notification_prayer_title, displayPrayerName)
            else -> {
                Log.w(TAG, "Unknown notification kind: $kind, suppressing notification")
                return
            }
        }
        
        val contentText = when (kind) {
            AlarmScheduler.KIND_WHITE_DAYS -> context.getString(R.string.notification_white_days_message)
            AlarmScheduler.KIND_PRE_PRAYER -> context.getString(R.string.notification_pre_prayer_message)
            AlarmScheduler.KIND_PRAYER -> context.getString(R.string.notification_prayer_message, displayPrayerName)
            else -> return
        }

        val notificationId = when (kind) {
            AlarmScheduler.KIND_WHITE_DAYS -> 300
            AlarmScheduler.KIND_PRE_PRAYER -> 200 + prayerName.hashCode()
            else -> 100 + prayerName.hashCode()
        }

        val alert = PrayerAlertNotification.Content(
            notificationId = notificationId,
            channelId = channelId,
            title = title,
            text = contentText,
            soundEnabled = soundEnabled,
            vibrateEnabled = vibrateEnabled
        )

        // A chosen adhan replaces the notification tone for the prayer itself. Pre-prayer
        // reminders and white-day nudges keep the short tone: they are a heads-up, not a
        // call to prayer, and a full recitation would misrepresent them.
        //
        // The alert travels with the request, and the service owns it from the moment it
        // accepts one: while the recitation plays it shows its own notification with a
        // stop button, and if playback never starts - focus denied, the file gone or
        // undecodable - it posts this one instead. Whether the audio works is not known
        // until long after this receiver has returned, so handing the alert over is the
        // only way the prayer cannot end up with no notification at all.
        if (kind == AlarmScheduler.KIND_PRAYER &&
            !adhanSoundId.isNullOrBlank() &&
            AdhanPlaybackService.start(context, adhanSoundId, displayPrayerName, alert)
        ) {
            return
        }

        PrayerAlertNotification.post(context, alert)
    }

    internal fun canPostNotifications(context: Context): Boolean {
        val runtimePermissionGranted = if (android.os.Build.VERSION.SDK_INT >= 33) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return runtimePermissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
