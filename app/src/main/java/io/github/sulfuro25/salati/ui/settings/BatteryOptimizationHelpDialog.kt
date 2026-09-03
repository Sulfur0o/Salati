package io.github.sulfuro25.salati.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.theme.SalatiShapeTokens
import io.github.sulfuro25.salati.theme.SalatiSpacing

@Composable
fun BatteryOptimizationHelpDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val detectedManufacturer = remember { DeviceManufacturerDetector.detect() }
    val deviceName = remember { DeviceManufacturerDetector.getDeviceDisplayName() }
    var showAllOems by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.battery_opt_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.battery_opt_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(SalatiSpacing.xs))

                Button(
                    onClick = { openBatteryOptimizationSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = SalatiShapeTokens.Control
                ) {
                    Text(text = stringResource(R.string.battery_opt_btn_open))
                }

                Spacer(modifier = Modifier.height(SalatiSpacing.sm))

                val headerTitle = if (detectedManufacturer != DeviceManufacturer.GENERIC) {
                    stringResource(R.string.battery_opt_device_detected, deviceName)
                } else {
                    stringResource(R.string.battery_opt_oem_title)
                }

                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Render ONLY the detected manufacturer's advice card
                when (detectedManufacturer) {
                    DeviceManufacturer.XIAOMI -> {
                        OemAdviceCard(
                            title = stringResource(R.string.battery_opt_xiaomi_title),
                            description = stringResource(R.string.battery_opt_xiaomi_desc)
                        )
                    }
                    DeviceManufacturer.SAMSUNG -> {
                        OemAdviceCard(
                            title = stringResource(R.string.battery_opt_samsung_title),
                            description = stringResource(R.string.battery_opt_samsung_desc)
                        )
                    }
                    DeviceManufacturer.OPPO_REALME_ONEPLUS -> {
                        OemAdviceCard(
                            title = stringResource(R.string.battery_opt_oppo_title),
                            description = stringResource(R.string.battery_opt_oppo_desc)
                        )
                    }
                    DeviceManufacturer.HUAWEI_HONOR -> {
                        OemAdviceCard(
                            title = stringResource(R.string.battery_opt_huawei_title),
                            description = stringResource(R.string.battery_opt_huawei_desc)
                        )
                    }
                    DeviceManufacturer.GENERIC -> {
                        OemAdviceCard(
                            title = stringResource(R.string.battery_opt_generic_title),
                            description = stringResource(R.string.battery_opt_generic_desc)
                        )
                    }
                }

                // Expandable section for other manufacturers
                TextButton(
                    onClick = { showAllOems = !showAllOems },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (showAllOems) stringResource(R.string.battery_opt_hide_all)
                        else stringResource(R.string.battery_opt_show_all),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                AnimatedVisibility(visible = showAllOems) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
                    ) {
                        if (detectedManufacturer != DeviceManufacturer.XIAOMI) {
                            OemAdviceCard(
                                title = stringResource(R.string.battery_opt_xiaomi_title),
                                description = stringResource(R.string.battery_opt_xiaomi_desc)
                            )
                        }
                        if (detectedManufacturer != DeviceManufacturer.SAMSUNG) {
                            OemAdviceCard(
                                title = stringResource(R.string.battery_opt_samsung_title),
                                description = stringResource(R.string.battery_opt_samsung_desc)
                            )
                        }
                        if (detectedManufacturer != DeviceManufacturer.OPPO_REALME_ONEPLUS) {
                            OemAdviceCard(
                                title = stringResource(R.string.battery_opt_oppo_title),
                                description = stringResource(R.string.battery_opt_oppo_desc)
                            )
                        }
                        if (detectedManufacturer != DeviceManufacturer.HUAWEI_HONOR) {
                            OemAdviceCard(
                                title = stringResource(R.string.battery_opt_huawei_title),
                                description = stringResource(R.string.battery_opt_huawei_desc)
                            )
                        }
                        if (detectedManufacturer != DeviceManufacturer.GENERIC) {
                            OemAdviceCard(
                                title = stringResource(R.string.battery_opt_generic_title),
                                description = stringResource(R.string.battery_opt_generic_desc)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss, shape = SalatiShapeTokens.Control) {
                Text(text = stringResource(R.string.battery_opt_close))
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun OemAdviceCard(
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SalatiShapeTokens.Control,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(SalatiSpacing.sm)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    val packageName = context.packageName
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallback) }
    }
}
