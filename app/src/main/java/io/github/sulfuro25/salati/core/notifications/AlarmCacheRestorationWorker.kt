package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import io.github.sulfuro25.salati.data.settings.SalatiPreferences
import kotlinx.coroutines.flow.first

class AlarmCacheRestorationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val preferences = SalatiPreferences(applicationContext)
        val startedWith = preferences.settings.first().alarmRelevantFingerprint()
        val refreshResult = performCacheOnlyAlarmRefresh { requireCacheOnly ->
            ReminderCoordinator.refreshAlarms(
                context = applicationContext,
                requireCacheOnly = requireCacheOnly
            )
        }
        val workerResult = mapCacheRestorationResult(refreshResult, runAttemptCount) {
            AlarmWorkScheduler.enqueueNetworkRefresh(applicationContext)
        }
        val current = preferences.settings.first().alarmRelevantFingerprint()
        enqueueOneReconciliationIfChanged(startedWith, current) {
            AlarmWorkScheduler.enqueueReconciliationRefresh(applicationContext)
        }
        return workerResult
    }
}

internal suspend fun performCacheOnlyAlarmRefresh(
    refresh: suspend (requireCacheOnly: Boolean) -> AlarmRefreshResult
): AlarmRefreshResult = refresh(true)

internal fun mapCacheRestorationResult(
    result: AlarmRefreshResult,
    runAttemptCount: Int = 0,
    enqueueNetworkRefresh: () -> Unit
): ListenableWorker.Result {
    return when (result) {
        is AlarmRefreshResult.Success,
        is AlarmRefreshResult.SuccessWithStaleAlarms,
        is AlarmRefreshResult.Disabled,
        is AlarmRefreshResult.Recovered -> ListenableWorker.Result.success()
        is AlarmRefreshResult.DisabledWithWarning -> {
            if (result.retryRecommended && runAttemptCount < MAX_MUTE_CANCELLATION_RETRY_ATTEMPTS) {
                ListenableWorker.Result.retry()
            } else {
                ListenableWorker.Result.success()
            }
        }
        is AlarmRefreshResult.CacheMiss,
        is AlarmRefreshResult.PartiallyRecovered,
        is AlarmRefreshResult.TemporaryFailure -> {
            enqueueNetworkRefresh()
            ListenableWorker.Result.success()
        }
        is AlarmRefreshResult.PermanentFailure -> ListenableWorker.Result.failure()

    }
}

internal const val MAX_MUTE_CANCELLATION_RETRY_ATTEMPTS = 1
