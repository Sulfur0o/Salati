package io.github.sulfuro25.salati.core.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.sulfuro25.salati.MainActivity
import io.github.sulfuro25.salati.R

class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "onReceive: action=$action")

        if (action == AlarmScheduler.ACTION_PRAYER_ALARM) {
            val prayerName = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER_NAME) ?: ""
            val kind = intent.getStringExtra(AlarmScheduler.EXTRA_NOTIFICATION_KIND)
            val isPreReminder = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_PRE_REMINDER, false)
            val vibrateEnabled = intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE_ENABLED, true)
            val soundEnabled = intent.getBooleanExtra(AlarmScheduler.EXTRA_SOUND_ENABLED, false)
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
                showNotification(context, prayerName, resolvedKind, vibrateEnabled, soundEnabled)
                runCatching { io.github.sulfuro25.salati.widget.SalatiAppWidgetProvider.updateAllWidgets(context) }
            }
        }
    }

    private fun showNotification(
        context: Context,
        prayerName: String,
        kind: String,
        vibrateEnabled: Boolean,
        soundEnabled: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        PrayerNotificationChannels.create(context)
        if (!canPostNotifications(context)) {
            Log.w(TAG, "Notification permission is unavailable; alarm notification suppressed")
            return
        }

        val channelId = PrayerNotificationChannels.channelFor(vibrateEnabled, soundEnabled)

        val title = when (kind) {
            AlarmScheduler.KIND_WHITE_DAYS -> context.getString(R.string.notification_white_days_title)
            AlarmScheduler.KIND_PRE_PRAYER -> context.getString(R.string.notification_pre_prayer_title, prayerName)
            AlarmScheduler.KIND_PRAYER -> context.getString(R.string.notification_prayer_title, prayerName)
            else -> {
                Log.w(TAG, "Unknown notification kind: $kind, suppressing notification")
                return
            }
        }
        
        val contentText = when (kind) {
            AlarmScheduler.KIND_WHITE_DAYS -> context.getString(R.string.notification_white_days_message)
            AlarmScheduler.KIND_PRE_PRAYER -> context.getString(R.string.notification_pre_prayer_message)
            AlarmScheduler.KIND_PRAYER -> context.getString(R.string.notification_prayer_message, prayerName)
            else -> return
        }

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            builder.setSound(
                if (soundEnabled) android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                else null
            )
            builder.setVibrate(
                if (vibrateEnabled) PrayerNotificationChannels.VIBRATION_PATTERN
                else longArrayOf(0L)
            )
        }

        val notificationId = when (kind) {
            AlarmScheduler.KIND_WHITE_DAYS -> 300
            AlarmScheduler.KIND_PRE_PRAYER -> 200 + prayerName.hashCode()
            else -> 100 + prayerName.hashCode()
        }
        try {
            notificationManager.notify(notificationId, builder.build())
            Log.d(TAG, "Notification shown: $title on channel $channelId")
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Notification permission changed before delivery", securityException)
        }
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
