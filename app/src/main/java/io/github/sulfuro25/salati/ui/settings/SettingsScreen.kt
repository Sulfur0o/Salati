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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import io.github.sulfuro25.salati.core.notifications.AppPermissionState
import io.github.sulfuro25.salati.data.settings.TimeFormatPreference
import io.github.sulfuro25.salati.ui.components.ExpandableSettingSection
import io.github.sulfuro25.salati.ui.components.SegmentedTabRow
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

/**
 * Language tag the current Activity's resources were built with on API < 33, where ""
 * means "follow the system". Written by [wrapContextForLanguage] from
 * `MainActivity.attachBaseContext`, so it always describes the resources actually in
 * use rather than whatever the deprecated configuration override happened to leave
 * behind. [applyAppLanguage] compares against it, which is what keeps a language change
 * to exactly one recreate instead of a recreate loop.
 */
@Volatile
internal var attachedLanguageTag: String = ""

/**
 * Builds the context an Activity should run on for [langCode] (API < 33). Returns [base]
 * unchanged for "system default"; otherwise returns a configuration context whose
 * resources genuinely carry the chosen locale, which survives `recreate()` because
 * `attachBaseContext` re-applies it on every Activity instance.
 */
internal fun wrapContextForLanguage(
    base: android.content.Context,
    langCode: String?
): android.content.Context {
    attachedLanguageTag = localeTagsForLanguageCode(langCode)
    if (langCode.isNullOrEmpty()) {
        java.util.Locale.setDefault(
            android.content.res.Resources.getSystem().configuration.locales[0]
        )
        return base
    }
    val target = java.util.Locale.forLanguageTag(langCode)
    java.util.Locale.setDefault(target)
    val config = android.content.res.Configuration(base.resources.configuration)
    config.setLocale(target)
    config.setLayoutDirection(target)
    return base.createConfigurationContext(config)
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
        // The Activity's resources were fixed at attachBaseContext time, so switching
        // language means rebuilding the Activity. Comparing against the tag that was
        // actually attached (rather than re-reading Resources, whose programmatic
        // override may or may not survive a recreate) makes this converge in one pass.
        if (localeTagsForLanguageCode(langCode) == attachedLanguageTag) {
            return
        }
        (context as? android.app.Activity)?.recreate()
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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

    val madhabs = listOf(
        "SHAFI" to stringResource(R.string.settings_madhab_shafi),
        "HANAFI" to stringResource(R.string.settings_madhab_hanafi)
    )

    val highLatRules = listOf(
        "MIDDLE_OF_THE_NIGHT" to stringResource(R.string.settings_high_lat_middle_of_night),
        "SEVENTH_OF_THE_NIGHT" to stringResource(R.string.settings_high_lat_seventh_of_night),
        "TWILIGHT_ANGLE" to stringResource(R.string.settings_high_lat_twilight_angle)
    )

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

    // Modal sheet / dialog visibility
    var showMethodSheet by remember { mutableStateOf(false) }
    var showHighLatSheet by remember { mutableStateOf(false) }
    var showMadhabSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showBatteryHelpDialog by remember { mutableStateOf(false) }
    var showCitySearchSheet by remember { mutableStateOf(false) }

    // Progressive disclosure: advanced blocks stay folded until asked for.
    var showLocationAdvanced by rememberSaveable { mutableStateOf(false) }
    var showAlarmsAdvanced by rememberSaveable { mutableStateOf(false) }

    var isUpdatingLocation by remember { mutableStateOf(false) }

    fun persistLocation(
        cityName: String,
        latitude: Double,
        longitude: Double,
        countryName: String? = null
    ) {
        if (isUpdatingLocation) return
        scope.launch {
            isUpdatingLocation = true
            try {
                val updated = PrayerLocationResolver.withResolvedTimezone(
                    context = appContext,
                    current = settings,
                    cityName = cityName,
                    latitude = latitude,
                    longitude = longitude,
                    countryName = countryName
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

    fun handleGpsClick() {
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

        // ------------------------------------------------------------------
        // Card 1 - Location & calculation
        // ------------------------------------------------------------------
        SettingSection(title = stringResource(R.string.settings_card_location_title)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isUpdatingLocation) {
                        Text(
                            text = stringResource(R.string.settings_location_detecting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            SettingRow(
                title = stringResource(R.string.settings_location_use_gps),
                supportingText = stringResource(R.string.settings_location_use_gps_description),
                modifier = Modifier.clickable(enabled = !isUpdatingLocation, role = Role.Button) {
                    handleGpsClick()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            SettingRow(
                title = stringResource(R.string.settings_location_search_city),
                supportingText = stringResource(R.string.settings_location_search_city_description),
                modifier = Modifier.clickable(enabled = !isUpdatingLocation, role = Role.Button) {
                    showCitySearchSheet = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            val selectedMethodName = methods.firstOrNull { it.first == settings.calculationMethod }?.second
                ?: settings.calculationMethod
            ValueSelectionRow(
                title = stringResource(R.string.settings_method_label),
                value = selectedMethodName,
                expanded = showMethodSheet,
                onExpandedChange = { showMethodSheet = it }
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            val selectedMadhabName = madhabs.firstOrNull { it.first == settings.madhab }?.second
                ?: settings.madhab
            ValueSelectionRow(
                title = stringResource(R.string.settings_madhab_label),
                value = selectedMadhabName,
                expanded = showMadhabSheet,
                onExpandedChange = { showMadhabSheet = it }
            )

            ExpandableSettingSection(
                sectionName = stringResource(R.string.settings_card_location_title),
                expanded = showLocationAdvanced,
                onExpandedChange = { showLocationAdvanced = it }
            ) {
                val selectedRuleName = highLatRules.firstOrNull { it.first == settings.highLatitudeRule }?.second
                    ?: settings.highLatitudeRule
                ValueSelectionRow(
                    title = stringResource(R.string.settings_high_latitudes_label),
                    value = selectedRuleName,
                    expanded = showHighLatSheet,
                    onExpandedChange = { showHighLatSheet = it }
                )
            }
        }

        // ------------------------------------------------------------------
        // Card 2 - Alarms & notifications
        // ------------------------------------------------------------------
        SettingSection(title = stringResource(R.string.settings_card_alarms_title)) {
            SettingToggleRow(
                title = stringResource(R.string.settings_reminders_mute),
                supportingText = stringResource(R.string.settings_reminders_mute_description),
                checked = settings.notificationsMuted,
                onCheckedChange = { isChecked -> saveSettings { it.copy(notificationsMuted = isChecked) } }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            SettingToggleRow(
                title = stringResource(R.string.settings_reminders_sound),
                supportingText = stringResource(R.string.settings_reminders_sound_description),
                checked = settings.soundEnabled,
                onCheckedChange = { isChecked -> saveSettings { it.copy(soundEnabled = isChecked) } }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            SettingToggleRow(
                title = stringResource(R.string.settings_reminders_vibration),
                supportingText = stringResource(R.string.settings_reminders_vibration_description),
                checked = settings.vibrateEnabled,
                onCheckedChange = { isChecked -> saveSettings { it.copy(vibrateEnabled = isChecked) } }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            SettingToggleRow(
                title = stringResource(R.string.settings_silent_mode_title),
                supportingText = stringResource(R.string.settings_silent_mode_description),
                checked = settings.silentModeAutomationEnabled,
                onCheckedChange = { isChecked ->
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
                }
            )

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
                    SegmentedTabRow(
                        tabs = offsetLabels,
                        selectedTabIndex = selectedOffset,
                        onTabSelected = { index ->
                            saveSettings { it.copy(silentModeMinutesAfterAdhan = offsetOptions[index]) }
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
                    SegmentedTabRow(
                        tabs = durationLabels,
                        selectedTabIndex = selectedDuration,
                        onTabSelected = { index ->
                            saveSettings { it.copy(silentModeDurationMinutes = durationOptions[index]) }
                        }
                    )
                }
            }

            ExpandableSettingSection(
                sectionName = stringResource(R.string.settings_card_alarms_title),
                expanded = showAlarmsAdvanced,
                onExpandedChange = { showAlarmsAdvanced = it }
            ) {
                SettingToggleRow(
                    title = stringResource(R.string.settings_reminders_white_days_title),
                    supportingText = stringResource(R.string.settings_reminders_white_days_description),
                    checked = settings.whiteDaysReminder,
                    onCheckedChange = { isChecked -> saveSettings { it.copy(whiteDaysReminder = isChecked) } }
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SalatiSpacing.xs, horizontal = SalatiSpacing.md)
                ) {
                    var prePrayerDraft by remember(settings.prePrayerMinutes) {
                        mutableFloatStateOf(settings.prePrayerMinutes.toFloat())
                    }
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
                    SalatiSlider(
                        value = prePrayerDraft,
                        onValueChange = { prePrayerDraft = it },
                        onValueChangeFinished = {
                            saveSettings { it.copy(prePrayerMinutes = prePrayerDraft.toInt()) }
                        },
                        valueRange = 0f..30f,
                        steps = 5,
                        contentDescription = prePrayerDesc
                    )
                }
            }
        }

        // ------------------------------------------------------------------
        // Card 3 - Appearance & regional
        // ------------------------------------------------------------------
        SettingSection(title = stringResource(R.string.settings_card_appearance_title)) {
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
                SegmentedTabRow(
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

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            val currentLangCode = settings.appLanguageCode ?: ""
            val currentLangName = languageOptions.firstOrNull { it.first == currentLangCode }?.second
                ?: stringResource(R.string.settings_language_system)
            ValueSelectionRow(
                title = stringResource(R.string.settings_language_label),
                value = currentLangName,
                expanded = showLanguageSheet,
                onExpandedChange = { showLanguageSheet = it }
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SalatiSpacing.sm, horizontal = SalatiSpacing.md)
            ) {
                Text(
                    text = stringResource(R.string.settings_time_format_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(SalatiSpacing.sm))
                val timeFormatOptions = listOf(
                    TimeFormatPreference.SYSTEM,
                    TimeFormatPreference.TWELVE_HOUR,
                    TimeFormatPreference.TWENTY_FOUR_HOUR
                )
                val timeFormatLabels = listOf(
                    stringResource(R.string.settings_time_format_system),
                    stringResource(R.string.settings_time_format_12h),
                    stringResource(R.string.settings_time_format_24h)
                )
                val selectedTimeFormat = timeFormatOptions.indexOf(settings.timeFormat)
                    .coerceAtLeast(0)
                SegmentedTabRow(
                    tabs = timeFormatLabels,
                    selectedTabIndex = selectedTimeFormat,
                    onTabSelected = { index ->
                        saveSettings { it.copy(timeFormat = timeFormatOptions[index]) }
                    }
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SalatiSpacing.xs, horizontal = SalatiSpacing.md)
            ) {
                var hijriDraft by remember(settings.hijriOffset) {
                    mutableFloatStateOf(settings.hijriOffset.toFloat())
                }
                val currentOffset = hijriDraft.toInt()
                val offsetText = when {
                    currentOffset == 0 -> stringResource(R.string.settings_calendar_hijri_offset_zero)
                    currentOffset > 0 -> pluralStringResource(
                        R.plurals.settings_calendar_hijri_offset_plus, currentOffset, currentOffset
                    )
                    else -> pluralStringResource(
                        R.plurals.settings_calendar_hijri_offset_minus, -currentOffset, -currentOffset
                    )
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
                val hijriDesc = stringResource(
                    R.string.settings_calendar_hijri_offset_accessibility, offsetText
                )
                SalatiSlider(
                    value = hijriDraft,
                    onValueChange = { hijriDraft = it },
                    onValueChangeFinished = {
                        saveSettings { it.copy(hijriOffset = hijriDraft.toInt()) }
                    },
                    valueRange = -2f..2f,
                    steps = 3,
                    contentDescription = hijriDesc
                )
            }
        }

        // ------------------------------------------------------------------
        // Card 4 - System status & permissions
        // ------------------------------------------------------------------
        SettingSection(title = stringResource(R.string.settings_card_system_title)) {
            PermissionStatusChips(
                permissionState = permissionState,
                showDndChip = settings.silentModeAutomationEnabled,
                onNotificationsClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !permissionState.notificationPermission
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

            SettingRow(
                title = stringResource(R.string.settings_about_title),
                supportingText = stringResource(R.string.settings_about_version, appVersionName(context))
            ) { }

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

        Spacer(modifier = Modifier.height(SalatiSpacing.xl))
    }

    // ----------------------------------------------------------------------
    // Sheets and dialogs
    // ----------------------------------------------------------------------
    if (showMethodSheet) {
        MethodSelectionSheet(
            selectedMethodId = settings.calculationMethod,
            methods = methods,
            onSelect = { methodId -> saveSettings { it.copy(calculationMethod = methodId) } },
            onDismiss = { showMethodSheet = false }
        )
    }

    if (showHighLatSheet) {
        OptionSelectionSheet(
            title = stringResource(R.string.settings_high_latitudes_label),
            selectedId = settings.highLatitudeRule,
            options = highLatRules,
            onSelect = { ruleId -> saveSettings { it.copy(highLatitudeRule = ruleId) } },
            onDismiss = { showHighLatSheet = false }
        )
    }

    if (showMadhabSheet) {
        OptionSelectionSheet(
            title = stringResource(R.string.settings_madhab_label),
            selectedId = settings.madhab,
            options = madhabs,
            onSelect = { madhabId -> saveSettings { it.copy(madhab = madhabId) } },
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
        BatteryOptimizationHelpDialog(onDismiss = { showBatteryHelpDialog = false })
    }

    if (showCitySearchSheet) {
        CitySearchSheet(
            onSelect = { suggestion ->
                showCitySearchSheet = false
                persistLocation(
                    cityName = suggestion.cityName,
                    latitude = suggestion.latitude,
                    longitude = suggestion.longitude,
                    countryName = suggestion.countryName
                )
            },
            onDismiss = { showCitySearchSheet = false }
        )
    }
}

/** Switch row wired for accessibility: the whole row toggles, the switch itself is mute. */
@Composable
private fun SettingToggleRow(
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
private fun SalatiSlider(
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
 * Compact permission overview. Each chip states the current grant and, where the system
 * offers a screen to change it, doubles as the shortcut to that screen.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PermissionStatusChips(
    permissionState: AppPermissionState,
    showDndChip: Boolean,
    onNotificationsClick: () -> Unit,
    onExactAlarmsClick: () -> Unit,
    onBatteryClick: () -> Unit,
    onDndClick: () -> Unit
) {
    val granted = stringResource(R.string.settings_status_granted)
    val denied = stringResource(R.string.settings_status_denied)

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StatusChip(
                label = stringResource(R.string.settings_status_notifications),
                value = if (permissionState.notificationPermission) granted else denied,
                isPositive = permissionState.notificationPermission,
                onClick = onNotificationsClick
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            StatusChip(
                label = stringResource(R.string.settings_status_exact_alarms),
                value = if (permissionState.exactAlarmAccess) granted else denied,
                isPositive = permissionState.exactAlarmAccess,
                onClick = onExactAlarmsClick
            )
        }
        StatusChip(
            label = stringResource(R.string.settings_status_battery),
            value = if (permissionState.batteryOptimizationIgnored) {
                stringResource(R.string.settings_status_unrestricted)
            } else {
                stringResource(R.string.settings_status_restricted)
            },
            isPositive = permissionState.batteryOptimizationIgnored,
            onClick = onBatteryClick
        )
        if (showDndChip) {
            StatusChip(
                label = stringResource(R.string.settings_status_dnd),
                value = if (permissionState.notificationPolicyAccess) granted else denied,
                isPositive = permissionState.notificationPolicyAccess,
                onClick = onDndClick
            )
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    value: String,
    isPositive: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text("$label · $value") },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isPositive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
            labelColor = if (isPositive) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }
        )
    )
}

private fun appVersionName(context: android.content.Context): String {
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}
