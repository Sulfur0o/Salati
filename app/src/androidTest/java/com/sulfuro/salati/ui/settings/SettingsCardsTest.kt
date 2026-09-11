package com.sulfuro.salati.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sulfuro.salati.R
import com.sulfuro.salati.core.permissions.AppPermissionState
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.TimeFormatPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.sulfuro.salati.data.settings.AlarmPreferences
import com.sulfuro.salati.data.settings.AppearanceSettings
import com.sulfuro.salati.data.settings.LocationSettings

/**
 * The settings cards, which were split out of one 1093-line screen and had no coverage of
 * their own afterwards.
 *
 * Each card is handed the settings and a `saveSettings` that takes a transform, so a test
 * can render one, tap a control, and apply the transform it produced to a known starting
 * point. That checks the thing worth checking: a control changes its own field and leaves
 * every other setting alone.
 */
@RunWith(AndroidJUnit4::class)
class SettingsCardsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val allPermitted = AppPermissionState(
        exactAlarmAccess = true,
        notificationPermission = true,
        batteryOptimizationIgnored = true,
        notificationPolicyAccess = true
    )

    /** Captures the transforms a card produces without persisting anything. */
    private class RecordedSaves {
        val transforms = mutableListOf<(CalculationSettings) -> CalculationSettings>()
        val save: ((CalculationSettings) -> CalculationSettings) -> Unit = { transforms += it }

        fun applyTo(start: CalculationSettings): CalculationSettings =
            transforms.fold(start) { current, transform -> transform(current) }
    }

    @Test
    fun mutingRemindersChangesOnlyThatSetting() {
        val saves = RecordedSaves()
        val start = CalculationSettings(
            location = LocationSettings(cityName = "Dubai, United Arab Emirates"),
            alarms = AlarmPreferences(
                notificationsMuted = false,
                vibrateEnabled = true,
                soundEnabled = true
            )
        )

        composeTestRule.setContent {
            SettingsAlarmsCard(
                settings = start,
                permissionState = allPermitted,
                saveSettings = saves.save
            )
        }

        composeTestRule.onNodeWithText(string(R.string.settings_reminders_mute)).performClick()

        val updated = saves.applyTo(start)
        assertTrue("the mute toggle must mute", updated.alarms.notificationsMuted)
        assertEquals(start.copy(alarms = start.alarms.copy(notificationsMuted = true)), updated)
    }

    /** Vibration and sound are separate switches and must stay separate. */
    @Test
    fun turningOffVibrationLeavesTheNotificationToneAlone() {
        val saves = RecordedSaves()
        val start = CalculationSettings(alarms = AlarmPreferences(vibrateEnabled = true, soundEnabled = true))

        composeTestRule.setContent {
            SettingsAlarmsCard(
                settings = start,
                permissionState = allPermitted,
                saveSettings = saves.save
            )
        }

        composeTestRule.onNodeWithText(string(R.string.settings_reminders_vibration)).performClick()

        val updated = saves.applyTo(start)
        assertFalse(updated.alarms.vibrateEnabled)
        assertTrue("sound is a different switch", updated.alarms.soundEnabled)
    }

    /**
     * The clock format is a three-way choice, and "System" is not the same as 12-hour: it
     * defers to the device, which is what a user who changes that setting later expects.
     */
    @Test
    fun theClockFormatSegmentsPickTheirOwnValues() {
        val saves = RecordedSaves()
        val start = CalculationSettings(appearance = AppearanceSettings(timeFormat = TimeFormatPreference.SYSTEM))

        composeTestRule.setContent {
            SettingsAppearanceCard(settings = start, saveSettings = saves.save)
        }

        composeTestRule.onNodeWithText(string(R.string.settings_time_format_24h)).performClick()
        assertEquals(TimeFormatPreference.TWENTY_FOUR_HOUR, saves.applyTo(start).appearance.timeFormat)

        composeTestRule.onNodeWithText(string(R.string.settings_time_format_12h)).performClick()
        assertEquals(TimeFormatPreference.TWELVE_HOUR, saves.applyTo(start).appearance.timeFormat)
    }

    /**
     * Theme has three states and the middle one is null, not false: "follow the system"
     * has to survive as an absence of choice rather than being flattened into "light".
     */
    @Test
    fun theThemeSegmentsDistinguishSystemFromLight() {
        val saves = RecordedSaves()
        val start = CalculationSettings(appearance = AppearanceSettings(isDarkMode = true))

        composeTestRule.setContent {
            SettingsAppearanceCard(settings = start, saveSettings = saves.save)
        }

        composeTestRule.onNodeWithText(string(R.string.settings_theme_option_light)).performClick()
        assertEquals(false, saves.applyTo(start).appearance.isDarkMode)

        composeTestRule.onNodeWithText(string(R.string.settings_theme_option_system)).performClick()
        assertEquals(null, saves.applyTo(start).appearance.isDarkMode)
    }
}
