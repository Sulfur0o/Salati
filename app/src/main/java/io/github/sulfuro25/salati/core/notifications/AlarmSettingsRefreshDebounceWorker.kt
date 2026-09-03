package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class AlarmSettingsRefreshDebounceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        AlarmWorkScheduler.enqueueRefresh(applicationContext)
        return Result.success()
    }
}
