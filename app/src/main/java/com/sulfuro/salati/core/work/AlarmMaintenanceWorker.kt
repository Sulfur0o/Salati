package com.sulfuro.salati.core.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class AlarmMaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        AlarmWorkScheduler.enqueueRefresh(applicationContext)
        return Result.success()
    }
}
