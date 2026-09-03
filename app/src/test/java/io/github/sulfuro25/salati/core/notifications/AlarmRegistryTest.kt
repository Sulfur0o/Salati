package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class AlarmRegistryTest {

    private lateinit var context: Context
    private lateinit var alarmRegistry: AlarmRegistry

    @Before
    fun setup() = kotlinx.coroutines.runBlocking {
        context = ApplicationProvider.getApplicationContext()
        alarmRegistry = AlarmRegistry(context)
        alarmRegistry.clearActiveAlarms()
        alarmRegistry.setLegacyCleanupCompleted(false)
    }

    @Test
    fun testCorruptedJsonProducesCorruptedResult() = kotlinx.coroutines.runBlocking {
        // Write bad JSON
        context.alarmRegistryDataStore.edit {
            it[AlarmRegistry.KEY_ACTIVE_ALARMS] = "{ bad_json: "
        }

        val result = alarmRegistry.getActiveAlarms()
        assertTrue(result is AlarmRegistryReadResult.Corrupted)
        assertEquals("{ bad_json: ", (result as AlarmRegistryReadResult.Corrupted).rawValue)

        // Reading should not overwrite the bad JSON
        val savedValue = context.alarmRegistryDataStore.data.map { it[AlarmRegistry.KEY_ACTIVE_ALARMS] }.first()
        assertEquals("{ bad_json: ", savedValue)
    }

    @Test
    fun testSuccessfulWriteReplacesCorruptedContent() = kotlinx.coroutines.runBlocking {
        context.alarmRegistryDataStore.edit {
            it[AlarmRegistry.KEY_ACTIVE_ALARMS] = "{ bad_json: "
        }

        val alarms = listOf(
            RegisteredAlarm(
                requestCode = 123,
                uri = "salati://alarm/123",
                prayerKey = "fajr",
                isPreReminder = false,
                triggerAtMillis = 1000L,
                vibrateEnabled = true
            )
        )

        alarmRegistry.saveActiveAlarms(alarms)

        val result = alarmRegistry.getActiveAlarms()
        assertTrue(result is AlarmRegistryReadResult.Valid)
        assertEquals(1, (result as AlarmRegistryReadResult.Valid).alarms.size)
    }

    @Test
    fun testSaveAndRetrieveActiveAlarms() = kotlinx.coroutines.runBlocking {
        val alarms = listOf(
            RegisteredAlarm(
                requestCode = 123,
                uri = "salati://alarm/123",
                prayerKey = "fajr",
                isPreReminder = false,
                triggerAtMillis = 1000L,
                vibrateEnabled = true
            )
        )
        alarmRegistry.saveActiveAlarms(alarms)

        val result = alarmRegistry.getActiveAlarms()
        assertTrue(result is AlarmRegistryReadResult.Valid)
        assertEquals(alarms, (result as AlarmRegistryReadResult.Valid).alarms)
    }

    @Test
    fun testClearActiveAlarms() = kotlinx.coroutines.runBlocking {
        val alarms = listOf(
            RegisteredAlarm(
                requestCode = 123,
                uri = "salati://alarm/123",
                prayerKey = "fajr",
                isPreReminder = false,
                triggerAtMillis = 1000L,
                vibrateEnabled = true
            )
        )
        alarmRegistry.saveActiveAlarms(alarms)
        alarmRegistry.clearActiveAlarms()

        val result = alarmRegistry.getActiveAlarms()
        assertTrue(result is AlarmRegistryReadResult.Valid)
        assertTrue((result as AlarmRegistryReadResult.Valid).alarms.isEmpty())
    }

    @Test
    fun testLegacyCleanupFlag() = kotlinx.coroutines.runBlocking {
        val initial = alarmRegistry.isLegacyCleanupCompleted()
        assertTrue(!initial) // Default should be false

        alarmRegistry.setLegacyCleanupCompleted(true)
        assertTrue(alarmRegistry.isLegacyCleanupCompleted())
    }
}
