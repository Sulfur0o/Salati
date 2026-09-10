package com.sulfuro.salati.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.sulfuro.salati.data.settings.SalatiPreferences
import kotlinx.coroutines.flow.first
import com.sulfuro.salati.core.alarms.AlarmRefreshResult
import com.sulfuro.salati.core.alarms.ReminderCoordinator
import com.sulfuro.salati.core.alarms.alarmRelevantFingerprint
import com.sulfuro.salati.core.alarms.enqueueOneReconciliationIfChanged

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
