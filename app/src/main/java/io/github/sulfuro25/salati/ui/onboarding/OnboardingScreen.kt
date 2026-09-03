package io.github.sulfuro25.salati.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.core.location.DeviceLocationProvider
import io.github.sulfuro25.salati.core.location.DeviceLocationResult
import io.github.sulfuro25.salati.core.location.PrayerLocationResolver
import io.github.sulfuro25.salati.ui.settings.LocationEditDialog
import io.github.sulfuro25.salati.core.notifications.readAppPermissionState
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import io.github.sulfuro25.salati.theme.SalatiShapeTokens
import io.github.sulfuro25.salati.theme.SalatiSpacing
import io.github.sulfuro25.salati.ui.components.PermissionStatusRow
import io.github.sulfuro25.salati.ui.components.SalatiSectionCard
import io.github.sulfuro25.salati.ui.settings.BatteryOptimizationHelpDialog
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.safeDrawingPadding

@Composable
fun OnboardingScreen(
    currentSettings: CalculationSettings,
    onComplete: (CalculationSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(1) }
    var draftSettings by remember { mutableStateOf(currentSettings) }
    val totalSteps = 4

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(SalatiSpacing.md)
    ) {
        // Step Indicator Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SalatiSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 1..totalSteps) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i <= step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(SalatiSpacing.sm))

        // Step Content with Animation
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "onboarding_step_content"
        ) { currentStep ->
            when (currentStep) {
                1 -> WelcomeStep(
                    onContinue = { step = 2 }
                )
                2 -> LocationStep(
                    currentSettings = draftSettings,
                    onLocationSelected = { updated ->
                        draftSettings = updated
                        step = 3
                    },
                    onBack = { step = 1 }
                )
                3 -> CalculationMethodStep(
                    currentMethod = draftSettings.calculationMethod,
                    onMethodSelected = { method ->
                        draftSettings = draftSettings.copy(calculationMethod = method)
                        step = 4
                    },
                    onBack = { step = 2 }
                )
                4 -> NotificationsStep(
                    draftSettings = draftSettings,
                    onSettingsChanged = { draftSettings = it },
                    onFinish = {
                        onComplete(draftSettings.copy(hasCompletedOnboarding = true))
                    },
                    onBack = { step = 3 }
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.md)
        ) {
            Spacer(modifier = Modifier.height(SalatiSpacing.md))

            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.onboarding_welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(SalatiSpacing.sm))

            FeatureOverviewCard(
                icon = Icons.Default.AccessTime,
                title = stringResource(R.string.onboarding_feat_prayers_title),
                description = stringResource(R.string.onboarding_feat_prayers_desc)
            )

            FeatureOverviewCard(
                icon = Icons.Default.Explore,
                title = stringResource(R.string.onboarding_feat_qibla_title),
                description = stringResource(R.string.onboarding_feat_qibla_desc)
            )

            FeatureOverviewCard(
                icon = Icons.Default.CalendarMonth,
                title = stringResource(R.string.onboarding_feat_calendar_title),
                description = stringResource(R.string.onboarding_feat_calendar_desc)
            )

            FeatureOverviewCard(
                icon = Icons.Default.VolunteerActivism,
                title = stringResource(R.string.onboarding_feat_zakat_title),
                description = stringResource(R.string.onboarding_feat_zakat_desc)
            )
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SalatiSpacing.md),
            shape = SalatiShapeTokens.Control
        ) {
            Text(
                text = stringResource(R.string.onboarding_btn_get_started),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FeatureOverviewCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SalatiShapeTokens.Control,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(SalatiSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LocationStep(
    currentSettings: CalculationSettings,
    onLocationSelected: (CalculationSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var isDetecting by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var detectedLocation by remember { mutableStateOf<CalculationSettings?>(null) }
    var showManualLocationDialog by remember { mutableStateOf(false) }
    val currentLocationLabel = stringResource(R.string.settings_location_current)

    fun runLocationDetection() {
        isDetecting = true
        locationError = null
        scope.launch {
            val result = DeviceLocationProvider.resolveCurrentLocation(context)
            isDetecting = false
            when (result) {
                is DeviceLocationResult.Success -> {
                    val city = result.location.cityName ?: currentLocationLabel
                    detectedLocation = PrayerLocationResolver.withResolvedTimezone(
                        context = context,
                        current = currentSettings,
                        cityName = city,
                        latitude = result.location.latitude,
                        longitude = result.location.longitude
                    )
                }
                DeviceLocationResult.PermissionDenied -> {
                    locationError = resources.getString(R.string.settings_location_error_permission)
                }
                DeviceLocationResult.LocationDisabled -> {
                    locationError = resources.getString(R.string.settings_location_error_disabled)
                }
                DeviceLocationResult.Unavailable -> {
                    locationError = resources.getString(R.string.settings_location_error_unavailable)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            runLocationDetection()
        } else {
            locationError = resources.getString(R.string.settings_location_error_permission)
        }
    }

    fun triggerDetection() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            runLocationDetection()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
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
            Spacer(modifier = Modifier.height(SalatiSpacing.sm))

            Text(
                text = stringResource(R.string.onboarding_location_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.onboarding_location_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(SalatiSpacing.md))

            // Primary Detection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = SalatiShapeTokens.Control,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(SalatiSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SalatiSpacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDetecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (detectedLocation != null) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    if (detectedLocation != null) {
                        Text(
                            text = stringResource(R.string.onboarding_location_selected),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = detectedLocation!!.cityName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.onboarding_location_auto),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.onboarding_location_auto_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = { triggerDetection() },
                        enabled = !isDetecting,
                        shape = SalatiShapeTokens.Control,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDetecting) stringResource(R.string.settings_location_detecting)
                                   else if (detectedLocation != null) stringResource(R.string.settings_location_gps_action)
                                   else stringResource(R.string.onboarding_location_detect)
                        )
                    }
                    OutlinedButton(
                        onClick = { showManualLocationDialog = true },
                        enabled = !isDetecting,
                        shape = SalatiShapeTokens.Control,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.settings_location_manual_title))
                    }
                }
            }

            if (locationError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SalatiShapeTokens.Control,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = locationError!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(SalatiSpacing.md)
                    )
                }
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
                onClick = {
                    val result = detectedLocation ?: return@Button
                    onLocationSelected(result)
                },
                enabled = detectedLocation != null,
                shape = SalatiShapeTokens.Control
            ) {
                Text(text = stringResource(R.string.onboarding_btn_continue))
            }
        }

        TextButton(
            onClick = { onLocationSelected(currentSettings) },
            enabled = !isDetecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.onboarding_location_skip))
        }
    }

    if (showManualLocationDialog) {
        LocationEditDialog(
            initialCityName = (detectedLocation ?: currentSettings).cityName,
            initialLatitude = (detectedLocation ?: currentSettings).latitude,
            initialLongitude = (detectedLocation ?: currentSettings).longitude,
            onDismiss = { showManualLocationDialog = false },
            onSave = { input ->
                showManualLocationDialog = false
                isDetecting = true
                locationError = null
                scope.launch {
                    detectedLocation = PrayerLocationResolver.withResolvedTimezone(
                        context = context,
                        current = currentSettings,
                        cityName = input.cityName,
                        latitude = input.latitude,
                        longitude = input.longitude
                    )
                    isDetecting = false
                }
            }
        )
    }
}

