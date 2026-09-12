package com.sulfuro.salati.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.ui.components.SettingRow
import com.sulfuro.salati.ui.components.PermissionStatusRow
import com.sulfuro.salati.core.permissions.AppPermissionState

@Composable
internal fun SettingToggleRow(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingRow(
        title = title,
        supportingText = supportingText,
        modifier = Modifier.toggleable(
            value = checked,
            onValueChange = onCheckedChange,
            role = Role.Switch
        )
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                // The track used to be surfaceVariant, which is the colour of the card the
                // switch sits on, and the border was transparent - so an off switch was a
                // thumb floating on nothing, with no track to say it was a switch at all.
                uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

/**
 * What the system currently allows, one permission per line, with a button on the ones
 * that still need something from the user.
 */
@Composable
internal fun PermissionStatusChips(
    permissionState: AppPermissionState,
    showDndChip: Boolean,
    onNotificationsClick: () -> Unit,
    onExactAlarmsClick: () -> Unit,
    onBatteryClick: () -> Unit,
    onDndClick: () -> Unit
) {
    val granted = stringResource(R.string.settings_status_granted)
    val denied = stringResource(R.string.settings_status_denied)
    val allow = stringResource(R.string.settings_status_action_allow)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
    ) {
        // Shown on every version: below API 33 there is no runtime permission, but the
        // user can still switch the app's notifications off, and that is exactly the state
        // worth surfacing.
        PermissionStatusRow(
            label = stringResource(R.string.settings_status_notifications),
            statusText = if (permissionState.notificationPermission) granted else denied,
            isAllowed = permissionState.notificationPermission,
            actionText = allow,
            actionDescription = stringResource(R.string.settings_permission_notifications_allow),
            onActionClick = onNotificationsClick
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionStatusRow(
                label = stringResource(R.string.settings_status_exact_alarms),
                statusText = if (permissionState.exactAlarmAccess) granted else denied,
                isAllowed = permissionState.exactAlarmAccess,
                actionText = allow,
                actionDescription = stringResource(R.string.settings_permission_exact_alarms_allow),
                onActionClick = onExactAlarmsClick
            )
        }
        PermissionStatusRow(
            label = stringResource(R.string.settings_status_battery),
            statusText = if (permissionState.batteryOptimizationIgnored) {
                stringResource(R.string.settings_status_unrestricted)
            } else {
                stringResource(R.string.settings_status_restricted)
            },
            isAllowed = permissionState.batteryOptimizationIgnored,
            actionText = stringResource(R.string.settings_status_action_optimize),
            actionDescription = stringResource(R.string.settings_status_battery_action_description),
            onActionClick = onBatteryClick
        )
        if (showDndChip) {
            PermissionStatusRow(
                label = stringResource(R.string.settings_status_dnd),
                statusText = if (permissionState.notificationPolicyAccess) granted else denied,
                isAllowed = permissionState.notificationPolicyAccess,
                actionText = allow,
                actionDescription = stringResource(R.string.settings_status_dnd_action_description),
                onActionClick = onDndClick
            )
        }
    }
}

internal fun appVersionName(context: android.content.Context): String {
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}
