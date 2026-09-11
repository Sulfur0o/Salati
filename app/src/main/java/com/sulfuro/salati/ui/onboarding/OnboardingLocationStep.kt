package com.sulfuro.salati.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sulfuro.salati.R
import com.sulfuro.salati.core.location.DeviceLocationProvider
import com.sulfuro.salati.core.location.DeviceLocationResult
import com.sulfuro.salati.core.location.PrayerLocationResolver
import com.sulfuro.salati.ui.settings.CitySearchSheet
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
import kotlinx.coroutines.launch

/**
 * Where the user is, by GPS or by name. Every prayer time in the app depends on this
 * answer, which is why it is asked before anything else is configured.
 */
@Composable
internal fun LocationStep(
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
                            text = detectedLocation!!.location.cityName,
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
                        Text(text = stringResource(R.string.settings_location_search_city))
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
    }

    if (showManualLocationDialog) {
        CitySearchSheet(
            onDismiss = { showManualLocationDialog = false },
            onSelect = { suggestion ->
                showManualLocationDialog = false
                isDetecting = true
                locationError = null
                scope.launch {
                    detectedLocation = PrayerLocationResolver.withResolvedTimezone(
                        context = context,
                        current = currentSettings,
                        cityName = suggestion.cityName,
                        latitude = suggestion.latitude,
                        longitude = suggestion.longitude,
                        countryName = suggestion.countryName
                    )
                    isDetecting = false
                }
            }
        )
    }
}
