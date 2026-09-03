package io.github.sulfuro25.salati.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.core.computation.zakatCurrencyOptions
import io.github.sulfuro25.salati.core.location.DeviceLocationProvider
import io.github.sulfuro25.salati.core.location.DeviceLocationResult
import io.github.sulfuro25.salati.core.location.PrayerLocationResolver
import io.github.sulfuro25.salati.core.notifications.enqueueAlarmSettingsRefreshIfNeeded
import io.github.sulfuro25.salati.core.notifications.PrayerSilentModeController
import io.github.sulfuro25.salati.core.notifications.PrayerSilentModeScheduler
import io.github.sulfuro25.salati.core.notifications.readAppPermissionState
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import io.github.sulfuro25.salati.data.settings.SalatiPreferences
import io.github.sulfuro25.salati.theme.SalatiSpacing
import io.github.sulfuro25.salati.ui.components.SettingRow
import io.github.sulfuro25.salati.ui.components.SettingSection
import io.github.sulfuro25.salati.ui.components.ValueSelectionRow
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


internal object LoadedSettingsCache {
    @Volatile
    var latest: CalculationSettings? = null
}

internal fun localeTagsForLanguageCode(langCode: String?): String {
    return langCode.orEmpty()
}

internal fun shouldUpdateApplicationLocales(currentTags: String, langCode: String?): Boolean {
    return currentTags != localeTagsForLanguageCode(langCode)
}

