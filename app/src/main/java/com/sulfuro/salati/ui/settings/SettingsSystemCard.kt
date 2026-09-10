package com.sulfuro.salati.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.core.notifications.AppPermissionState
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.ui.components.SalatiLogo
import com.sulfuro.salati.ui.components.SettingRow
import com.sulfuro.salati.ui.components.SettingSection

/**
 * What the operating system is currently allowing, and who to talk to about it.
 *
 * @param onRequestNotificationPermission asks for the notification permission. The
 *   launcher itself stays with the screen that observes the permission state, so the
 *   result lands where it is watched for.
 */
@Composable
internal fun SettingsSystemCard(
    settings: CalculationSettings,
    permissionState: AppPermissionState,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    var showBatteryHelpDialog by remember { mutableStateOf(false) }

    SettingSection(title = stringResource(R.string.settings_card_system_title)) {
        PermissionStatusChips(
            permissionState = permissionState,
            showDndChip = settings.silentModeAutomationEnabled,
            onNotificationsClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !permissionState.notificationPermission
                ) {
                    onRequestNotificationPermission()
                }
            },
            onExactAlarmsClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                }
            },
            onBatteryClick = { showBatteryHelpDialog = true },
            onDndClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SalatiSpacing.sm, horizontal = SalatiSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.md)
        ) {
            SalatiLogo(
                contentDescription = stringResource(R.string.app_logo_description),
                size = 56.dp
            )
            Column {
                Text(
                    text = stringResource(R.string.settings_about_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.settings_about_version, appVersionName(context)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
        SettingRow(
            title = stringResource(R.string.settings_privacy_policy),
            supportingText = stringResource(R.string.settings_privacy_policy_description),
            modifier = Modifier.clickable(role = Role.Button) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)))
                }
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.settings_privacy_policy_open)
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        val supportEmail = stringResource(R.string.settings_support_email_address)
        SettingRow(
            title = stringResource(R.string.settings_support_report_bug),
            supportingText = supportEmail,
            modifier = Modifier.clickable(role = Role.Button) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$supportEmail"))
                    )
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = stringResource(R.string.settings_support_email_open)
            )
        }
    }

    if (showBatteryHelpDialog) {
        BatteryOptimizationHelpDialog(onDismiss = { showBatteryHelpDialog = false })
    }
}
