package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class AlarmWorkersTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        workManager.cancelAllWork().result.get()
    }

    @Test
    fun temporaryFailureMapsToNetworkWorkerRetry() {
        val actual = mapNetworkRefreshResult(
            AlarmRefreshResult.TemporaryFailure(java.io.IOException("offline"))
        )

        assertEquals(ListenableWorker.Result.retry().javaClass, actual.javaClass)
    }

    @Test
    fun permanentFailureMapsToNetworkWorkerFailure() {
        val actual = mapNetworkRefreshResult(
            AlarmRefreshResult.PermanentFailure(IllegalArgumentException("invalid"))
        )

        assertEquals(ListenableWorker.Result.failure().javaClass, actual.javaClass)
    }

    @Test
    fun cacheOnlyMaintenanceAlwaysRequiresCacheAndNeverSelectsApiPath() = runBlocking {
        var cacheOnlyArgument: Boolean? = null

        performCacheOnlyAlarmRefresh { requireCacheOnly ->
            cacheOnlyArgument = requireCacheOnly
            AlarmRefreshResult.CacheMiss
        }

        assertEquals(true, cacheOnlyArgument)
    }

    @Test
    fun cacheMissHandoffEnqueuesNetworkPathOnceAndCompletesCacheWorker() {
        var enqueueCalls = 0
        val actual = mapCacheRestorationResult(AlarmRefreshResult.CacheMiss) {
            enqueueCalls++
        }

        assertEquals(1, enqueueCalls)
        assertEquals(ListenableWorker.Result.success().javaClass, actual.javaClass)
        assertEquals("salati_alarm_network_refresh", AlarmWorkScheduler.NETWORK_REFRESH_WORK_NAME)
        assertEquals(ExistingWorkPolicy.KEEP, AlarmWorkScheduler.networkWorkPolicy)
    }

    @Test
    fun networkRefreshWorkRequiresConnectedNetwork() {
        assertEquals(
            NetworkType.CONNECTED,
            AlarmWorkScheduler.networkConstraints().requiredNetworkType
        )
    }

    @Test
    fun repeatedStartupRefreshRequestsLeaveOneActiveRealRefresh() {
        AlarmWorkScheduler.enqueueRefresh(context)
        AlarmWorkScheduler.enqueueRefresh(context)

        assertEquals("salati_alarm_refresh", AlarmWorkScheduler.REFRESH_WORK_NAME)
        assertEquals(ExistingWorkPolicy.REPLACE, AlarmWorkScheduler.refreshWorkPolicy)
        assertEquals(1, workManager.getWorkInfosForUniqueWork(AlarmWorkScheduler.REFRESH_WORK_NAME).get().distinctBy { it.id }.size)
        assertEquals(true, activeWorkCount(AlarmWorkScheduler.REFRESH_WORK_NAME) <= 1)
    }

    @Test
    fun muteChangeBypassesDebounceAndEnqueuesRealRefreshImmediately() {
        val trigger = enqueueAlarmSettingsRefreshIfNeeded(
            context = context,
            previous = CalculationSettings(),
            updated = CalculationSettings(notificationsMuted = true)
        )

        assertEquals(AlarmSettingsRefreshTrigger.IMMEDIATE, trigger)
        assertTrue(
            workManager.getWorkInfosForUniqueWork(AlarmWorkScheduler.REFRESH_WORK_NAME).get().isNotEmpty()
        )
        assertTrue(
            workManager.getWorkInfosForUniqueWork(
                AlarmWorkScheduler.SETTINGS_REFRESH_DEBOUNCE_WORK_NAME
            ).get().isEmpty()
        )
    }

    @Test
    fun repeatedSliderChangesCollapseIntoOneDelayedDebounceKick() {
        repeat(5) { AlarmWorkScheduler.enqueueSettingsRefreshDebounced(context) }

        val active = activeWork(AlarmWorkScheduler.SETTINGS_REFRESH_DEBOUNCE_WORK_NAME)
        assertEquals(1, active.size)
        assertEquals(
            TimeUnit.SECONDS.toMillis(1),
            AlarmWorkScheduler.settingsDebounceRequest().workSpec.initialDelay
        )
        assertEquals(ExistingWorkPolicy.REPLACE, AlarmWorkScheduler.settingsDebounceWorkPolicy)
    }

    @Test
    fun repeatedPeriodicRegistrationLeavesOneActivePeriodicRequest() {
        AlarmWorkScheduler.registerPeriodicMaintenance(context)
        AlarmWorkScheduler.registerPeriodicMaintenance(context)

        assertEquals(
            1,
            activeWork(AlarmWorkScheduler.PERIODIC_MAINTENANCE_WORK_NAME).size
        )
    }

    @Test
    fun periodicMaintenanceUsesApprovedNameTimingFlexAndKeepPolicy() {
        val request = AlarmWorkScheduler.periodicMaintenanceRequest()

        assertEquals(
            "salati_alarm_periodic_maintenance",
            AlarmWorkScheduler.PERIODIC_MAINTENANCE_WORK_NAME
        )
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, AlarmWorkScheduler.periodicWorkPolicy)
        assertEquals(TimeUnit.HOURS.toMillis(24), request.workSpec.intervalDuration)
        assertEquals(TimeUnit.HOURS.toMillis(6), request.workSpec.flexDuration)
        assertEquals(NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
    }
    @Test
    fun muteCancellationWarningRetriesOnceThenStopsWithoutNetworkHandoff() {
        val warning = AlarmRefreshResult.DisabledWithWarning(
            warning = "cancellation failed",
            retryRecommended = true
        )
        var networkFallbacks = 0

        val firstCacheAttempt = mapCacheRestorationResult(warning, runAttemptCount = 0) {
            networkFallbacks++
        }
        val finalCacheAttempt = mapCacheRestorationResult(warning, runAttemptCount = 1) {
            networkFallbacks++
        }
        val firstNetworkAttempt = mapNetworkRefreshResult(warning, runAttemptCount = 0)
        val finalNetworkAttempt = mapNetworkRefreshResult(warning, runAttemptCount = 1)

        assertEquals(ListenableWorker.Result.retry().javaClass, firstCacheAttempt.javaClass)
        assertEquals(ListenableWorker.Result.success().javaClass, finalCacheAttempt.javaClass)
        assertEquals(ListenableWorker.Result.retry().javaClass, firstNetworkAttempt.javaClass)
        assertEquals(ListenableWorker.Result.success().javaClass, finalNetworkAttempt.javaClass)
        assertEquals(0, networkFallbacks)
        assertEquals(1, MAX_MUTE_CANCELLATION_RETRY_ATTEMPTS)
    }

    private fun activeWorkCount(uniqueName: String): Int = activeWork(uniqueName).size

    private fun activeWork(uniqueName: String): List<WorkInfo> {
        return workManager.getWorkInfosForUniqueWork(uniqueName).get().filter {
            !it.state.isFinished
        }
    }
}
