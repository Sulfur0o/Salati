package io.github.sulfuro25.salati.ui.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.core.notifications.AppPermissionState
import io.github.sulfuro25.salati.ui.components.PermissionStatusRow

@Composable
internal fun PermissionAccessControls(
    permissionState: AppPermissionState,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarms: () -> Unit,
    showNotificationPolicyAccess: Boolean = false,
    onRequestNotificationPolicyAccess: () -> Unit = {},
    onConfigureBattery: (() -> Unit)? = null
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasNotifications = permissionState.notificationPermission
        PermissionStatusRow(
            title = stringResource(R.string.settings_permission_notifications_title),
            description = stringResource(R.string.settings_permission_notifications_description),
            statusText = stringResource(
                if (hasNotifications) R.string.settings_permission_notifications_state_allowed
                else R.string.settings_permission_notifications_state_not_allowed
            ),
            isAllowed = hasNotifications,
            onActionClick = onRequestNotifications,
            actionText = stringResource(R.string.settings_permission_notifications_allow)
        )
    }

    if (showNotificationPolicyAccess) {
        val hasPolicyAccess = permissionState.notificationPolicyAccess
        PermissionStatusRow(
            title = stringResource(R.string.settings_permission_silent_mode_title),
            description = stringResource(R.string.settings_permission_silent_mode_description),
            statusText = stringResource(
                if (hasPolicyAccess) R.string.settings_permission_silent_mode_state_allowed
                else R.string.settings_permission_silent_mode_state_not_allowed
            ),
            isAllowed = hasPolicyAccess,
            onActionClick = onRequestNotificationPolicyAccess,
            actionText = stringResource(R.string.settings_permission_silent_mode_allow)
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val hasExactAlarms = permissionState.exactAlarmAccess
        PermissionStatusRow(
            title = stringResource(R.string.settings_permission_exact_alarms_title),
            description = stringResource(
                if (hasExactAlarms) R.string.settings_permission_exact_alarms_enabled
                else R.string.settings_permission_exact_alarms_disabled
            ),
            statusText = stringResource(
                if (hasExactAlarms) R.string.settings_permission_exact_alarms_state_allowed
                else R.string.settings_permission_exact_alarms_state_not_allowed
            ),
            isAllowed = hasExactAlarms,
            onActionClick = onRequestExactAlarms,
            actionText = stringResource(R.string.settings_permission_exact_alarms_allow)
        )
    }

    if (onConfigureBattery != null) {
        val isUnrestricted = permissionState.batteryOptimizationIgnored
        PermissionStatusRow(
            title = stringResource(R.string.battery_opt_title),
            description = stringResource(
                if (isUnrestricted) R.string.battery_opt_state_unrestricted_desc
                else R.string.battery_opt_state_restricted_desc
            ),
            statusText = stringResource(
                if (isUnrestricted) R.string.battery_opt_state_unrestricted
                else R.string.battery_opt_state_restricted
            ),
            isAllowed = isUnrestricted,
            onActionClick = onConfigureBattery,
            actionText = stringResource(R.string.battery_opt_btn_open)
        )
    }
}
