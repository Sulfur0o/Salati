package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RegisteredAlarm(
    val requestCode: Int,
    val uri: String,
    val prayerKey: String,
    val isPreReminder: Boolean,
    val triggerAtMillis: Long,
    val vibrateEnabled: Boolean,
    val soundEnabled: Boolean = false,
    val silentModeAutomationEnabled: Boolean = false,
    val silentModeMinutesAfterAdhan: Int = 0,
    val silentModeDurationMinutes: Int = 20
)

interface AlarmRegistryStore {
    suspend fun getActiveAlarms(): AlarmRegistryReadResult
    suspend fun saveActiveAlarms(alarms: List<RegisteredAlarm>)
    suspend fun clearActiveAlarms()
    suspend fun isLegacyCleanupCompleted(): Boolean
    suspend fun setLegacyCleanupCompleted(completed: Boolean)
}

val Context.alarmRegistryDataStore: DataStore<Preferences> by preferencesDataStore(name = "alarm_registry")

class AlarmRegistry(private val context: Context) : AlarmRegistryStore {
    companion object {
        val KEY_ACTIVE_ALARMS = stringPreferencesKey("active_alarms_json")
        val KEY_LEGACY_CLEANUP_COMPLETED = booleanPreferencesKey("legacy_cleanup_completed")
    }

    override suspend fun getActiveAlarms(): AlarmRegistryReadResult {
        val jsonString = context.alarmRegistryDataStore.data.map { preferences ->
            preferences[KEY_ACTIVE_ALARMS] ?: "[]"
        }.first()

        return try {
            AlarmRegistryReadResult.Valid(Json.decodeFromString(jsonString))
        } catch (e: Exception) {
            android.util.Log.e("AlarmRegistry", "Failed to parse active alarms JSON", e)
            AlarmRegistryReadResult.Corrupted(jsonString, e)
        }
    }

    override suspend fun saveActiveAlarms(alarms: List<RegisteredAlarm>) {
        val jsonString = Json.encodeToString(alarms)
        context.alarmRegistryDataStore.edit { preferences ->
            preferences[KEY_ACTIVE_ALARMS] = jsonString
        }
    }

    override suspend fun clearActiveAlarms() {
        context.alarmRegistryDataStore.edit { preferences ->
            preferences.remove(KEY_ACTIVE_ALARMS)
        }
    }

    override suspend fun isLegacyCleanupCompleted(): Boolean {
        return context.alarmRegistryDataStore.data.map { preferences ->
            preferences[KEY_LEGACY_CLEANUP_COMPLETED] ?: false
        }.first()
    }

    override suspend fun setLegacyCleanupCompleted(completed: Boolean) {
        context.alarmRegistryDataStore.edit { preferences ->
            preferences[KEY_LEGACY_CLEANUP_COMPLETED] = completed
        }
    }
}
