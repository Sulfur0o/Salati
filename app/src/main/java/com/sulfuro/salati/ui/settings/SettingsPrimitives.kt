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
import com.sulfuro.salati.ui.components.StatusPill
import com.sulfuro.salati.core.notifications.AppPermissionState

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
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

/** Shared slider styling, extracted so the two sliders cannot drift apart. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SalatiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    contentDescription: String
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ),
                sliderState = sliderState,
                modifier = Modifier.height(4.dp)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription }
    )
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionStatusActionRow(
                label = stringResource(R.string.settings_status_notifications),
                value = if (permissionState.notificationPermission) granted else denied,
                isPositive = permissionState.notificationPermission,
                actionText = allow,
                actionDescription = stringResource(R.string.settings_permission_notifications_allow),
                onAction = onNotificationsClick
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionStatusActionRow(
                label = stringResource(R.string.settings_status_exact_alarms),
                value = if (permissionState.exactAlarmAccess) granted else denied,
                isPositive = permissionState.exactAlarmAccess,
                actionText = allow,
                actionDescription = stringResource(R.string.settings_permission_exact_alarms_allow),
                onAction = onExactAlarmsClick
            )
        }
        PermissionStatusActionRow(
            label = stringResource(R.string.settings_status_battery),
            value = if (permissionState.batteryOptimizationIgnored) {
                stringResource(R.string.settings_status_unrestricted)
            } else {
                stringResource(R.string.settings_status_restricted)
            },
            isPositive = permissionState.batteryOptimizationIgnored,
            actionText = stringResource(R.string.settings_status_action_optimize),
            actionDescription = stringResource(R.string.settings_status_battery_action_description),
            onAction = onBatteryClick
        )
        if (showDndChip) {
            PermissionStatusActionRow(
                label = stringResource(R.string.settings_status_dnd),
                value = if (permissionState.notificationPolicyAccess) granted else denied,
                isPositive = permissionState.notificationPolicyAccess,
                actionText = allow,
                actionDescription = stringResource(R.string.settings_status_dnd_action_description),
                onAction = onDndClick
            )
        }
    }
}

/**
 * One permission: what its state is, and - only when something needs doing about it - the
 * button that does it.
 *
 * These used to be chips that were themselves the control, which meant the only thing that
 * could be tapped looked exactly like a label. Nothing said "Granted" was inert and
 * "Denied" was actionable, so the row that needed attention was the one that looked least
 * like a button. Now the state is a pill and the action is a button beside it, and when a
 * permission is granted the button is absent rather than disabled: nothing to tap is
 * nothing to wonder about.
 */
@Composable
private fun PermissionStatusActionRow(
    label: String,
    value: String,
    isPositive: Boolean,
    actionText: String,
    actionDescription: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPill(
            text = "$label · $value",
            containerColor = if (isPositive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
            contentColor = if (isPositive) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            // Shrinks rather than pushing the button off the row when a translation runs
            // long, which Arabic and Dutch both do here.
            modifier = Modifier.weight(1f, fill = false)
        )

        if (!isPositive) {
            Spacer(modifier = Modifier.width(SalatiSpacing.sm))
            FilledTonalButton(
                onClick = onAction,
                // Green, not the default tonal brass: the pill states the problem in red
                // and the button is the way out of it, so it wears the colour of the
                // state the user is heading towards.
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                // "Allow" on its own tells a screen reader nothing about which permission.
                modifier = Modifier.semantics { contentDescription = actionDescription }
            ) {
                Text(text = actionText)
            }
        }
    }
}

internal fun appVersionName(context: android.content.Context): String {
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}
