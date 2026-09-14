package com.sulfuro.salati.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sulfuro.salati.R
import com.sulfuro.salati.data.settings.AlarmPreferences
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.LocationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The onboarding steps, one at a time.
 *
 * Step 2 is absent on purpose: it does nothing until a real GPS fix or a network city
 * search answers it, so its testable half - the Continue gate - is checked from the wizard
 * in OnboardingScreenTest instead.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingStepsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /**
     * A radio button, found by the method name printed next to it. Read from the unmerged
     * tree: the card around each row is clickable and therefore merges, which in the merged
     * tree makes every other method's card a sibling carrying this same text.
     */
    private fun besideLabel(label: String): SemanticsMatcher =
        hasAnySibling(hasText(label))

    // ----- Step 1: welcome -------------------------------------------------------------

    /**
     * The welcome step is the app's only pitch to someone who has just installed it, and the
     * four things it promises are the four things the app does. A feature card silently
     * dropped from that list is a regression nothing else here would catch.
     */
    @Test
    fun theWelcomeStepSaysWhatTheAppIsFor() {
        composeTestRule.setContent { WelcomeStep(onContinue = {}) }

        composeTestRule.onNodeWithText(string(R.string.onboarding_welcome_title)).assertIsDisplayed()
        listOf(
            R.string.onboarding_feat_prayers_title,
            R.string.onboarding_feat_qibla_title,
            R.string.onboarding_feat_calendar_title,
            R.string.onboarding_feat_zakat_title
        ).forEach { title ->
            composeTestRule.onNodeWithText(string(title)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun getStartedIsTheWayOnFromTheWelcomeStep() {
        var continued = false
        composeTestRule.setContent { WelcomeStep(onContinue = { continued = true }) }

        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_get_started))
            .performScrollTo()
            .performClick()

        assertTrue(continued)
    }

    // ----- Step 3: calculation method --------------------------------------------------

    /**
     * The method carried in from the existing settings has to arrive already chosen. A step
     * that opened on its own first entry would quietly overwrite the choice of anyone sent
     * back through onboarding.
     */
    @Test
    fun theMethodStepOpensOnTheMethodAlreadyInUse() {
        composeTestRule.setContent {
            CalculationMethodStep(currentMethod = "ISNA", onMethodSelected = {}, onBack = {})
        }

        composeTestRule.onNode(
            isSelectable() and besideLabel(string(R.string.settings_method_isna)),
            useUnmergedTree = true
        ).performScrollTo().assertIsSelected()

        composeTestRule.onNode(
            isSelectable() and besideLabel(string(R.string.settings_method_mwl)),
            useUnmergedTree = true
        ).assertIsNotSelected()
    }

    /**
     * Tapping a method moves the radio button, but the wizard is not told until Continue.
     * Someone reading down the list has not chosen anything yet, and a step that reported
     * every tap would march the wizard forward nine times on the way down.
     */
    @Test
    fun aMethodIsOnlyReportedOnceYouContinue() {
        var reported: String? = null
        composeTestRule.setContent {
            CalculationMethodStep(
                currentMethod = "MUSLIM_WORLD_LEAGUE",
                onMethodSelected = { reported = it },
                onBack = {}
            )
        }

        composeTestRule.onNode(
            isSelectable() and besideLabel(string(R.string.settings_method_isna)),
            useUnmergedTree = true
        ).performScrollTo().performClick()
        assertNull(reported)

        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_continue)).performClick()
        assertEquals("ISNA", reported)
    }

    /** Leaving without choosing reports nothing at all. */
    @Test
    fun theMethodStepCanGoBackWithoutChoosing() {
        var reported: String? = null
        var wentBack = false
        composeTestRule.setContent {
            CalculationMethodStep(
                currentMethod = "MUSLIM_WORLD_LEAGUE",
                onMethodSelected = { reported = it },
                onBack = { wentBack = true }
            )
        }

        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_back)).performClick()

        assertTrue(wentBack)
        assertNull(reported)
    }

    // ----- Step 4: notifications -------------------------------------------------------

    /** One switch on, one off, so each test can say which one it means and prove it. */
    private val notificationStart = CalculationSettings(
        location = LocationSettings(cityName = "Brussels, Belgium"),
        alarms = AlarmPreferences(vibrateEnabled = true, whiteDaysReminder = false)
    )

    @Test
    fun turningVibrationOffChangesNothingElse() {
        var current by mutableStateOf(notificationStart)
        composeTestRule.setContent {
            NotificationsStep(
                draftSettings = current,
                onSettingsChanged = { current = it },
                onFinish = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText(string(R.string.settings_reminders_vibration))
            .performScrollTo()
            .assertIsOn()
            .performClick()

        assertFalse(current.alarms.vibrateEnabled)
        assertEquals(
            notificationStart.copy(alarms = notificationStart.alarms.copy(vibrateEnabled = false)),
            current
        )
    }

    @Test
    fun theWhiteDaysReminderCanBeTurnedOnDuringOnboarding() {
        var current by mutableStateOf(notificationStart)
        composeTestRule.setContent {
            NotificationsStep(
                draftSettings = current,
                onSettingsChanged = { current = it },
                onFinish = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText(string(R.string.settings_reminders_white_days_title))
            .performScrollTo()
            .assertIsOff()
            .performClick()

        assertEquals(
            notificationStart.copy(alarms = notificationStart.alarms.copy(whiteDaysReminder = true)),
            current
        )
    }

    @Test
    fun finishIsTheLastThingTheWizardAsksFor() {
        var finished = false
        composeTestRule.setContent {
            NotificationsStep(
                draftSettings = notificationStart,
                onSettingsChanged = {},
                onFinish = { finished = true },
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_finish)).performClick()

        assertTrue(finished)
    }

    @Test
    fun theNotificationsStepCanGoBack() {
        var wentBack = false
        composeTestRule.setContent {
            NotificationsStep(
                draftSettings = notificationStart,
                onSettingsChanged = {},
                onFinish = {},
                onBack = { wentBack = true }
            )
        }

        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_back)).performClick()

        assertTrue(wentBack)
    }

    /**
     * The permissions the alarms depend on are named here rather than asked for silently,
     * because someone who declines one during onboarding is the person who later reports
     * that the adhan never sounded.
     */
    @Test
    fun theNotificationsStepNamesThePermissionsItNeeds() {
        composeTestRule.setContent {
            NotificationsStep(
                draftSettings = notificationStart,
                onSettingsChanged = {},
                onFinish = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText(string(R.string.onboarding_required_permissions))
            .performScrollTo()
            .assertIsDisplayed()
        // Each permission is announced as "<name> · <state>" in one pill, and the state
        // depends on what this particular device allows - so match the name and the
        // separator rather than the whole pill or the bare name, which also appears in the
        // step's own heading.
        composeTestRule.onNodeWithText(
            string(R.string.settings_permission_notifications_title) + " ·",
            substring = true
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            string(R.string.battery_opt_title) + " ·",
            substring = true
        ).performScrollTo().assertIsDisplayed()
    }
}
