package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AlarmWorkScheduler {
    const val REFRESH_WORK_NAME = "salati_alarm_refresh"
    const val SETTINGS_REFRESH_DEBOUNCE_WORK_NAME = "salati_alarm_settings_refresh_debounce"
    const val PERIODIC_MAINTENANCE_WORK_NAME = "salati_alarm_periodic_maintenance"
    const val NETWORK_REFRESH_WORK_NAME = "salati_alarm_network_refresh"

    internal val refreshWorkPolicy = ExistingWorkPolicy.REPLACE

    internal val settingsDebounceWorkPolicy = ExistingWorkPolicy.REPLACE
    internal val periodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
    internal val networkWorkPolicy = ExistingWorkPolicy.KEEP
    internal const val SETTINGS_DEBOUNCE_SECONDS = 1L
    internal const val MAINTENANCE_REPEAT_HOURS = 24L
    internal const val MAINTENANCE_FLEX_HOURS = 6L

    internal fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    fun enqueueRefresh(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            REFRESH_WORK_NAME,
            refreshWorkPolicy,
            OneTimeWorkRequestBuilder<AlarmCacheRestorationWorker>().build()
        )
    }

    fun enqueueReconciliationRefresh(context: Context) {
        enqueueSettingsRefreshDebounced(context)
    }

    internal fun settingsDebounceRequest(): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<AlarmSettingsRefreshDebounceWorker>()
            .setInitialDelay(SETTINGS_DEBOUNCE_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    fun enqueueSettingsRefreshDebounced(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SETTINGS_REFRESH_DEBOUNCE_WORK_NAME,
            settingsDebounceWorkPolicy,
            settingsDebounceRequest()
        )
    }

    internal fun periodicMaintenanceRequest(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<AlarmMaintenanceWorker>(
            MAINTENANCE_REPEAT_HOURS,
            TimeUnit.HOURS,
            MAINTENANCE_FLEX_HOURS,
            TimeUnit.HOURS
        ).build()
    }

    fun registerPeriodicMaintenance(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_MAINTENANCE_WORK_NAME,
            periodicWorkPolicy,
            periodicMaintenanceRequest()
        )
    }

    fun enqueueNetworkRefresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<AlarmNetworkRefreshWorker>()
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NETWORK_REFRESH_WORK_NAME,
            networkWorkPolicy,
            request
        )
    }
}
