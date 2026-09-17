package com.sulfuro.salati.ui.onboarding

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sulfuro.salati.R
import com.sulfuro.salati.core.permissions.readAppPermissionState
import com.sulfuro.salati.core.permissions.rememberNotificationPermissionRequest
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.withAlarms
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.ui.battery.BatteryOptimizationHelpDialog
import com.sulfuro.salati.ui.components.PermissionStatusRow
import com.sulfuro.salati.ui.components.SalatiSectionCard
import com.sulfuro.salati.ui.settings.SettingToggleRow
import com.sulfuro.salati.ui.settings.SettingsDivider

/** The permissions the alarms need, asked for with the reason attached. */
@Composable
internal fun NotificationsStep(
    draftSettings: CalculationSettings,
    onSettingsChanged: (CalculationSettings) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showBatteryHelp by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionState by remember { mutableStateOf(readAppPermissionState(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = readAppPermissionState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Falls through to system settings once Android stops showing the dialog. Onboarding
    // is the worst place for an inert button: the next tap is "Finish".
    val requestNotificationPermission = rememberNotificationPermissionRequest {
        permissionState = readAppPermissionState(context)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.onboarding_notifications_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.onboarding_notifications_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Permissions Status & Actions Section
            SalatiSectionCard {
                Column(
                    modifier = Modifier.padding(SalatiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_required_permissions),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // 1. Post Notifications (Android 13+)
                    val hasNotification = permissionState.notificationPermission
                    PermissionStatusRow(
                        label = stringResource(R.string.settings_permission_notifications_title),
                        description = stringResource(R.string.settings_permission_notifications_description),
                        statusText = stringResource(
                            if (hasNotification) R.string.settings_permission_notifications_state_allowed
                            else R.string.settings_permission_notifications_state_not_allowed
                        ),
                        isAllowed = hasNotification,
                        onActionClick = requestNotificationPermission,
                        actionText = stringResource(R.string.settings_permission_notifications_allow),
                        actionDescription = stringResource(R.string.settings_permission_notifications_allow)
                    )
                    SettingsDivider()

                    // 2. Exact Alarms (Android 12+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val hasExact = permissionState.exactAlarmAccess
                        PermissionStatusRow(
                            label = stringResource(R.string.settings_permission_exact_alarms_title),
                            description = stringResource(
                                if (hasExact) R.string.settings_permission_exact_alarms_enabled
                                else R.string.settings_permission_exact_alarms_disabled
                            ),
                            statusText = stringResource(
                                if (hasExact) R.string.settings_permission_exact_alarms_state_allowed
                                else R.string.settings_permission_exact_alarms_state_not_allowed
                            ),
                            isAllowed = hasExact,
                            onActionClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = "package:${context.packageName}".toUri()
                                    })
                                }
                            },
                            actionText = stringResource(R.string.settings_permission_exact_alarms_allow),
                            actionDescription = stringResource(R.string.settings_permission_exact_alarms_allow)
                        )
                        SettingsDivider()
                    }

                    // 3. Battery Optimization
                    val isBatteryUnrestricted = permissionState.batteryOptimizationIgnored
                    PermissionStatusRow(
                        label = stringResource(R.string.battery_opt_title),
                        description = stringResource(
                            if (isBatteryUnrestricted) R.string.battery_opt_state_unrestricted_desc
                            else R.string.battery_opt_state_restricted_desc
                        ),
                        statusText = stringResource(
                            if (isBatteryUnrestricted) R.string.battery_opt_state_unrestricted
                            else R.string.battery_opt_state_restricted
                        ),
                        isAllowed = isBatteryUnrestricted,
                        onActionClick = { showBatteryHelp = true },
                        actionText = stringResource(R.string.onboarding_action_optimize),
                        actionDescription = stringResource(R.string.settings_status_battery_action_description)
                    )
                }
            }

            // The same rows the Settings screen uses. Hand-rolled Row + Switch left the
            // switch itself unlabelled, so a screen reader read the two lines of text and
            // then an anonymous "Switch, on"; SettingToggleRow makes the whole row the
            // control and silences the switch, which is also what the rest of the app does.
            SalatiSectionCard {
                SettingToggleRow(
                    title = stringResource(R.string.settings_reminders_vibration),
                    supportingText = stringResource(R.string.settings_reminders_vibration_description),
                    checked = draftSettings.alarms.vibrateEnabled,
                    onCheckedChange = {
                        onSettingsChanged(
                            draftSettings.withAlarms { copy(vibrateEnabled = it) }
                        )
                    }
                )

                SettingsDivider()

                SettingToggleRow(
                    title = stringResource(R.string.settings_reminders_white_days_title),
                    supportingText = stringResource(R.string.settings_reminders_white_days_description),
                    checked = draftSettings.alarms.whiteDaysReminder,
                    onCheckedChange = {
                        onSettingsChanged(
                            draftSettings.withAlarms { copy(whiteDaysReminder = it) }
                        )
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SalatiSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack, shape = SalatiShapeTokens.Control) {
                Text(text = stringResource(R.string.onboarding_btn_back))
            }
            Button(
                onClick = onFinish,
                shape = SalatiShapeTokens.Control
            ) {
                Text(text = stringResource(R.string.onboarding_btn_finish))
            }
        }
    }

    if (showBatteryHelp) {
        BatteryOptimizationHelpDialog(
            onDismiss = { showBatteryHelp = false }
        )
    }
}
