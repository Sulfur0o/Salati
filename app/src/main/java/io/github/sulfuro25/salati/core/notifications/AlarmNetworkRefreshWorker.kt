package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import io.github.sulfuro25.salati.data.settings.SalatiPreferences
import kotlinx.coroutines.flow.first

class AlarmNetworkRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val preferences = SalatiPreferences(applicationContext)
        val startedWith = preferences.settings.first().alarmRelevantFingerprint()
        val workerResult = mapNetworkRefreshResult(
            ReminderCoordinator.refreshAlarms(
                context = applicationContext,
                requireCacheOnly = false
            ),
            runAttemptCount
        )
        val current = preferences.settings.first().alarmRelevantFingerprint()
        enqueueOneReconciliationIfChanged(startedWith, current) {
            AlarmWorkScheduler.enqueueReconciliationRefresh(applicationContext)
        }
        return workerResult
    }
}

internal fun mapNetworkRefreshResult(
    result: AlarmRefreshResult,
    runAttemptCount: Int = 0
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
        is AlarmRefreshResult.TemporaryFailure -> ListenableWorker.Result.retry()
        is AlarmRefreshResult.PartiallyRecovered,
        is AlarmRefreshResult.PermanentFailure -> ListenableWorker.Result.failure()
    }
}
