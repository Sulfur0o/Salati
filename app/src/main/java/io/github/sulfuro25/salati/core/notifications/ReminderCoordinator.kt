package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import android.util.Log
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import io.github.sulfuro25.salati.data.settings.SalatiPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.ZoneId

interface AlarmPreparationSource {
    suspend fun prepare(
        context: Context,
        settings: CalculationSettings,
        requireCacheOnly: Boolean,
        clock: Clock
    ): AlarmPreparationResult
}

object SchedulerAlarmPreparationSource : AlarmPreparationSource {
    override suspend fun prepare(
        context: Context,
        settings: CalculationSettings,
        requireCacheOnly: Boolean,
        clock: Clock
    ): AlarmPreparationResult {
        return AlarmScheduler.prepareAlarms(context, settings, requireCacheOnly, clock)
    }
}

interface AlarmSettingsSource {
    suspend fun getSettings(): CalculationSettings
}

class DataStoreAlarmSettingsSource(context: Context) : AlarmSettingsSource {
    private val preferences = SalatiPreferences(context)

    override suspend fun getSettings(): CalculationSettings = preferences.settings.first()
}

object ReminderCoordinator {
    private const val TAG = "ReminderCoordinator"
    private val refreshMutex = Mutex()
    private val legacyCodes = listOf(1, 2, 3, 4, 5, 6, 11, 12, 13, 14, 15, 16)

    suspend fun refreshAlarms(
        context: Context,
        registrar: AlarmRegistrar = SystemAlarmRegistrar(),
        clock: Clock = Clock.systemUTC(),
        requireCacheOnly: Boolean = false,
        preparationSource: AlarmPreparationSource = SchedulerAlarmPreparationSource,
        registry: AlarmRegistryStore = AlarmRegistry(context),
        settingsSource: AlarmSettingsSource = DataStoreAlarmSettingsSource(context)
    ): AlarmRefreshResult = refreshMutex.withLock {
        refreshAlarmsLocked(
            context = context,
            registrar = registrar,
            clock = clock,
            requireCacheOnly = requireCacheOnly,
            preparationSource = preparationSource,
            registry = registry,
            settingsSource = settingsSource
        )
    }

    private suspend fun refreshAlarmsLocked(
        context: Context,
        registrar: AlarmRegistrar,
        clock: Clock,
        requireCacheOnly: Boolean,
        preparationSource: AlarmPreparationSource,
        registry: AlarmRegistryStore,
        settingsSource: AlarmSettingsSource
    ): AlarmRefreshResult {
        val settings = settingsSource.getSettings()
        val oldAlarmsResult = registry.getActiveAlarms()
        val oldAlarms = when (oldAlarmsResult) {
            is AlarmRegistryReadResult.Valid -> oldAlarmsResult.alarms
            is AlarmRegistryReadResult.Corrupted -> emptyList()
        }

        val preparationResult = try {
            preparationSource.prepare(context, settings, requireCacheOnly, clock)
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled exception during preparation", e)
            return AlarmRefreshResult.PermanentFailure(e)
        }

        when (preparationResult) {
            is AlarmPreparationResult.Disabled -> {
                return disableAlarms(
                    context = context,
                    registrar = registrar,
                    clock = clock,
                    zoneId = ZoneId.of(settings.timezoneId),
                    registry = registry,
                    registryReadResult = oldAlarmsResult
                )
            }
            is AlarmPreparationResult.CacheMiss -> {
                return AlarmRefreshResult.CacheMiss
            }
            is AlarmPreparationResult.TemporaryNetworkFailure -> {
                return AlarmRefreshResult.TemporaryFailure(preparationResult.cause)
            }
            is AlarmPreparationResult.RetryableServerFailure -> {
                return AlarmRefreshResult.TemporaryFailure(
                    IllegalStateException("Retryable HTTP ${preparationResult.statusCode}")
                )
            }
            is AlarmPreparationResult.PermanentHttpFailure -> {
                return AlarmRefreshResult.PermanentFailure(
                    IllegalStateException("Permanent HTTP ${preparationResult.statusCode}")
                )
            }
            is AlarmPreparationResult.InvalidConfiguration -> {
                return AlarmRefreshResult.PermanentFailure(preparationResult.cause)
            }
            is AlarmPreparationResult.InvalidCachedData -> {
                return AlarmRefreshResult.TemporaryFailure(preparationResult.cause)
            }
            is AlarmPreparationResult.InvalidApiResponse -> {
                return AlarmRefreshResult.PermanentFailure(preparationResult.cause)
            }
            is AlarmPreparationResult.Success -> Unit
        }

        val preparedAlarms = preparationResult.alarms
        val failedOldCancellations = cancelAlarms(context, registrar, oldAlarms)
        performLegacyCleanup(context, registrar, registry)

        val newRegisteredAlarms = mutableListOf<RegisteredAlarm>()
        var failureCause: Throwable? = null

        for (preparedAlarm in preparedAlarms) {
            when (val result = registrar.scheduleAlarm(context, preparedAlarm, settings.vibrateEnabled, settings.soundEnabled)) {
                is ScheduleResult.Success -> newRegisteredAlarms += result.alarm
                is ScheduleResult.Failure -> {
                    failureCause = result.cause
                    Log.e(TAG, "Failed to schedule replacement alarm: ${preparedAlarm.prayerKey}", result.cause)
                    break
                }
            }
        }

        if (failureCause == null) {
            // Any old alarm we couldn't cancel is still live in AlarmManager; keep it in the
            // registry (instead of silently dropping it) so the next refresh retries cancelling
            // it rather than orphaning a stale or duplicate notification.
            val staleAlarms = failedOldCancellations.filter {
                it.triggerAtMillis > clock.millis() && AlarmScheduler.isSupportedAlarmEvent(it.prayerKey)
            }
            registry.saveActiveAlarms(newRegisteredAlarms + staleAlarms)
            return if (staleAlarms.isEmpty()) {
                AlarmRefreshResult.Success(newRegisteredAlarms.size)
            } else {
                Log.w(TAG, "Rescheduled successfully but failed to cancel ${staleAlarms.size} stale alarm(s)")
                AlarmRefreshResult.SuccessWithStaleAlarms(
                    scheduledCount = newRegisteredAlarms.size,
                    staleCount = staleAlarms.size
                )
            }
        }

        Log.w(TAG, "Scheduling failed midway; rolling back partial replacements")
        cancelAlarms(context, registrar, newRegisteredAlarms)

        val restoredAlarms = mutableListOf<RegisteredAlarm>()
        val futureOldAlarms = oldAlarms.filter {
            it.triggerAtMillis > clock.millis() &&
                AlarmScheduler.isSupportedAlarmEvent(it.prayerKey)
        }
        var firstRestoreFailure: Throwable? = null
        for (oldAlarm in futureOldAlarms) {
            when (val result = registrar.restoreAlarm(context, oldAlarm)) {
                is RestoreResult.Success -> restoredAlarms += result.alarm
                is RestoreResult.Failure -> {
                    if (firstRestoreFailure == null) firstRestoreFailure = result.cause
                    Log.e(TAG, "Failed to restore old alarm: ${oldAlarm.prayerKey}", result.cause)
                }
            }
        }

        when {
            oldAlarmsResult is AlarmRegistryReadResult.Valid -> {
                registry.saveActiveAlarms(restoredAlarms)
            }
            restoredAlarms.isNotEmpty() -> registry.saveActiveAlarms(restoredAlarms)
            else -> Log.w(TAG, "Preserving corrupted registry because replacement failed")
        }

        return if (restoredAlarms.size == futureOldAlarms.size) {
            AlarmRefreshResult.Recovered(restoredAlarms.size)
        } else {
            AlarmRefreshResult.PartiallyRecovered(
                restoredCount = restoredAlarms.size,
                failedCount = futureOldAlarms.size - restoredAlarms.size,
                cause = firstRestoreFailure ?: failureCause
            )
        }
    }

