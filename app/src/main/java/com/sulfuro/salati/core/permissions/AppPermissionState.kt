package com.sulfuro.salati.core.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal data class AppPermissionState(
    val exactAlarmAccess: Boolean,
    val notificationPermission: Boolean,
    val batteryOptimizationIgnored: Boolean = false,
    val notificationPolicyAccess: Boolean = false
)

internal fun readAppPermissionState(context: Context): AppPermissionState {
    val exactAlarmAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.canScheduleExactAlarms() == true
    } else {
        true
    }
    // Both halves matter, and they are not the same question. The runtime permission is
    // all there is to ask about on API 33+, but below it there is no permission at all and
    // the user can still switch the app's notifications off in system settings - which
    // AlarmReceiver honours. Reporting only the permission meant the card could show
    // "Granted" in green, with no button, on a phone that was silently dropping every
    // prayer alert.
    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    } && NotificationManagerCompat.from(context).areNotificationsEnabled()
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val batteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    val notificationPolicyAccess = notificationManager?.isNotificationPolicyAccessGranted == true

    return AppPermissionState(
        exactAlarmAccess = exactAlarmAccess,
        notificationPermission = notificationPermission,
        batteryOptimizationIgnored = batteryOptimizationIgnored,
        notificationPolicyAccess = notificationPolicyAccess
    )
}

internal class PermissionStateRefreshController(
    initialState: AppPermissionState,
    private val enqueueRefresh: () -> Unit
) {
    private var lastObservedState = initialState

    fun onActivityResume(currentState: AppPermissionState): Boolean {
        if (currentState == lastObservedState) return false
        lastObservedState = currentState
        enqueueRefresh()
        return true
    }
}
