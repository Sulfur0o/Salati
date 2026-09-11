package com.sulfuro.salati.core.alarms

import com.sulfuro.salati.data.settings.CalculationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sulfuro.salati.data.settings.LocationSettings

class AlarmRelevantSettingsTest {
    private val base = CalculationSettings()

    @Test
    fun everyRelevantFieldChangesFingerprint() {
        val changedSettings = listOf(
            base.copy(prayer = base.prayer.copy(calculationMethod = "ISNA")),
            base.copy(prayer = base.prayer.copy(madhab = "HANAFI")),
            base.copy(prayer = base.prayer.copy(highLatitudeRule = "SEVENTH_OF_THE_NIGHT")),
            base.copy(alarms = base.alarms.copy(notificationsMuted = true)),
            base.copy(alarms = base.alarms.copy(prePrayerMinutes = 20)),
            base.copy(alarms = base.alarms.copy(vibrateEnabled = false)),
            base.copy(alarms = base.alarms.copy(soundEnabled = true)),
            base.copy(alarms = base.alarms.copy(whiteDaysReminder = true)),
            base.copy(alarms = base.alarms.copy(silentModeAutomationEnabled = true)),
            base.copy(alarms = base.alarms.copy(silentModeMinutesAfterAdhan = 5)),
            base.copy(alarms = base.alarms.copy(silentModeDurationMinutes = 30)),
            base.copy(prayer = base.prayer.copy(hijriOffset = 2)),
            base.copy(location = base.location.copy(latitude = 1.0)),
            base.copy(location = base.location.copy(longitude = 2.0)),
            base.copy(location = base.location.copy(timezoneId = "UTC"))
        )

        changedSettings.forEach { changed ->
            assertNotEquals(base.alarmRelevantFingerprint(), changed.alarmRelevantFingerprint())
        }
    }

    @Test
    fun everyExcludedFieldLeavesFingerprintUnchanged() {
        val excludedChanges = listOf(
            base.copy(location = base.location.copy(cityName = "Legacy city")),
            base.copy(zakat = base.zakat.copy(goldPrice = 99.0)),
            base.copy(zakat = base.zakat.copy(nisabGram = 90.0)),
            base.copy(zakat = base.zakat.copy(silverPrice = 2.0)),
            base.copy(zakat = base.zakat.copy(nisabSilverGram = 600.0))
        )

        excludedChanges.forEach { changed ->
            assertEquals(base.alarmRelevantFingerprint(), changed.alarmRelevantFingerprint())
        }
    }

    @Test
    fun muteChangeRequiresImmediateRefreshWhileOtherRelevantChangesAreDebounced() {
        assertEquals(
            AlarmSettingsRefreshTrigger.IMMEDIATE,
            getAlarmSettingsRefreshTrigger(base, base.copy(alarms = base.alarms.copy(notificationsMuted = true)))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.DEBOUNCED,
            getAlarmSettingsRefreshTrigger(base, base.copy(alarms = base.alarms.copy(prePrayerMinutes = 20)))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.IMMEDIATE,
            getAlarmSettingsRefreshTrigger(base, base.copy(alarms = base.alarms.copy(whiteDaysReminder = true)))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.IMMEDIATE,
            getAlarmSettingsRefreshTrigger(base, base.copy(alarms = base.alarms.copy(silentModeAutomationEnabled = true)))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.DEBOUNCED,
            getAlarmSettingsRefreshTrigger(base, base.copy(alarms = base.alarms.copy(silentModeMinutesAfterAdhan = 5)))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.DEBOUNCED,
            getAlarmSettingsRefreshTrigger(base, base.copy(prayer = base.prayer.copy(hijriOffset = 1)))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.DEBOUNCED,
            getAlarmSettingsRefreshTrigger(base, base.copy(location = base.location.copy(latitude = 1.0)))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.NONE,
            getAlarmSettingsRefreshTrigger(base, base.copy(location = base.location.copy(cityName = "Legacy city")))
        )
    }

    @Test
    fun reconciliationQueuesAtMostOneFollowUp() {
        var calls = 0
        val started = base.alarmRelevantFingerprint()
        val changed = base.copy(prayer = base.prayer.copy(madhab = "HANAFI")).alarmRelevantFingerprint()

        assertTrue(enqueueOneReconciliationIfChanged(started, changed) { calls++ })
        assertEquals(1, calls)
        assertEquals(false, enqueueOneReconciliationIfChanged(changed, changed) { calls++ })
        assertEquals(1, calls)
    }

    @Test
    fun safeZoneIdFallsBackToSystemDefaultOnInvalidOrEmptyTimezone() {
        val valid = CalculationSettings(location = LocationSettings(timezoneId = "Europe/Brussels"))
        assertEquals(java.time.ZoneId.of("Europe/Brussels"), com.sulfuro.salati.data.settings.safeZoneId(valid.location.timezoneId))

        val invalid = CalculationSettings(location = LocationSettings(timezoneId = "Invalid/Timezone_Name"))
        assertEquals(java.time.ZoneId.systemDefault(), com.sulfuro.salati.data.settings.safeZoneId(invalid.location.timezoneId))

        val empty = CalculationSettings(location = LocationSettings(timezoneId = ""))
        assertEquals(java.time.ZoneId.systemDefault(), com.sulfuro.salati.data.settings.safeZoneId(empty.location.timezoneId))
    }
}
