package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class WhiteDaysRollbackTest {

    private lateinit var context: Context
    private val clock = Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneId.of("Europe/Brussels"))

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun registered(code: Int, trigger: Long, key: String) = RegisteredAlarm(
        requestCode = code,
        uri = "salati://alarm/123",
        prayerKey = key,
        isPreReminder = false,
        triggerAtMillis = trigger,
        vibrateEnabled = false
    )

    private class FakeAlarmRegistry(var readResult: AlarmRegistryReadResult) : AlarmRegistryStore {
        val savedAlarmLists = mutableListOf<List<RegisteredAlarm>>()
        override suspend fun getActiveAlarms(): AlarmRegistryReadResult = readResult
        override suspend fun saveActiveAlarms(alarms: List<RegisteredAlarm>) {
            savedAlarmLists += alarms.toList()
        }
        override suspend fun clearActiveAlarms() {}
        override suspend fun isLegacyCleanupCompleted(): Boolean = true
        override suspend fun setLegacyCleanupCompleted(completed: Boolean) {}
    }

    private class FakeAlarmRegistrar : AlarmRegistrar {
        var failOnScheduleNumber: Int? = null
        var scheduleCalls = 0
        val scheduledAlarms = mutableListOf<RegisteredAlarm>()
        val cancelledAlarms = mutableListOf<RegisteredAlarm>()

        override fun scheduleAlarm(context: Context, alarm: PreparedAlarm, vibrateEnabled: Boolean, soundEnabled: Boolean): ScheduleResult {
            scheduleCalls++
            if (scheduleCalls == failOnScheduleNumber) return ScheduleResult.Failure(Exception("fail"))
            val reg = RegisteredAlarm(alarm.requestCode, alarm.uri, alarm.prayerKey, alarm.isPreReminder, alarm.triggerAtMillis, vibrateEnabled, soundEnabled)
            scheduledAlarms += reg
            return ScheduleResult.Success(reg)
        }
        override fun cancelAlarm(context: Context, alarm: RegisteredAlarm) {
            cancelledAlarms += alarm
        }
        override fun cancelLegacyAlarm(context: Context, requestCode: Int) {}
        override fun restoreAlarm(context: Context, alarm: RegisteredAlarm): RestoreResult {
            scheduledAlarms += alarm
            return RestoreResult.Success(alarm)
        }
    }

    @Test
    fun testWhiteDaysRestoredAndUnsupportedRejected() = runBlocking {
        // Arrange
        val validPrayer = registered(1, clock.millis() + 5000, "fajr")
        val validWhiteDays = registered(2, clock.millis() + 6000, "white_days")
        val unsupportedEvent = registered(3, clock.millis() + 7000, "some_fake_event")
        val expiredPrayer = registered(4, clock.millis() - 1000, "dhuhr")

        val oldAlarms = listOf(validPrayer, validWhiteDays, unsupportedEvent, expiredPrayer)
        val registry = FakeAlarmRegistry(AlarmRegistryReadResult.Valid(oldAlarms))
        
        val registrar = FakeAlarmRegistrar().apply {
            failOnScheduleNumber = 1 // Fail the new schedule, forcing a rollback
        }
        
        val preparedNew = PreparedAlarm(10, "salati://10", "asr", false, clock.millis() + 10000)

        // Act
        val result = ReminderCoordinator.refreshAlarms(
            context = context,
            registrar = registrar,
            clock = clock,
            preparationSource = object : AlarmPreparationSource {
                override suspend fun prepare(context: Context, settings: CalculationSettings, requireCacheOnly: Boolean, clock: Clock) = AlarmPreparationResult.Success(listOf(preparedNew))
            },
            registry = registry,
            settingsSource = object : AlarmSettingsSource {
                override suspend fun getSettings() = CalculationSettings()
            }
        )

        // Assert
        assertTrue(result is AlarmRefreshResult.PartiallyRecovered || result is AlarmRefreshResult.Recovered || result is AlarmRefreshResult.PermanentFailure || result is AlarmRefreshResult.TemporaryFailure)
        
        // Ensure valid ones were scheduled during rollback
        assertTrue(registrar.scheduledAlarms.contains(validPrayer))
        assertTrue(registrar.scheduledAlarms.contains(validWhiteDays))
        
        // Ensure unsupported/expired were ignored
        assertFalse(registrar.scheduledAlarms.contains(unsupportedEvent))
        assertFalse(registrar.scheduledAlarms.contains(expiredPrayer))
    }

    @Test
    fun testCorruptedRegistryKnownCancellationSweepIncludesWhiteDays() = runBlocking {
        val registry = FakeAlarmRegistry(AlarmRegistryReadResult.Corrupted("bad", Exception()))
        val registrar = FakeAlarmRegistrar()
        
        ReminderCoordinator.refreshAlarms(
            context = context,
            registrar = registrar,
            clock = clock,
            preparationSource = object : AlarmPreparationSource {
                override suspend fun prepare(context: Context, settings: CalculationSettings, requireCacheOnly: Boolean, clock: Clock) = AlarmPreparationResult.Disabled
            },
            registry = registry,
            settingsSource = object : AlarmSettingsSource {
                override suspend fun getSettings() = CalculationSettings()
            }
        )

        // The fallback sweep cancels all identities.
        // It should include normal prayers AND white days.
        assertTrue(registrar.cancelledAlarms.any { it.prayerKey == "fajr" })
        assertTrue(registrar.cancelledAlarms.any { it.prayerKey == "white_days" })
        // It shouldn't contain weird keys
        assertFalse(registrar.cancelledAlarms.any { it.prayerKey == "some_fake_event" })
    }
}
