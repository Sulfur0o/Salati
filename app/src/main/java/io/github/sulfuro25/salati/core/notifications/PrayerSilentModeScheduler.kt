package io.github.sulfuro25.salati.core.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log

internal data class PrayerSilentWindow(
    val startAtMillis: Long,
    val endAtMillis: Long
)

internal enum class SilentWindowScheduleResult {
    SCHEDULED,
    DISABLED,
    MISSING_POLICY_ACCESS,
    INVALID_CONFIGURATION,
    EXPIRED,
    ALARM_SERVICE_UNAVAILABLE,
    SCHEDULING_FAILED
}

internal fun calculatePrayerSilentWindow(
    prayerAtMillis: Long,
    minutesAfterAdhan: Int,
    durationMinutes: Int,
    nowMillis: Long
): PrayerSilentWindow? {
    if (minutesAfterAdhan !in PrayerSilentModeScheduler.ALLOWED_OFFSETS_MINUTES) return null
    if (durationMinutes !in PrayerSilentModeScheduler.ALLOWED_DURATIONS_MINUTES) return null

    val configuredStart = prayerAtMillis + minutesAfterAdhan * 60_000L
    val configuredEnd = configuredStart + durationMinutes * 60_000L
    if (configuredEnd <= nowMillis) return null
    return PrayerSilentWindow(
        startAtMillis = maxOf(configuredStart, nowMillis),
        endAtMillis = configuredEnd
    )
}

internal object PrayerSilentModeScheduler {
    private const val TAG = "PrayerSilentMode"
    private const val START_REQUEST_MASK = 0x2000_0000
    private const val RESTORE_REQUEST_MASK = 0x4000_0000

    val ALLOWED_OFFSETS_MINUTES = setOf(0, 5, 10, 15)
    val ALLOWED_DURATIONS_MINUTES = setOf(15, 20, 30)

    fun hasPolicyAccess(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager
        return notificationManager?.isNotificationPolicyAccessGranted == true
    }

    fun setAutomationEnabled(context: Context, enabled: Boolean) {
        silentModePreferences(context).edit()
            .putBoolean(KEY_AUTOMATION_ENABLED, enabled)
            .apply()
    }

    fun isAutomationEnabled(context: Context): Boolean {
        return silentModePreferences(context).getBoolean(KEY_AUTOMATION_ENABLED, false)
    }

    fun scheduleForPrayer(
        context: Context,
        prayerRequestCode: Int,
        prayerAtMillis: Long,
        enabled: Boolean,
        minutesAfterAdhan: Int,
        durationMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): SilentWindowScheduleResult {
        if (!enabled) return SilentWindowScheduleResult.DISABLED
        if (minutesAfterAdhan !in ALLOWED_OFFSETS_MINUTES ||
            durationMinutes !in ALLOWED_DURATIONS_MINUTES
        ) {
            Log.w(TAG, "Ignoring invalid prayer silent-mode configuration")
            return SilentWindowScheduleResult.INVALID_CONFIGURATION
        }
        if (!hasPolicyAccess(context)) return SilentWindowScheduleResult.MISSING_POLICY_ACCESS

        val window = calculatePrayerSilentWindow(
            prayerAtMillis = prayerAtMillis,
            minutesAfterAdhan = minutesAfterAdhan,
            durationMinutes = durationMinutes,
            nowMillis = nowMillis
        ) ?: return SilentWindowScheduleResult.EXPIRED
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return SilentWindowScheduleResult.ALARM_SERVICE_UNAVAILABLE

        return try {
            // Register restoration first so a partial scheduling failure can never leave
            // the device silent without a recovery alarm.
            scheduleRestore(
                context = context,
                alarmManager = alarmManager,
                prayerRequestCode = prayerRequestCode,
                sessionEndMillis = window.endAtMillis
            )
            schedule(
                alarmManager = alarmManager,
                pendingIntent = silentModePendingIntent(
                    context = context,
                    action = MosqueModeReceiver.ACTION_ENTER_SILENT_MODE,
                    requestCode = startRequestCode(prayerRequestCode),
                    prayerRequestCode = prayerRequestCode,
                    sessionEndMillis = window.endAtMillis
                ),
                triggerAtMillis = window.startAtMillis
            )
            SilentWindowScheduleResult.SCHEDULED
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to schedule prayer silent-mode window", exception)
            SilentWindowScheduleResult.SCHEDULING_FAILED
        }
    }

    fun requestActiveSessionReconciliation(context: Context) {
        context.sendBroadcast(
            Intent(context, MosqueModeReceiver::class.java).apply {
                action = MosqueModeReceiver.ACTION_RECONCILE_SILENT_MODE
            }
        )
    }

