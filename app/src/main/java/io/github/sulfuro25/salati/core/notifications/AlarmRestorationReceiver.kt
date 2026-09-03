package io.github.sulfuro25.salati.core.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmRestorationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                PrayerSilentModeScheduler.requestActiveSessionReconciliation(context)
                AlarmWorkScheduler.registerPeriodicMaintenance(context)
                AlarmWorkScheduler.enqueueRefresh(context)
            }
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                AlarmWorkScheduler.enqueueRefresh(context)
            }
        }
    }
}
