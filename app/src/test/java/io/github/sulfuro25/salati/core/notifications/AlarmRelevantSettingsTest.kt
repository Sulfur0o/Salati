package io.github.sulfuro25.salati.core.notifications

import io.github.sulfuro25.salati.data.settings.CalculationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRelevantSettingsTest {
    private val base = CalculationSettings()

    @Test
    fun everyRelevantFieldChangesFingerprint() {
        val changedSettings = listOf(
            base.copy(calculationMethod = "ISNA"),
            base.copy(madhab = "HANAFI"),
            base.copy(highLatitudeRule = "SEVENTH_OF_THE_NIGHT"),
            base.copy(notificationsMuted = true),
            base.copy(prePrayerMinutes = 20),
            base.copy(vibrateEnabled = false),
            base.copy(soundEnabled = true),
            base.copy(whiteDaysReminder = true),
            base.copy(silentModeAutomationEnabled = true),
            base.copy(silentModeMinutesAfterAdhan = 5),
            base.copy(silentModeDurationMinutes = 30),
            base.copy(hijriOffset = 2),
            base.copy(latitude = 1.0),
            base.copy(longitude = 2.0),
            base.copy(timezoneId = "UTC")
        )

        changedSettings.forEach { changed ->
            assertNotEquals(base.alarmRelevantFingerprint(), changed.alarmRelevantFingerprint())
        }
    }

    @Test
    fun everyExcludedFieldLeavesFingerprintUnchanged() {
        val excludedChanges = listOf(
            base.copy(cityName = "Legacy city"),
            base.copy(zakatGoldPrice = 99.0),
            base.copy(zakatNisabGram = 90.0),
            base.copy(zakatSilverPrice = 2.0),
            base.copy(zakatNisabSilverGram = 600.0)
        )

        excludedChanges.forEach { changed ->
            assertEquals(base.alarmRelevantFingerprint(), changed.alarmRelevantFingerprint())
        }
    }

    @Test
    fun muteChangeRequiresImmediateRefreshWhileOtherRelevantChangesAreDebounced() {
        assertEquals(
            AlarmSettingsRefreshTrigger.IMMEDIATE,
            getAlarmSettingsRefreshTrigger(base, base.copy(notificationsMuted = true))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.DEBOUNCED,
            getAlarmSettingsRefreshTrigger(base, base.copy(prePrayerMinutes = 20))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.IMMEDIATE,
            getAlarmSettingsRefreshTrigger(base, base.copy(whiteDaysReminder = true))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.IMMEDIATE,
            getAlarmSettingsRefreshTrigger(base, base.copy(silentModeAutomationEnabled = true))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.DEBOUNCED,
            getAlarmSettingsRefreshTrigger(base, base.copy(silentModeMinutesAfterAdhan = 5))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.DEBOUNCED,
            getAlarmSettingsRefreshTrigger(base, base.copy(hijriOffset = 1))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.DEBOUNCED,
            getAlarmSettingsRefreshTrigger(base, base.copy(latitude = 1.0))
        )
        assertEquals(
            AlarmSettingsRefreshTrigger.NONE,
            getAlarmSettingsRefreshTrigger(base, base.copy(cityName = "Legacy city"))
        )
    }

    @Test
    fun reconciliationQueuesAtMostOneFollowUp() {
        var calls = 0
        val started = base.alarmRelevantFingerprint()
        val changed = base.copy(madhab = "HANAFI").alarmRelevantFingerprint()

        assertTrue(enqueueOneReconciliationIfChanged(started, changed) { calls++ })
        assertEquals(1, calls)
        assertEquals(false, enqueueOneReconciliationIfChanged(changed, changed) { calls++ })
        assertEquals(1, calls)
    }
}
