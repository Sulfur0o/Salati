package io.github.sulfuro25.salati.core.notifications

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowPendingIntent

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class AlarmRegistrarTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        ShadowPendingIntent.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    @Suppress("DEPRECATION")
    fun restoreReconstructsCompletePendingIntentIdentityAndExtras() {
        val alarm = RegisteredAlarm(
            requestCode = 42_001,
            uri = "salati://alarm/2026-07-15/maghrib/pre",
            prayerKey = "maghrib",
            isPreReminder = true,
            triggerAtMillis = 1_784_136_600_000L,
            vibrateEnabled = false,
            silentModeAutomationEnabled = true,
            silentModeMinutesAfterAdhan = 5,
            silentModeDurationMinutes = 30
        )

        val result = SystemAlarmRegistrar().restoreAlarm(context, alarm)

        assertEquals(RestoreResult.Success(alarm), result)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduled = requireNotNull(Shadows.shadowOf(alarmManager).nextScheduledAlarm)
        assertEquals(alarm.triggerAtMillis, scheduled.triggerAtTime)

        val pendingIntent = scheduled.operation
        val shadowPendingIntent = Shadows.shadowOf(pendingIntent)
        val savedIntent = shadowPendingIntent.savedIntent
        assertTrue(shadowPendingIntent.isBroadcast)
        assertEquals(alarm.requestCode, shadowPendingIntent.requestCode)
        assertEquals(AlarmReceiver::class.java.name, savedIntent.component?.className)
        assertEquals(AlarmScheduler.ACTION_PRAYER_ALARM, savedIntent.action)
        assertEquals(alarm.uri, savedIntent.dataString)
        assertEquals(alarm.prayerKey, savedIntent.getStringExtra(AlarmScheduler.EXTRA_PRAYER_NAME))
        assertEquals(
            alarm.isPreReminder,
            savedIntent.getBooleanExtra(AlarmScheduler.EXTRA_IS_PRE_REMINDER, false)
        )
        assertEquals(
            alarm.triggerAtMillis,
            savedIntent.getLongExtra(AlarmScheduler.EXTRA_ALARM_TIME, -1L)
        )
        assertEquals(
            alarm.vibrateEnabled,
            savedIntent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE_ENABLED, true)
        )
        assertEquals(
            alarm.requestCode,
            savedIntent.getIntExtra(AlarmScheduler.EXTRA_ALARM_REQUEST_CODE, -1)
        )
        assertEquals(
            alarm.silentModeAutomationEnabled,
            savedIntent.getBooleanExtra(
                AlarmScheduler.EXTRA_SILENT_MODE_AUTOMATION_ENABLED,
                false
            )
        )
        assertEquals(
            alarm.silentModeMinutesAfterAdhan,
            savedIntent.getIntExtra(AlarmScheduler.EXTRA_SILENT_MODE_MINUTES_AFTER_ADHAN, -1)
        )
        assertEquals(
            alarm.silentModeDurationMinutes,
            savedIntent.getIntExtra(AlarmScheduler.EXTRA_SILENT_MODE_DURATION_MINUTES, -1)
        )
    }
    @Test
    fun exactAccessUsesExactScheduling() {
        var exactCalls = 0
        var inexactCalls = 0

        val delivery = scheduleWithExactFallback(
            canScheduleExact = true,
            scheduleExact = { exactCalls++ },
            scheduleInexact = { inexactCalls++ }
        )

        assertEquals(AlarmDelivery.EXACT, delivery)
        assertEquals(1, exactCalls)
        assertEquals(0, inexactCalls)
    }

    @Test
    fun deniedExactAccessUsesInexactFallback() {
        var exactCalls = 0
        var inexactCalls = 0

        val delivery = scheduleWithExactFallback(
            canScheduleExact = false,
            scheduleExact = { exactCalls++ },
            scheduleInexact = { inexactCalls++ }
        )

        assertEquals(AlarmDelivery.INEXACT, delivery)
        assertEquals(0, exactCalls)
        assertEquals(1, inexactCalls)
    }

    @Test
    fun exactSecurityExceptionFallsBackToInexact() {
        var inexactCalls = 0
        var reportedFailure: SecurityException? = null
        val denied = SecurityException("revoked")

        val delivery = scheduleWithExactFallback(
            canScheduleExact = true,
            scheduleExact = { throw denied },
            scheduleInexact = { inexactCalls++ },
            onExactFailure = { reportedFailure = it }
        )

        assertEquals(AlarmDelivery.INEXACT, delivery)
        assertEquals(1, inexactCalls)
        assertEquals(denied, reportedFailure)
    }
}
