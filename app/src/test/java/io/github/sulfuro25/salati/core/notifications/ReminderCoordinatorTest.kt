package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

private class FakeAlarmRegistry(
    var readResult: AlarmRegistryReadResult,
    var legacyCleanupCompleted: Boolean = false
) : AlarmRegistryStore {
    val savedAlarmLists = mutableListOf<List<RegisteredAlarm>>()
    val events = mutableListOf<String>()

    override suspend fun getActiveAlarms(): AlarmRegistryReadResult {
        events += "read"
        return readResult
    }

    override suspend fun saveActiveAlarms(alarms: List<RegisteredAlarm>) {
        events += "save"
        savedAlarmLists += alarms.toList()
        readResult = AlarmRegistryReadResult.Valid(alarms.toList())
    }

    override suspend fun clearActiveAlarms() {
        readResult = AlarmRegistryReadResult.Valid(emptyList())
    }

    override suspend fun isLegacyCleanupCompleted(): Boolean = legacyCleanupCompleted

    override suspend fun setLegacyCleanupCompleted(completed: Boolean) {
        legacyCleanupCompleted = completed
    }
}

private class FakeAlarmRegistrar : AlarmRegistrar {
    var failOnScheduleNumber: Int? = null
    var scheduleCalls = 0
    val restoreFailures = mutableSetOf<Int>()
    val scheduledAlarms = mutableListOf<RegisteredAlarm>()
    val cancellationFailures = mutableSetOf<Int>()
    val restoredAlarms = mutableListOf<RegisteredAlarm>()
    val cancelledAlarms = mutableListOf<RegisteredAlarm>()
    val cancelledLegacyCodes = mutableListOf<Int>()

    override fun scheduleAlarm(
        context: Context,
        alarm: PreparedAlarm,
        vibrateEnabled: Boolean,
        soundEnabled: Boolean
    ): ScheduleResult {
        scheduleCalls++
        if (scheduleCalls == failOnScheduleNumber) {
            return ScheduleResult.Failure(IllegalStateException("deliberate schedule failure"))
        }
        val registered = RegisteredAlarm(
            requestCode = alarm.requestCode,
            uri = alarm.uri,
            prayerKey = alarm.prayerKey,
            isPreReminder = alarm.isPreReminder,
            triggerAtMillis = alarm.triggerAtMillis,
            vibrateEnabled = vibrateEnabled
        )
        scheduledAlarms += registered
        return ScheduleResult.Success(registered)
    }

    override fun restoreAlarm(context: Context, alarm: RegisteredAlarm): RestoreResult {
        if (alarm.requestCode in restoreFailures) {
            return RestoreResult.Failure(IllegalStateException("deliberate restore failure"))
        }
        restoredAlarms += alarm
        return RestoreResult.Success(alarm)
    }

    override fun cancelAlarm(context: Context, alarm: RegisteredAlarm) {
        if (alarm.requestCode in cancellationFailures) {
            throw IllegalStateException("deliberate cancellation failure")
        }
        cancelledAlarms += alarm
        scheduledAlarms.removeAll { it.requestCode == alarm.requestCode && it.uri == alarm.uri }
    }

    override fun cancelLegacyAlarm(context: Context, requestCode: Int) {
        cancelledLegacyCodes += requestCode
    }
}

private class FixedPreparationSource(
    private val result: AlarmPreparationResult
) : AlarmPreparationSource {
    override suspend fun prepare(
        context: Context,
        settings: CalculationSettings,
        requireCacheOnly: Boolean,
        clock: Clock
    ): AlarmPreparationResult = result
}

private object DefaultSettingsSource : AlarmSettingsSource {
    override suspend fun getSettings(): CalculationSettings = CalculationSettings()
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class ReminderCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = Clock.fixed(Instant.ofEpochMilli(3_000L), ZoneId.of("Europe/Brussels"))

    @Test
    fun secondReplacementFailureCancelsPartialSkipsExpiredAndFullyRestoresFutureAlarms() = runBlocking {
        val expired = registered(100, 1_000L)
        val futureOne = registered(101, 5_000L)
        val futureTwo = registered(102, 6_000L)
        val registry = FakeAlarmRegistry(AlarmRegistryReadResult.Valid(listOf(expired, futureOne, futureTwo)))
        val registrar = FakeAlarmRegistrar().apply { failOnScheduleNumber = 2 }
        val replacements = listOf(prepared(201, 7_000L), prepared(202, 8_000L))

        val result = refresh(
            registry = registry,
            registrar = registrar,
            preparation = FixedPreparationSource(AlarmPreparationResult.Success(replacements))
        )

        assertEquals(AlarmRefreshResult.Recovered(2), result)
        assertTrue(registrar.cancelledAlarms.any { it.requestCode == 201 })
        assertFalse(registrar.restoredAlarms.any { it.requestCode == expired.requestCode })
        assertEquals(listOf(futureOne, futureTwo), registrar.restoredAlarms)
        assertEquals(listOf(futureOne, futureTwo), registry.savedAlarmLists.last())
    }