@Composable
private fun CalculationMethodStep(
    currentMethod: String,
    onMethodSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf(currentMethod) }

    val methods = listOf(
        "MUSLIM_WORLD_LEAGUE" to stringResource(R.string.settings_method_mwl),
        "UMM_AL_QURA" to stringResource(R.string.settings_method_umm_al_qura),
        "ISNA" to stringResource(R.string.settings_method_isna),
        "EGYPT" to stringResource(R.string.settings_method_egypt),
        "KARACHI" to stringResource(R.string.settings_method_karachi),
        "DUBAI" to stringResource(R.string.settings_method_dubai),
        "KUWAIT" to stringResource(R.string.settings_method_kuwait),
        "QATAR" to stringResource(R.string.settings_method_qatar),
        "MOON_SIGHTING" to stringResource(R.string.settings_method_moon_sighting)
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
        ) {
            Text(
                text = stringResource(R.string.onboarding_method_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.onboarding_method_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(SalatiSpacing.xs))

            methods.forEach { (id, name) ->
                val isSelected = selectedMethod == id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMethod = id },
                    shape = SalatiShapeTokens.Control,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.sm)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedMethod = id }
                        )
                    }
                }
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
                onClick = { onMethodSelected(selectedMethod) },
                shape = SalatiShapeTokens.Control
            ) {
                Text(text = stringResource(R.string.onboarding_btn_continue))
            }
        }
    }
}

@Composable
private fun NotificationsStep(
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
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
                        text = "Required Permissions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // 1. Post Notifications (Android 13+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasNotification = permissionState.notificationPermission
                        PermissionStatusRow(
                            title = stringResource(R.string.settings_permission_notifications_title),
                            description = stringResource(R.string.settings_permission_notifications_description),
                            statusText = stringResource(
                                if (hasNotification) R.string.settings_permission_notifications_state_allowed
                                else R.string.settings_permission_notifications_state_not_allowed
                            ),
                            isAllowed = hasNotification,
                            onActionClick = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            actionText = stringResource(R.string.settings_permission_notifications_allow)
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    // 2. Exact Alarms (Android 12+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val hasExact = permissionState.exactAlarmAccess
                        PermissionStatusRow(
                            title = stringResource(R.string.settings_permission_exact_alarms_title),
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
                                        data = Uri.parse("package:${context.packageName}")
                                    })
                                }
                            },
                            actionText = stringResource(R.string.settings_permission_exact_alarms_allow)
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    // 3. Battery Optimization
                    val isBatteryUnrestricted = permissionState.batteryOptimizationIgnored
                    PermissionStatusRow(
                        title = stringResource(R.string.battery_opt_title),
                        description = if (isBatteryUnrestricted) "Battery saver does not restrict prayer alarms"
                                      else "Prevent system battery saver from delaying prayer reminders",
                        statusText = if (isBatteryUnrestricted) "Unrestricted" else "Restricted",
                        isAllowed = isBatteryUnrestricted,
                        onActionClick = { showBatteryHelp = true },
                        actionText = "Optimize"
                    )
                }
            }

            // Notification Preference Toggles
            SalatiSectionCard {
                Column(modifier = Modifier.padding(SalatiSpacing.md), verticalArrangement = Arrangement.spacedBy(SalatiSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_reminders_vibration),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.settings_reminders_vibration_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = draftSettings.vibrateEnabled,
                            onCheckedChange = { onSettingsChanged(draftSettings.copy(vibrateEnabled = it)) }
                        )
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_reminders_white_days_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.settings_reminders_white_days_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = draftSettings.whiteDaysReminder,
                            onCheckedChange = { onSettingsChanged(draftSettings.copy(whiteDaysReminder = it)) }
                        )
                    }
                }
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
