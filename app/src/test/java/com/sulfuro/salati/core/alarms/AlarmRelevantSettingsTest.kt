package com.sulfuro.salati.core.alarms

import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.LocationSettings
import com.sulfuro.salati.data.settings.withAlarms
import com.sulfuro.salati.data.settings.withLocation
import com.sulfuro.salati.data.settings.withPrayer
import com.sulfuro.salati.data.settings.withZakat
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRelevantSettingsTest {
    private val base = CalculationSettings()

    @Test
    fun everyRelevantFieldChangesFingerprint() {
        val changedSettings = listOf(
            base.withPrayer { copy(calculationMethod = "ISNA") },
            base.withPrayer { copy(madhab = "HANAFI") },
            base.withPrayer { copy(highLatitudeRule = "SEVENTH_OF_THE_NIGHT") },
            base.withAlarms { copy(notificationsMuted = true) },
            base.withAlarms { copy(prePrayerMinutes = 20) },
            base.withAlarms { copy(vibrateEnabled = false) },
            base.withAlarms { copy(soundEnabled = true) },
            base.withAlarms { copy(whiteDaysReminder = true) },
            base.withAlarms { copy(silentModeAutomationEnabled = true) },
            base.withAlarms { copy(silentModeMinutesAfterAdhan = 5) },
            base.withAlarms { copy(silentModeDurationMinutes = 30) },
            base.withPrayer { copy(hijriOffset = 2) },
            base.withLocation { copy(latitude = 1.0) },
            base.withLocation { copy(longitude = 2.0) },
            base.withLocation { copy(timezoneId = "UTC") }
        )

        changedSettings.forEach { changed ->
            assertNotEquals(base.alarmRelevantFingerprint(), changed.alarmRelevantFingerprint())
        }
    }

    @Test
    fun everyExcludedFieldLeavesFingerprintUnchanged() {
        val excludedChanges = listOf(
            base.withLocation { copy(cityName = "Legacy city") },
            base.withZakat { copy(goldPrice = 99.0) },
            base.withZakat { copy(nisabGram = 90.0) },
            base.withZakat { copy(silverPrice = 2.0) },
            base.withZakat { copy(nisabSilverGram = 600.0) }
        )

        excludedChanges.forEach { changed ->
            assertEquals(base.alarmRelevantFingerprint(), changed.alarmRelevantFingerprint())
        }
    }

    @Test
    fun muteChangeRequiresImmediateRefreshWhileOtherRelevantChangesAreDebounced() {
        assertEquals(
            AlarmSettingsRefreshTrigger.IMMEDIATE,
            getAlarmSettingsRefreshTrigger(base, base.withAlarms { copy(notificationsMuted = true) })
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.DEBOUNCED,
            getAlarmSettingsRefreshTrigger(base, base.withAlarms { copy(prePrayerMinutes = 20) })
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.IMMEDIATE,
            getAlarmSettingsRefreshTrigger(base, base.withAlarms { copy(whiteDaysReminder = true) })
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.IMMEDIATE,
            getAlarmSettingsRefreshTrigger(base, base.withAlarms { copy(silentModeAutomationEnabled = true) })
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.DEBOUNCED,
            getAlarmSettingsRefreshTrigger(base, base.withAlarms { copy(silentModeMinutesAfterAdhan = 5) })
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.DEBOUNCED,
            getAlarmSettingsRefreshTrigger(base, base.withPrayer { copy(hijriOffset = 1) })
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.DEBOUNCED,
            getAlarmSettingsRefreshTrigger(base, base.withLocation { copy(latitude = 1.0) })
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.NONE,
            getAlarmSettingsRefreshTrigger(base, base.withLocation { copy(cityName = "Legacy city") })
        )
    }

    @Test
    fun reconciliationQueuesAtMostOneFollowUp() {
        var calls = 0
        val started = base.alarmRelevantFingerprint()
        val changed = base.withPrayer { copy(madhab = "HANAFI") }.alarmRelevantFingerprint()

        assertTrue(enqueueOneReconciliationIfChanged(started, changed) { calls++ })
        assertEquals(1, calls)
        assertEquals(false, enqueueOneReconciliationIfChanged(changed, changed) { calls++ })
        assertEquals(1, calls)
    }

    @Test
    fun safeZoneIdFallsBackToSystemDefaultOnInvalidOrEmptyTimezone() {
        val valid = CalculationSettings(location = LocationSettings(timezoneId = "Europe/Brussels"))
        assertEquals(ZoneId.of("Europe/Brussels"), com.sulfuro.salati.data.settings.safeZoneId(valid.location.timezoneId))

        val invalid = CalculationSettings(location = LocationSettings(timezoneId = "Invalid/Timezone_Name"))
        assertEquals(ZoneId.systemDefault(), com.sulfuro.salati.data.settings.safeZoneId(invalid.location.timezoneId))

        val empty = CalculationSettings(location = LocationSettings(timezoneId = ""))
        assertEquals(ZoneId.systemDefault(), com.sulfuro.salati.data.settings.safeZoneId(empty.location.timezoneId))
    }
}