    @Test
    fun failedOldCancellationIsKeptInRegistryInsteadOfSilentlyDropped() = runBlocking {
        val staleAlarm = registered(101, 9_000L)
        val registry = FakeAlarmRegistry(AlarmRegistryReadResult.Valid(listOf(staleAlarm)))
        val registrar = FakeAlarmRegistrar().apply {
            cancellationFailures += staleAlarm.requestCode
        }
        val replacement = prepared(201, 7_000L)

        val result = refresh(
            registry = registry,
            registrar = registrar,
            preparation = FixedPreparationSource(AlarmPreparationResult.Success(listOf(replacement)))
        )

        assertTrue(result is AlarmRefreshResult.SuccessWithStaleAlarms)
        result as AlarmRefreshResult.SuccessWithStaleAlarms
        assertEquals(1, result.scheduledCount)
        assertEquals(1, result.staleCount)
        // The stale alarm must still be tracked in the registry (not orphaned) so the
        // next refresh retries cancelling it.
        val savedRegistry = registry.savedAlarmLists.last()
        assertTrue(savedRegistry.any { it.requestCode == staleAlarm.requestCode })
        assertTrue(savedRegistry.any { it.requestCode == 201 })
    }

    @Test
    fun partialRestorePersistsOnlyConfirmedAlarmsAndReportsPartialRecovery() = runBlocking {
        val futureOne = registered(101, 5_000L)
        val futureTwo = registered(102, 6_000L)
        val registry = FakeAlarmRegistry(AlarmRegistryReadResult.Valid(listOf(futureOne, futureTwo)))
        val registrar = FakeAlarmRegistrar().apply {
            failOnScheduleNumber = 2
            restoreFailures += futureTwo.requestCode
        }

        val result = refresh(
            registry = registry,
            registrar = registrar,
            preparation = FixedPreparationSource(
                AlarmPreparationResult.Success(listOf(prepared(201, 7_000L), prepared(202, 8_000L)))
            )
        )

        assertTrue(result is AlarmRefreshResult.PartiallyRecovered)
        result as AlarmRefreshResult.PartiallyRecovered
        assertEquals(1, result.restoredCount)
        assertEquals(1, result.failedCount)
        assertEquals(listOf(futureOne), registry.savedAlarmLists.last())
    }

    @Test
    fun failedReplacementPreservesCorruptedRegistry() = runBlocking {
        val corrupted = AlarmRegistryReadResult.Corrupted("{bad", IllegalStateException("bad JSON"))
        val registry = FakeAlarmRegistry(corrupted)
        val registrar = FakeAlarmRegistrar().apply { failOnScheduleNumber = 2 }

        val result = refresh(
            registry = registry,
            registrar = registrar,
            preparation = FixedPreparationSource(
                AlarmPreparationResult.Success(listOf(prepared(201, 7_000L), prepared(202, 8_000L)))
            )
        )

        assertEquals(AlarmRefreshResult.Recovered(0), result)
        assertTrue(registry.savedAlarmLists.isEmpty())
        assertTrue(registry.readResult is AlarmRegistryReadResult.Corrupted)
    }

    @Test
    fun validRegistryMuteCancelsRegisteredAndLegacyAlarmsThenPersistsEmptyRegistry() = runBlocking {
        val old = registered(101, 5_000L)
        val registry = FakeAlarmRegistry(AlarmRegistryReadResult.Valid(listOf(old)))
        val registrar = FakeAlarmRegistrar()

        val result = refresh(
            registry = registry,
            registrar = registrar,
            preparation = FixedPreparationSource(AlarmPreparationResult.Disabled)
        )

        assertTrue(result is AlarmRefreshResult.Disabled)
        assertNull((result as AlarmRefreshResult.Disabled).warning)
        assertEquals(listOf(old), registrar.cancelledAlarms)
        assertEquals(12, registrar.cancelledLegacyCodes.size)
        assertEquals(emptyList<RegisteredAlarm>(), registry.savedAlarmLists.last())
    }

    @Test
    fun corruptedRegistryMuteCancelsKnownWindowAndReturnsWarning() = runBlocking {
        val fixedClock = Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneId.of("Europe/Brussels"))
        val registry = FakeAlarmRegistry(
            AlarmRegistryReadResult.Corrupted("{bad", IllegalStateException("bad JSON"))
        )
        val registrar = FakeAlarmRegistrar()

