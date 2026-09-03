package io.github.sulfuro25.salati.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

sealed interface ScheduleResult {
    data class Success(val alarm: RegisteredAlarm) : ScheduleResult
    data class Failure(val cause: Throwable) : ScheduleResult
}

sealed interface RestoreResult {
    data class Success(val alarm: RegisteredAlarm) : RestoreResult
    data class Failure(val cause: Throwable) : RestoreResult
}

interface AlarmRegistrar {
    fun scheduleAlarm(context: Context, alarm: PreparedAlarm, vibrateEnabled: Boolean, soundEnabled: Boolean = false): ScheduleResult
    fun restoreAlarm(context: Context, alarm: RegisteredAlarm): RestoreResult
    fun cancelAlarm(context: Context, alarm: RegisteredAlarm)
    fun cancelLegacyAlarm(context: Context, requestCode: Int)
}

internal enum class AlarmDelivery {
    EXACT,
    INEXACT
}

internal fun scheduleWithExactFallback(
    canScheduleExact: Boolean,
    scheduleExact: () -> Unit,
    scheduleInexact: () -> Unit,
    onExactFailure: (SecurityException) -> Unit = {}
): AlarmDelivery {
    if (!canScheduleExact) {
        scheduleInexact()
        return AlarmDelivery.INEXACT
    }
    return try {
        scheduleExact()
        AlarmDelivery.EXACT
    } catch (securityException: SecurityException) {
        onExactFailure(securityException)
        scheduleInexact()
        AlarmDelivery.INEXACT
    }
}

class SystemAlarmRegistrar : AlarmRegistrar {
    companion object {
        private const val TAG = "SystemAlarmRegistrar"
    }

    override fun scheduleAlarm(context: Context, alarm: PreparedAlarm, vibrateEnabled: Boolean, soundEnabled: Boolean): ScheduleResult {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: throw IllegalStateException("AlarmManager not available")

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmScheduler.ACTION_PRAYER_ALARM
                data = android.net.Uri.parse(alarm.uri)
                putExtra(AlarmScheduler.EXTRA_PRAYER_NAME, alarm.prayerKey)
                putExtra(AlarmScheduler.EXTRA_IS_PRE_REMINDER, alarm.isPreReminder)
                putExtra(AlarmScheduler.EXTRA_ALARM_TIME, alarm.triggerAtMillis)
                val kind = when {
                    alarm.prayerKey == "white_days" -> AlarmScheduler.KIND_WHITE_DAYS
                    alarm.isPreReminder -> AlarmScheduler.KIND_PRE_PRAYER
                    else -> AlarmScheduler.KIND_PRAYER
                }
                putExtra(AlarmScheduler.EXTRA_NOTIFICATION_KIND, kind)
                putExtra(AlarmScheduler.EXTRA_VIBRATE_ENABLED, vibrateEnabled)
                putExtra(AlarmScheduler.EXTRA_SOUND_ENABLED, soundEnabled)
                putExtra(AlarmScheduler.EXTRA_ALARM_REQUEST_CODE, alarm.requestCode)
                putExtra(
                    AlarmScheduler.EXTRA_SILENT_MODE_AUTOMATION_ENABLED,
                    alarm.silentModeAutomationEnabled
                )
                putExtra(
                    AlarmScheduler.EXTRA_SILENT_MODE_MINUTES_AFTER_ADHAN,
                    alarm.silentModeMinutesAfterAdhan
                )
                putExtra(
                    AlarmScheduler.EXTRA_SILENT_MODE_DURATION_MINUTES,
                    alarm.silentModeDurationMinutes
                )
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scheduleWithAlarmManager(alarmManager, pendingIntent, alarm.triggerAtMillis)

            ScheduleResult.Success(
                RegisteredAlarm(
                    requestCode = alarm.requestCode,
                    uri = alarm.uri,
                    prayerKey = alarm.prayerKey,
                    isPreReminder = alarm.isPreReminder,
                    triggerAtMillis = alarm.triggerAtMillis,
                    vibrateEnabled = vibrateEnabled,
                    soundEnabled = soundEnabled,
                    silentModeAutomationEnabled = alarm.silentModeAutomationEnabled,
                    silentModeMinutesAfterAdhan = alarm.silentModeMinutesAfterAdhan,
                    silentModeDurationMinutes = alarm.silentModeDurationMinutes
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm: ${alarm.prayerKey}", e)
            ScheduleResult.Failure(e)
        }
    }

    override fun restoreAlarm(context: Context, alarm: RegisteredAlarm): RestoreResult {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: throw IllegalStateException("AlarmManager not available")

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmScheduler.ACTION_PRAYER_ALARM
                data = android.net.Uri.parse(alarm.uri)
                putExtra(AlarmScheduler.EXTRA_PRAYER_NAME, alarm.prayerKey)
                putExtra(AlarmScheduler.EXTRA_IS_PRE_REMINDER, alarm.isPreReminder)
                putExtra(AlarmScheduler.EXTRA_ALARM_TIME, alarm.triggerAtMillis)
                val kind = when {
                    alarm.prayerKey == "white_days" -> AlarmScheduler.KIND_WHITE_DAYS
                    alarm.isPreReminder -> AlarmScheduler.KIND_PRE_PRAYER
                    else -> AlarmScheduler.KIND_PRAYER
                }
                putExtra(AlarmScheduler.EXTRA_NOTIFICATION_KIND, kind)
                putExtra(AlarmScheduler.EXTRA_VIBRATE_ENABLED, alarm.vibrateEnabled)
                putExtra(AlarmScheduler.EXTRA_SOUND_ENABLED, alarm.soundEnabled)
                putExtra(AlarmScheduler.EXTRA_ALARM_REQUEST_CODE, alarm.requestCode)
                putExtra(
                    AlarmScheduler.EXTRA_SILENT_MODE_AUTOMATION_ENABLED,
                    alarm.silentModeAutomationEnabled
                )
                putExtra(
                    AlarmScheduler.EXTRA_SILENT_MODE_MINUTES_AFTER_ADHAN,
                    alarm.silentModeMinutesAfterAdhan
                )
                putExtra(
                    AlarmScheduler.EXTRA_SILENT_MODE_DURATION_MINUTES,
                    alarm.silentModeDurationMinutes
                )
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scheduleWithAlarmManager(alarmManager, pendingIntent, alarm.triggerAtMillis)

            RestoreResult.Success(alarm)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore alarm: ${alarm.prayerKey}", e)
            RestoreResult.Failure(e)
        }
    }

    private fun scheduleWithAlarmManager(alarmManager: AlarmManager, pendingIntent: PendingIntent, triggerAtMillis: Long) {
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        scheduleWithExactFallback(
            canScheduleExact = canScheduleExact,
            scheduleExact = {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            },
            scheduleInexact = {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            },
            onExactFailure = { Log.w(TAG, "Exact alarm denied; using inexact fallback", it) }
        )
    }

    override fun cancelAlarm(context: Context, alarm: RegisteredAlarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_PRAYER_ALARM
            data = android.net.Uri.parse(alarm.uri)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    override fun cancelLegacyAlarm(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_PRAYER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
