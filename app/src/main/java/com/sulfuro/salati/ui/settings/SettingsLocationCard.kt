package com.sulfuro.salati.ui.settings

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.core.location.DeviceLocationProvider
import com.sulfuro.salati.core.location.DeviceLocationResult
import com.sulfuro.salati.core.location.PrayerLocationResolver
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.ui.components.SettingRow
import com.sulfuro.salati.ui.components.SettingSection
import com.sulfuro.salati.ui.components.ValueSelectionRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Search
import com.sulfuro.salati.ui.components.ExpandableSettingSection
import kotlinx.coroutines.launch

/**
 * Where prayers are calculated for, and by whose rules.
 *
 * The card owns its own sheets and the GPS plumbing rather than taking them as
 * parameters: nothing outside it needs to know whether a picker is open, and keeping that
 * state here means changing the calculation method no longer invalidates every other card
 * on the screen.
 */
@Composable
internal fun SettingsLocationCard(
    settings: CalculationSettings,
    saveSettings: ((CalculationSettings) -> CalculationSettings) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

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

    var showMethodSheet by remember { mutableStateOf(false) }
    var showHighLatSheet by remember { mutableStateOf(false) }
    var showMadhabSheet by remember { mutableStateOf(false) }
    var showCitySearchSheet by remember { mutableStateOf(false) }
    var showLocationAdvanced by rememberSaveable { mutableStateOf(false) }
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
