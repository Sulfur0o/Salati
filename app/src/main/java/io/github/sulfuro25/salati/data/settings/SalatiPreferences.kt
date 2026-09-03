package io.github.sulfuro25.salati.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "salati_settings")

class SalatiPreferences(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val settingsKey = stringPreferencesKey("calculation_settings")
    private val corruptedBackupKey = stringPreferencesKey("calculation_settings_corrupted_backup")

    val settings: Flow<CalculationSettings> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[settingsKey]
        if (jsonStr != null) {
            try {
                json.decodeFromString(CalculationSettings.serializer(), jsonStr)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode saved settings; using defaults for this read", e)
                CalculationSettings()
            }
        } else {
            CalculationSettings()
        }
    }

    suspend fun updateSettings(transform: (CalculationSettings) -> CalculationSettings) {
        context.dataStore.edit { preferences ->
            val currentStr = preferences[settingsKey]
            val current = if (currentStr != null) {
                try {
                    json.decodeFromString(CalculationSettings.serializer(), currentStr)
                } catch (e: Exception) {
                    // Keep the unreadable JSON around instead of silently discarding it: a
                    // future app update or support flow may still be able to recover it,
                    // rather than the user's settings just vanishing without a trace.
                    Log.w(TAG, "Failed to decode saved settings; falling back to defaults and backing up the raw value", e)
                    preferences[corruptedBackupKey] = currentStr
                    CalculationSettings()
                }
            } else {
                CalculationSettings()
            }
            val updated = transform(current)
            preferences[settingsKey] = json.encodeToString(CalculationSettings.serializer(), updated)
        }
    }

    private companion object {
        const val TAG = "SalatiPreferences"
    }
}