    fun reconcileActiveSession(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val activeUntil = PrayerSilentModeController.activeSessionEnd(context) ?: return
        if (activeUntil <= nowMillis) {
            PrayerSilentModeController.forceRestore(context)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        scheduleRestore(
            context = context,
            alarmManager = alarmManager,
            prayerRequestCode = activeUntil.hashCode(),
            sessionEndMillis = activeUntil
        )
    }

    fun rescheduleRestore(
        context: Context,
        prayerRequestCode: Int,
        sessionEndMillis: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        scheduleRestore(context, alarmManager, prayerRequestCode, sessionEndMillis)
    }

    internal fun startRequestCode(prayerRequestCode: Int): Int =
        prayerRequestCode xor START_REQUEST_MASK

    internal fun restoreRequestCode(prayerRequestCode: Int): Int =
        prayerRequestCode xor RESTORE_REQUEST_MASK

    private fun scheduleRestore(
        context: Context,
        alarmManager: AlarmManager,
        prayerRequestCode: Int,
        sessionEndMillis: Long
    ) {
        schedule(
            alarmManager = alarmManager,
            pendingIntent = silentModePendingIntent(
                context = context,
                action = MosqueModeReceiver.ACTION_RESTORE_RINGER_MODE,
                requestCode = restoreRequestCode(prayerRequestCode),
                prayerRequestCode = prayerRequestCode,
                sessionEndMillis = sessionEndMillis
            ),
            triggerAtMillis = sessionEndMillis
        )
    }

    private fun silentModePendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
        prayerRequestCode: Int,
        sessionEndMillis: Long
    ): PendingIntent {
        val intent = Intent(context, MosqueModeReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("salati://silent-mode/$prayerRequestCode/$action/$sessionEndMillis")
            putExtra(MosqueModeReceiver.EXTRA_PRAYER_REQUEST_CODE, prayerRequestCode)
            putExtra(MosqueModeReceiver.EXTRA_SESSION_END_MILLIS, sessionEndMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun schedule(
        alarmManager: AlarmManager,
        pendingIntent: PendingIntent,
        triggerAtMillis: Long
    ) {
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        scheduleWithExactFallback(
            canScheduleExact = canScheduleExact,
            scheduleExact = {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            },
            scheduleInexact = {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            },
            onExactFailure = { Log.w(TAG, "Exact silent-mode alarm denied; using fallback", it) }
        )
    }
}

internal sealed interface SilentModeRestoreResult {
    data object NothingToRestore : SilentModeRestoreResult
    data object Restored : SilentModeRestoreResult
    data class NotDue(val activeUntilMillis: Long) : SilentModeRestoreResult
    data class Failed(val cause: Throwable) : SilentModeRestoreResult
}

internal object PrayerSilentModeController {
    private const val TAG = "PrayerSilentMode"

    fun enterSilentMode(context: Context, sessionEndMillis: Long): Boolean {
        if (!PrayerSilentModeScheduler.isAutomationEnabled(context) ||
            !PrayerSilentModeScheduler.hasPolicyAccess(context) ||
            sessionEndMillis <= System.currentTimeMillis()
        ) {
            return false
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        val preferences = silentModePreferences(context)
        val alreadyActive = preferences.contains(KEY_PREVIOUS_RINGER_MODE)
        val previousMode = if (alreadyActive) {
            preferences.getInt(KEY_PREVIOUS_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
        } else {
            audioManager.ringerMode
        }
        val activeUntil = maxOf(
            preferences.getLong(KEY_ACTIVE_UNTIL_MILLIS, 0L),
            sessionEndMillis
        )

        return try {
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            val saved = preferences.edit()
                .putInt(KEY_PREVIOUS_RINGER_MODE, previousMode)
                .putLong(KEY_ACTIVE_UNTIL_MILLIS, activeUntil)
                .commit()
            if (!saved) {
                audioManager.ringerMode = previousMode
            }
            saved
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Notification policy access was revoked before silent mode started", securityException)
            false
        }
    }

    fun restoreIfDue(
        context: Context,
        expectedSessionEndMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): SilentModeRestoreResult {
        val preferences = silentModePreferences(context)
        if (!preferences.contains(KEY_PREVIOUS_RINGER_MODE)) {
            return SilentModeRestoreResult.NothingToRestore
        }
        val activeUntil = preferences.getLong(KEY_ACTIVE_UNTIL_MILLIS, 0L)
        if (expectedSessionEndMillis < activeUntil || nowMillis < activeUntil) {
            return SilentModeRestoreResult.NotDue(activeUntil)
        }
        return restore(context)
    }

    fun forceRestore(context: Context): SilentModeRestoreResult = restore(context)

    fun activeSessionEnd(context: Context): Long? {
        val preferences = silentModePreferences(context)
        if (!preferences.contains(KEY_PREVIOUS_RINGER_MODE)) return null
        return preferences.getLong(KEY_ACTIVE_UNTIL_MILLIS, 0L)
    }

    private fun restore(context: Context): SilentModeRestoreResult {
        val preferences = silentModePreferences(context)
        if (!preferences.contains(KEY_PREVIOUS_RINGER_MODE)) {
            return SilentModeRestoreResult.NothingToRestore
        }
        val previousMode = preferences.getInt(
            KEY_PREVIOUS_RINGER_MODE,
            AudioManager.RINGER_MODE_NORMAL
        )
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return SilentModeRestoreResult.Failed(IllegalStateException("AudioManager unavailable"))

        return try {
            // Respect a manual user change made during the prayer window.
            if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                audioManager.ringerMode = previousMode
            }
            preferences.edit()
                .remove(KEY_PREVIOUS_RINGER_MODE)
                .remove(KEY_ACTIVE_UNTIL_MILLIS)
                .commit()
            SilentModeRestoreResult.Restored
        } catch (securityException: SecurityException) {
            Log.w(TAG, "Unable to restore the previous ringer mode", securityException)
            SilentModeRestoreResult.Failed(securityException)
        }
    }
}

private const val PREFERENCES_NAME = "prayer_silent_mode"
private const val KEY_AUTOMATION_ENABLED = "automation_enabled"
private const val KEY_PREVIOUS_RINGER_MODE = "previous_ringer_mode"
private const val KEY_ACTIVE_UNTIL_MILLIS = "active_until_millis"

private fun silentModePreferences(context: Context) =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