internal fun applyAppLanguage(context: android.content.Context, langCode: String?) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val localeManager = context.getSystemService(android.app.LocaleManager::class.java) ?: return
        val localeList = if (langCode.isNullOrEmpty()) {
            android.os.LocaleList.getEmptyLocaleList()
        } else {
            android.os.LocaleList.forLanguageTags(langCode)
        }
        if (!shouldUpdateApplicationLocales(localeManager.applicationLocales.toLanguageTags(), langCode)) {
            return
        }
        localeManager.applicationLocales = localeList
    } else {
        val locale = if (langCode.isNullOrEmpty()) {
            java.util.Locale.getDefault()
        } else {
            java.util.Locale.forLanguageTag(langCode)
        }
        val current = context.resources.configuration.locales[0]
        if (!langCode.isNullOrEmpty() && current.toLanguageTag().equals(locale.toLanguageTag(), ignoreCase = true)) {
            return
        }
        java.util.Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: CalculationSettings,
    preferences: SalatiPreferences,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appContext = context.applicationContext

    // Permission state observer
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionState by remember { mutableStateOf(readAppPermissionState(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissionState = readAppPermissionState(context)
    }

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

    // Calculation Methods
    val methods = listOf(
        "MUSLIM_WORLD_LEAGUE" to stringResource(R.string.settings_method_mwl),
        "ISNA" to stringResource(R.string.settings_method_isna),
        "EGYPT" to stringResource(R.string.settings_method_egypt),
        "UMM_AL_QURA" to stringResource(R.string.settings_method_umm_al_qura),
        "KARACHI" to stringResource(R.string.settings_method_karachi),
        "KUWAIT" to stringResource(R.string.settings_method_kuwait),
        "QATAR" to stringResource(R.string.settings_method_qatar),
        "DUBAI" to stringResource(R.string.settings_method_dubai),
        "SINGAPORE" to stringResource(R.string.settings_method_singapore),
        "MOON_SIGHTING" to stringResource(R.string.settings_method_moon_sighting)
    )

    // Madhabs
    val madhabs = listOf(
        "SHAFI" to stringResource(R.string.settings_madhab_shafi),
        "HANAFI" to stringResource(R.string.settings_madhab_hanafi)
    )

    // High Latitude rules
    val highLatRules = listOf(
        "MIDDLE_OF_THE_NIGHT" to stringResource(R.string.settings_high_lat_middle_of_night),
        "SEVENTH_OF_THE_NIGHT" to stringResource(R.string.settings_high_lat_seventh_of_night),
        "TWILIGHT_ANGLE" to stringResource(R.string.settings_high_lat_twilight_angle)
    )

    // Language options
    val languageOptions = listOf(
        "" to stringResource(R.string.settings_language_system),
        "en" to stringResource(R.string.settings_language_en),
        "ar" to stringResource(R.string.settings_language_ar),
        "fr" to stringResource(R.string.settings_language_fr),
        "nl" to stringResource(R.string.settings_language_nl)
    )

    val saveSettings = { transform: (CalculationSettings) -> CalculationSettings ->
        scope.launch {
            var settingsChange: Pair<CalculationSettings, CalculationSettings>? = null
            preferences.updateSettings { current ->
                val updated = transform(current)
                settingsChange = current to updated
                updated
            }
            settingsChange?.let { (previous, updated) ->
                enqueueAlarmSettingsRefreshIfNeeded(appContext, previous, updated)
            }
        }
    }

    // Modal BottomSheet states
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showMethodSheet by remember { mutableStateOf(false) }
    var showHighLatSheet by remember { mutableStateOf(false) }
    var showMadhabSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showBatteryHelpDialog by remember { mutableStateOf(false) }
    var showManualLocationDialog by remember { mutableStateOf(false) }

    var isUpdatingLocation by remember { mutableStateOf(false) }

    fun persistLocation(cityName: String, latitude: Double, longitude: Double) {
        if (isUpdatingLocation) return
        scope.launch {
            isUpdatingLocation = true
            try {
                val updated = PrayerLocationResolver.withResolvedTimezone(
                    context = appContext,
                    current = settings,
                    cityName = cityName,
                    latitude = latitude,
                    longitude = longitude
                )
                saveSettings { updated }
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.settings_location_updated, updated.cityName),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isUpdatingLocation = false
            }
        }
    }

    fun triggerGpsUpdate() {
        if (isUpdatingLocation) return
        scope.launch {
            isUpdatingLocation = true
            try {
            when (val result = DeviceLocationProvider.resolveCurrentLocation(appContext)) {
                is DeviceLocationResult.Success -> {
                    val resolved = result.location
                    val resolvedCity = resolved.cityName
                        ?: appContext.getString(R.string.settings_location_current)
                    val updated = PrayerLocationResolver.withResolvedTimezone(
                        context = appContext,
                        current = settings,
                        cityName = resolvedCity,
                        latitude = resolved.latitude,
                        longitude = resolved.longitude
                    )
                    saveSettings { updated }
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.settings_location_updated, updated.cityName),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                DeviceLocationResult.PermissionDenied -> {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.settings_location_error_permission),
                        Toast.LENGTH_LONG
                    ).show()
                }
                DeviceLocationResult.LocationDisabled -> {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.settings_location_error_disabled),
                        Toast.LENGTH_LONG
                    ).show()
                }
                DeviceLocationResult.Unavailable -> {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.settings_location_error_unavailable),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            } finally {
                isUpdatingLocation = false
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            triggerGpsUpdate()
        } else {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.settings_location_error_permission),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun handleLocationClick() {
        if (DeviceLocationProvider.hasLocationPermission(appContext)) {
            triggerGpsUpdate()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = SalatiSpacing.md, end = SalatiSpacing.md, bottom = SalatiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // 1. Prayer location (Single 1-tap GPS Location row)
        SettingSection(title = stringResource(R.string.settings_location)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isUpdatingLocation) { handleLocationClick() }
                    .padding(vertical = SalatiSpacing.sm, horizontal = SalatiSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isUpdatingLocation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settings.cityName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isUpdatingLocation) {
                        Text(
                            text = stringResource(R.string.settings_location_detecting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!isUpdatingLocation) {
                    IconButton(onClick = { handleLocationClick() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.settings_location_gps_action),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            SettingRow(
                title = stringResource(R.string.settings_location_manual_title),
                supportingText = stringResource(R.string.settings_location_manual_description),
                modifier = Modifier.clickable(role = Role.Button) {
                    showManualLocationDialog = true
                }
            ) { }
        }

        // Zakat currency
        val currentCurrency = zakatCurrencyOptions.firstOrNull { it.code == settings.zakatCurrencyCode }
        val currentCurrencyLabel = if (currentCurrency != null) {
            "${currentCurrency.code} (${currentCurrency.symbol}) — ${currentCurrency.displayName}"
        } else {
            settings.zakatCurrencyCode
        }
        SettingSection(title = stringResource(R.string.settings_zakat_currency_title)) {
            ValueSelectionRow(
                title = stringResource(R.string.settings_zakat_currency_label),
                value = currentCurrencyLabel,
                expanded = showCurrencySheet,
                onExpandedChange = { showCurrencySheet = it }
            )
        }

        // 2. Calculation Settings
        SettingSection(title = stringResource(R.string.settings_calculation_title)) {
            val selectedMethodName = methods.firstOrNull { it.first == settings.calculationMethod }?.second ?: settings.calculationMethod
            ValueSelectionRow(
                title = stringResource(R.string.settings_method_label),
                value = selectedMethodName,
                expanded = showMethodSheet,
                onExpandedChange = { showMethodSheet = it }
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            val selectedRuleName = highLatRules.firstOrNull { it.first == settings.highLatitudeRule }?.second ?: settings.highLatitudeRule
            ValueSelectionRow(
                title = stringResource(R.string.settings_high_latitudes_label),
                value = selectedRuleName,
                expanded = showHighLatSheet,
                onExpandedChange = { showHighLatSheet = it }
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            val selectedMadhabName = madhabs.firstOrNull { it.first == settings.madhab }?.second ?: settings.madhab
            ValueSelectionRow(
                title = stringResource(R.string.settings_madhab_label),
                value = selectedMadhabName,
                expanded = showMadhabSheet,
                onExpandedChange = { showMadhabSheet = it }
            )
        }

        // 3. Alarms & Reminders
        SettingSection(title = stringResource(R.string.settings_reminders_title)) {
            PermissionAccessControls(
                permissionState = permissionState,
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onRequestExactAlarms = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            })
                        }
                    }
                },
                showNotificationPolicyAccess = settings.silentModeAutomationEnabled,
                onRequestNotificationPolicyAccess = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                },
                onConfigureBattery = { showBatteryHelpDialog = true }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            SettingRow(
                title = stringResource(R.string.settings_reminders_mute),
                supportingText = stringResource(R.string.settings_reminders_mute_description),
                modifier = Modifier.toggleable(
                    value = settings.notificationsMuted,
                    onValueChange = { isChecked ->
                        saveSettings { it.copy(notificationsMuted = isChecked) }
                    },
                    role = Role.Switch
                )
            ) {
                Switch(
                    checked = settings.notificationsMuted,
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
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            SettingRow(
                title = stringResource(R.string.settings_reminders_vibration),
                supportingText = stringResource(R.string.settings_reminders_vibration_description),
                modifier = Modifier.toggleable(
                    value = settings.vibrateEnabled,
                    onValueChange = { isChecked ->
                        saveSettings { it.copy(vibrateEnabled = isChecked) }
                    },
                    role = Role.Switch
                )
            ) {
                Switch(
                    checked = settings.vibrateEnabled,
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
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            SettingRow(
                title = stringResource(R.string.settings_silent_mode_title),
                supportingText = stringResource(R.string.settings_silent_mode_description),
                modifier = Modifier.toggleable(
                    value = settings.silentModeAutomationEnabled,
                    onValueChange = { isChecked ->
                        PrayerSilentModeScheduler.setAutomationEnabled(appContext, isChecked)
                        saveSettings { it.copy(silentModeAutomationEnabled = isChecked) }
                        if (isChecked && !permissionState.notificationPolicyAccess) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                )
                            }
                        } else if (!isChecked) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    PrayerSilentModeController.forceRestore(appContext)
                                }
                            }
                        }
                    },
                    role = Role.Switch
                )
            ) {
                Switch(
                    checked = settings.silentModeAutomationEnabled,
                    onCheckedChange = null,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }

            if (settings.silentModeAutomationEnabled) {
                val offsetOptions = listOf(0, 5, 10, 15)
                val offsetLabels = listOf(
                    stringResource(R.string.settings_silent_mode_offset_now),
                    stringResource(R.string.settings_silent_mode_offset_5),
                    stringResource(R.string.settings_silent_mode_offset_10),
                    stringResource(R.string.settings_silent_mode_offset_15)
                )
                val selectedOffset = offsetOptions.indexOf(settings.silentModeMinutesAfterAdhan)
                    .coerceAtLeast(0)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
                ) {
                    Text(
                        text = stringResource(R.string.settings_silent_mode_offset_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    io.github.sulfuro25.salati.ui.components.SegmentedTabRow(
                        tabs = offsetLabels,
                        selectedTabIndex = selectedOffset,
                        onTabSelected = { index ->
                            saveSettings {
                                it.copy(silentModeMinutesAfterAdhan = offsetOptions[index])
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(SalatiSpacing.xs))

                    val durationOptions = listOf(15, 20, 30)
                    val durationLabels = listOf(
                        stringResource(R.string.settings_silent_mode_duration_15),
                        stringResource(R.string.settings_silent_mode_duration_20),
                        stringResource(R.string.settings_silent_mode_duration_30)
                    )
                    val selectedDuration = durationOptions.indexOf(settings.silentModeDurationMinutes)
                        .coerceAtLeast(0)
                    Text(
                        text = stringResource(R.string.settings_silent_mode_duration_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    io.github.sulfuro25.salati.ui.components.SegmentedTabRow(
                        tabs = durationLabels,
                        selectedTabIndex = selectedDuration,
                        onTabSelected = { index ->
                            saveSettings {
                                it.copy(silentModeDurationMinutes = durationOptions[index])
                            }
                        }
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)


            SettingRow(
                title = stringResource(R.string.settings_reminders_white_days_title),
                supportingText = stringResource(R.string.settings_reminders_white_days_description),
                modifier = Modifier.toggleable(
                    value = settings.whiteDaysReminder,
                    onValueChange = { isChecked ->
                        saveSettings { it.copy(whiteDaysReminder = isChecked) }
                    },
                    role = Role.Switch
                )
            ) {
                Switch(
                    checked = settings.whiteDaysReminder,
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
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Pre-prayer alert slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SalatiSpacing.xs, horizontal = SalatiSpacing.md)
            ) {
                var prePrayerDraft by remember(settings.prePrayerMinutes) { mutableFloatStateOf(settings.prePrayerMinutes.toFloat()) }
                val prePrayerMinutes = prePrayerDraft.toInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_reminders_pre_prayer),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_reminders_pre_prayer_value,
                            prePrayerMinutes
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val prePrayerDesc = stringResource(
                    R.string.settings_reminders_pre_prayer_accessibility,
                    prePrayerMinutes
                )
                Slider(
                    value = prePrayerDraft,
                    onValueChange = { value -> prePrayerDraft = value },
                    onValueChangeFinished = {
                        saveSettings { it.copy(prePrayerMinutes = prePrayerDraft.toInt()) }
                    },
                    valueRange = 0f..30f,
                    steps = 5,
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
                        .semantics {
                            contentDescription = prePrayerDesc
                        }
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Hijri day offset
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SalatiSpacing.xs, horizontal = SalatiSpacing.md)
            ) {
                var hijriDraft by remember(settings.hijriOffset) { mutableFloatStateOf(settings.hijriOffset.toFloat()) }
                val currentOffset = hijriDraft.toInt()
                val offsetText = when {
                    currentOffset == 0 -> stringResource(R.string.settings_calendar_hijri_offset_zero)
                    currentOffset > 0 -> pluralStringResource(R.plurals.settings_calendar_hijri_offset_plus, currentOffset, currentOffset)
                    else -> pluralStringResource(R.plurals.settings_calendar_hijri_offset_minus, -currentOffset, -currentOffset)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_calendar_hijri_offset),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = offsetText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val hijriDesc = stringResource(R.string.settings_calendar_hijri_offset_accessibility, offsetText)
                Slider(
                    value = hijriDraft,
                    onValueChange = { value -> hijriDraft = value },
                    onValueChangeFinished = {
                        saveSettings { it.copy(hijriOffset = hijriDraft.toInt()) }
                    },
                    valueRange = -2f..2f,
                    steps = 3,
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
                        .semantics {
                            contentDescription = hijriDesc
                        }
                )
            }
        }

        // 4. App Theme
        SettingSection(title = stringResource(R.string.settings_theme_title)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SalatiSpacing.sm, horizontal = SalatiSpacing.md)
            ) {
                Text(
                    text = stringResource(R.string.settings_theme_dark_mode),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.settings_theme_dark_mode_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(SalatiSpacing.sm))
                val themeOptionLabels = listOf(
                    stringResource(R.string.settings_theme_option_system),
                    stringResource(R.string.settings_theme_option_light),
                    stringResource(R.string.settings_theme_option_dark)
                )
                val selectedThemeIndex = when (settings.isDarkMode) {
                    null -> 0
                    false -> 1
                    true -> 2
                }
                io.github.sulfuro25.salati.ui.components.SegmentedTabRow(
                    tabs = themeOptionLabels,
                    selectedTabIndex = selectedThemeIndex,
                    onTabSelected = { index ->
                        val newValue = when (index) {
                            1 -> false
                            2 -> true
                            else -> null
                        }
                        saveSettings { it.copy(isDarkMode = newValue) }
                    }
                )
            }
        }

        
        // 5. App Language
        val currentLangCode = settings.appLanguageCode ?: ""
        val currentLangName = languageOptions.firstOrNull { it.first == currentLangCode }?.second
            ?: stringResource(R.string.settings_language_system)
        SettingSection(title = stringResource(R.string.settings_language_title)) {
            ValueSelectionRow(
                title = stringResource(R.string.settings_language_label),
                value = currentLangName,
                expanded = showLanguageSheet,
                onExpandedChange = { showLanguageSheet = it }
            )
        }

        val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
        SettingSection(title = stringResource(R.string.settings_privacy_title)) {
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
        }

        val supportEmail = stringResource(R.string.settings_support_email_address)
        SettingSection(title = stringResource(R.string.settings_support_title)) {
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

        Spacer(modifier = Modifier.height(SalatiSpacing.xl))
    }

    // Modal BottomSheets
    if (showCurrencySheet) {
        CurrencySelectionSheet(
            selectedCode = settings.zakatCurrencyCode,
            onSelect = { code ->
                saveSettings { it.copy(zakatCurrencyCode = code) }
            },
            onDismiss = { showCurrencySheet = false }
        )
    }

    if (showMethodSheet) {
        MethodSelectionSheet(
            selectedMethodId = settings.calculationMethod,
            methods = methods,
            onSelect = { methodId ->
                saveSettings { it.copy(calculationMethod = methodId) }
            },
            onDismiss = { showMethodSheet = false }
        )
    }

    if (showHighLatSheet) {
        OptionSelectionSheet(
            title = stringResource(R.string.settings_high_latitudes_label),
            selectedId = settings.highLatitudeRule,
            options = highLatRules,
            onSelect = { ruleId ->
                saveSettings { it.copy(highLatitudeRule = ruleId) }
            },
            onDismiss = { showHighLatSheet = false }
        )
    }

    if (showMadhabSheet) {
        OptionSelectionSheet(
            title = stringResource(R.string.settings_madhab_label),
            selectedId = settings.madhab,
            options = madhabs,
            onSelect = { madhabId ->
                saveSettings { it.copy(madhab = madhabId) }
            },
            onDismiss = { showMadhabSheet = false }
        )
    }



    
    if (showLanguageSheet) {
        OptionSelectionSheet(
            title = stringResource(R.string.settings_language_title),
            selectedId = settings.appLanguageCode ?: "",
            options = languageOptions,
            onSelect = { langCode ->
                val codeOrNull: String? = if (langCode.isEmpty()) null else langCode
                LoadedSettingsCache.latest = settings.copy(appLanguageCode = codeOrNull)
                saveSettings { it.copy(appLanguageCode = codeOrNull) }
                applyAppLanguage(context, codeOrNull)
            },
            onDismiss = { showLanguageSheet = false }
        )
    }

    if (showBatteryHelpDialog) {
        BatteryOptimizationHelpDialog(
            onDismiss = { showBatteryHelpDialog = false }
        )
    }

    if (showManualLocationDialog) {
        LocationEditDialog(
            initialCityName = settings.cityName,
            initialLatitude = settings.latitude,
            initialLongitude = settings.longitude,
            onDismiss = { showManualLocationDialog = false },
            onSave = { input ->
                showManualLocationDialog = false
                persistLocation(input.cityName, input.latitude, input.longitude)
            }
        )
    }
}
