package io.github.sulfuro25.salati.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.theme.SalatiSpacing

internal data class ManualLocationInput(
    val cityName: String,
    val latitude: Double,
    val longitude: Double
)

@Composable
internal fun LocationEditDialog(
    initialCityName: String,
    initialLatitude: Double,
    initialLongitude: Double,
    onDismiss: () -> Unit,
    onSave: (ManualLocationInput) -> Unit
) {
    var cityName by remember { mutableStateOf(initialCityName) }
    var latitudeText by remember { mutableStateOf(formatCoordinate(initialLatitude)) }
    var longitudeText by remember { mutableStateOf(formatCoordinate(initialLongitude)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val invalidMessage = stringResource(R.string.settings_location_manual_invalid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_location_manual_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
            ) {
                OutlinedTextField(
                    value = cityName,
                    onValueChange = {
                        cityName = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.settings_location_manual_city)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = latitudeText,
                    onValueChange = {
                        latitudeText = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.settings_location_manual_latitude)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = longitudeText,
                    onValueChange = {
                        longitudeText = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.settings_location_manual_longitude)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val latitude = latitudeText.toDoubleOrNull()
                    val longitude = longitudeText.toDoubleOrNull()
                    val trimmedCity = cityName.trim()
                    if (trimmedCity.isEmpty() ||
                        latitude == null || longitude == null ||
                        latitude !in -90.0..90.0 ||
                        longitude !in -180.0..180.0
                    ) {
                        errorMessage = invalidMessage
                        return@TextButton
                    }
                    onSave(ManualLocationInput(trimmedCity, latitude, longitude))
                }
            ) {
                Text(stringResource(R.string.settings_location_manual_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_location_manual_cancel))
            }
        }
    )
}

private fun formatCoordinate(value: Double): String =
    "%.4f".format(java.util.Locale.US, value)
