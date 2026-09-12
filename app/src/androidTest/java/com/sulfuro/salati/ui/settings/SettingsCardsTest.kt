package com.sulfuro.salati.ui.settings

import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
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

    /**
     * Mute, Sound and Vibration used to be three switches with eight combinations between
     * them. They are one named choice now, so the test picks a named state and checks that
     * the three stored flags land where that name says - and that nothing outside the
     * alarm settings moves.
     */
    @Test
    fun choosingNoNotificationsSilencesEverythingAndTouchesNothingElse() {
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
            SettingsAlarmsCard(settings = start, saveSettings = saves.save)
        }

        composeTestRule.onNodeWithText(string(R.string.settings_alerts_label)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_alerts_none)).performClick()

        val updated = saves.applyTo(start)
        assertTrue("the alert style must mute", updated.alarms.notificationsMuted)
        assertEquals(
            start.copy(
                alarms = start.alarms.copy(
                    notificationsMuted = true,
                    soundEnabled = false,
                    vibrateEnabled = false
                )
            ),
            updated
        )
    }

    /** Sound without vibration is one of the five states, and it has to be reachable. */
    @Test
    fun soundOnlyKeepsTheToneAndDropsTheBuzz() {
        val saves = RecordedSaves()
        val start = CalculationSettings(alarms = AlarmPreferences(vibrateEnabled = true, soundEnabled = true))

        composeTestRule.setContent {
            SettingsAlarmsCard(settings = start, saveSettings = saves.save)
        }

        composeTestRule.onNodeWithText(string(R.string.settings_alerts_label)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_alerts_sound_only)).performClick()

        val updated = saves.applyTo(start)
        assertFalse(updated.alarms.vibrateEnabled)
        assertTrue("sound is what this option keeps", updated.alarms.soundEnabled)
        assertFalse("and it is still an alert", updated.alarms.notificationsMuted)
    }

    /**
     * The clock format is a three-way choice, and "System" is not the same as 12-hour: it
     * defers to the device, which is what a user who changes that setting later expects.
     *
     * It is a row and a sheet now rather than a segmented strip, so each pick is two taps:
     * open the row, choose. The sheet closes on select, which is why the row is reopened
     * before the second choice.
     */
    @Test
    fun theClockFormatOptionsPickTheirOwnValues() {
        val saves = RecordedSaves()
        val start = CalculationSettings(appearance = AppearanceSettings(timeFormat = TimeFormatPreference.SYSTEM))

        composeTestRule.setContent {
            SettingsAppearanceCard(settings = start, saveSettings = saves.save)
        }

        composeTestRule.onNodeWithText(string(R.string.settings_time_format_title)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_time_format_24h)).performClick()
        assertEquals(TimeFormatPreference.TWENTY_FOUR_HOUR, saves.applyTo(start).appearance.timeFormat)

        composeTestRule.onNodeWithText(string(R.string.settings_time_format_title)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_time_format_12h)).performClick()
        assertEquals(TimeFormatPreference.TWELVE_HOUR, saves.applyTo(start).appearance.timeFormat)
    }

    /**
     * Theme has three states and the middle one is null, not false: "follow the system"
     * has to survive as an absence of choice rather than being flattened into "light".
     *
     * The card starts on Dark, so the row reads "Dark" throughout and "Light" belongs to
     * the sheet alone. The clock starts on 24-hour for the same reason: left on its
     * default it shows "System" as its own value, which is the word this test clicks.
     */
    @Test
    fun theThemeOptionsDistinguishSystemFromLight() {
        val saves = RecordedSaves()
        val start = CalculationSettings(
            appearance = AppearanceSettings(
                isDarkMode = true,
                timeFormat = TimeFormatPreference.TWENTY_FOUR_HOUR
            )
        )

        composeTestRule.setContent {
            SettingsAppearanceCard(settings = start, saveSettings = saves.save)
        }

        composeTestRule.onNodeWithText(string(R.string.settings_theme_label)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_theme_option_light)).performClick()
        assertEquals(false, saves.applyTo(start).appearance.isDarkMode)

        composeTestRule.onNodeWithText(string(R.string.settings_theme_label)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_theme_option_system)).performClick()
        assertEquals(null, saves.applyTo(start).appearance.isDarkMode)
    }
}