    private suspend fun disableAlarms(
        context: Context,
        registrar: AlarmRegistrar,
        clock: Clock,
        zoneId: ZoneId,
        registry: AlarmRegistryStore,
        registryReadResult: AlarmRegistryReadResult
    ): AlarmRefreshResult {
        val cancellationTargets = when (registryReadResult) {
            is AlarmRegistryReadResult.Valid -> registryReadResult.alarms
            is AlarmRegistryReadResult.Corrupted -> AlarmScheduler.getKnownAlarmIdentities(clock, zoneId)
        }
        val failedCancellations = cancelAlarms(context, registrar, cancellationTargets)
        val legacyFailures = performLegacyCleanup(context, registrar, registry)
        val cancellationFailed = failedCancellations.isNotEmpty() || legacyFailures > 0
        if (!cancellationFailed) {
            registry.saveActiveAlarms(emptyList())
        }

        val warnings = buildList {
            if (registryReadResult is AlarmRegistryReadResult.Corrupted) {
                add("Alarm registry was corrupted; unidentified alarms outside the known date window may still fire once")
            }
            if (cancellationFailed) {
                add("One or more alarm cancellation attempts failed")
            }
        }
        return if (warnings.isEmpty()) {
            AlarmRefreshResult.Disabled()
        } else {
            AlarmRefreshResult.DisabledWithWarning(
                warning = warnings.joinToString("; "),
                retryRecommended = cancellationFailed
            )
    }
    }

    private fun cancelAlarms(
        context: Context,
        registrar: AlarmRegistrar,
        alarms: List<RegisteredAlarm>
    ): List<RegisteredAlarm> {
        val failed = mutableListOf<RegisteredAlarm>()
        for (alarm in alarms) {
            try {
                registrar.cancelAlarm(context, alarm)
            } catch (e: Exception) {
                failed += alarm
                Log.e(TAG, "Failed to cancel alarm identity ${alarm.requestCode}", e)
            }
        }
        return failed
    }

    private suspend fun performLegacyCleanup(
        context: Context,
        registrar: AlarmRegistrar,
        registry: AlarmRegistryStore
    ): Int {
        if (registry.isLegacyCleanupCompleted()) return 0

        var failures = 0
        for (requestCode in legacyCodes) {
            try {
                registrar.cancelLegacyAlarm(context, requestCode)
            } catch (e: Exception) {
                failures++
                Log.e(TAG, "Failed to cancel legacy alarm $requestCode", e)
            }
        }
        if (failures == 0) {
            registry.setLegacyCleanupCompleted(true)
        }
        return failures
    }
}