        val result = ReminderCoordinator.refreshAlarms(
            context = context,
            registrar = registrar,
            clock = fixedClock,
            preparationSource = FixedPreparationSource(AlarmPreparationResult.Disabled),
            registry = registry,
            settingsSource = DefaultSettingsSource
        )

        assertTrue(result is AlarmRefreshResult.DisabledWithWarning)
        result as AlarmRefreshResult.DisabledWithWarning
        assertTrue(result.warning.contains("corrupted"))
        assertFalse(result.retryRecommended)
        assertEquals(88, registrar.cancelledAlarms.size)
        assertFalse(registrar.cancelledAlarms.any { it.prayerKey == "sunrise" })
        assertEquals(12, registrar.cancelledLegacyCodes.size)
        assertEquals(emptyList<RegisteredAlarm>(), registry.savedAlarmLists.last())
    }

    @Test

    fun muteCancellationFailureIsHonestAndPreservesRegistryForBoundedRetry() = runBlocking {
        val old = registered(101, 5_000L)
        val registry = FakeAlarmRegistry(
            readResult = AlarmRegistryReadResult.Valid(listOf(old)),
            legacyCleanupCompleted = true
        )
        val registrar = FakeAlarmRegistrar().apply {
            cancellationFailures += old.requestCode
        }

        val result = refresh(
            registry = registry,
            registrar = registrar,
            preparation = FixedPreparationSource(AlarmPreparationResult.Disabled)
        )

        assertTrue(result is AlarmRefreshResult.DisabledWithWarning)
        result as AlarmRefreshResult.DisabledWithWarning
        assertTrue(result.retryRecommended)
        assertTrue(result.warning.contains("cancellation"))
        assertTrue(registry.savedAlarmLists.isEmpty())
        assertEquals(AlarmRegistryReadResult.Valid(listOf(old)), registry.readResult)
    }

    @Test
    fun simultaneousRefreshesCannotEnterTheTransactionTogether() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        val preparation = object : AlarmPreparationSource {
            override suspend fun prepare(
                context: Context,
                settings: CalculationSettings,
                requireCacheOnly: Boolean,
                clock: Clock
            ): AlarmPreparationResult {
                when (calls.incrementAndGet()) {
                    1 -> {
                        firstEntered.complete(Unit)
                        releaseFirst.await()
                    }
                    2 -> secondEntered.complete(Unit)
                }
                return AlarmPreparationResult.Success(emptyList())
            }
        }
        val registry = FakeAlarmRegistry(AlarmRegistryReadResult.Valid(emptyList()))
        val registrar = FakeAlarmRegistrar()

        val first = async(Dispatchers.Default) {
            refresh(registry, registrar, preparation)
        }
        firstEntered.await()
        val second = async(Dispatchers.Default) {
            refresh(registry, registrar, preparation)
        }

        try {
            val interleaved = withTimeoutOrNull(150L) {
                secondEntered.await()
                true
            } ?: false
            assertFalse("Second refresh entered while the first transaction was blocked", interleaved)
        } finally {
            releaseFirst.complete(Unit)
        }

        assertTrue(first.await() is AlarmRefreshResult.Success)
        assertTrue(second.await() is AlarmRefreshResult.Success)
        assertEquals(2, calls.get())
    }

    private suspend fun refresh(
        registry: AlarmRegistryStore,
        registrar: AlarmRegistrar,
        preparation: AlarmPreparationSource
    ): AlarmRefreshResult {
        return ReminderCoordinator.refreshAlarms(
            context = context,
            registrar = registrar,
            clock = clock,
            preparationSource = preparation,
            registry = registry,
            settingsSource = DefaultSettingsSource
        )
    }

    private fun prepared(requestCode: Int, triggerAtMillis: Long): PreparedAlarm {
        return PreparedAlarm(
            requestCode = requestCode,
            uri = "salati://alarm/2026-07-15/fajr/main/$requestCode",
            prayerKey = "fajr",
            isPreReminder = false,
            triggerAtMillis = triggerAtMillis
        )
    }

    private fun registered(requestCode: Int, triggerAtMillis: Long): RegisteredAlarm {
        return RegisteredAlarm(
            requestCode = requestCode,
            uri = "salati://alarm/2026-07-15/fajr/main/$requestCode",
            prayerKey = "fajr",
            isPreReminder = false,
            triggerAtMillis = triggerAtMillis,
            vibrateEnabled = true
        )
    }
}
