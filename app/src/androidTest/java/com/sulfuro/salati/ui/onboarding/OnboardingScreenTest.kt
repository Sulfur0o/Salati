package com.sulfuro.salati.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sulfuro.salati.R
import com.sulfuro.salati.data.settings.CalculationSettings
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The onboarding wizard, which is the first thing every new user sees and had no test of
 * any kind. It is also the only screen in the app that can leave someone stuck: prayer
 * times cannot be computed without a location, so the step that asks for one is a gate
 * rather than a suggestion.
 *
 * The steps below step 2 cannot be reached by a test, because reaching them means
 * satisfying that gate with a real GPS fix or a network city search. The individual step
 * composables are covered on their own in OnboardingStepsTest; what is checked here is the
 * wizard around them - where it starts, how it moves, what it refuses, and what it keeps.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** Records the settings onboarding hands back, so a test can assert it hands back none. */
    private class CompletionSpy {
        var completed: CalculationSettings? = null
        val onComplete: (CalculationSettings) -> Unit = { completed = it }
    }

    @Test
    fun aNewUserLandsOnTheWelcomeStep() {
        composeTestRule.setContent {
            OnboardingScreen(currentSettings = CalculationSettings(), onComplete = {})
        }

        composeTestRule.onNodeWithText(string(R.string.onboarding_welcome_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_get_started))
            .assertIsDisplayed()
    }

    @Test
    fun gettingStartedLeadsToTheLocationQuestion() {
        composeTestRule.setContent {
            OnboardingScreen(currentSettings = CalculationSettings(), onComplete = {})
        }

        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_get_started)).performClick()

        composeTestRule.onNodeWithText(string(R.string.onboarding_location_title)).assertIsDisplayed()
    }

    @Test
    fun theLocationStepCanGoBackToTheWelcome() {
        composeTestRule.setContent {
            OnboardingScreen(currentSettings = CalculationSettings(), onComplete = {})
        }

        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_get_started)).performClick()
        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_back)).performClick()

        composeTestRule.onNodeWithText(string(R.string.onboarding_welcome_title)).assertIsDisplayed()
    }

    /**
     * Every prayer time in the app is computed from coordinates, so an onboarding that let
     * someone walk past this step would hand them an app that shows nothing and never
     * explains why. Continue stays dead until a location has actually been resolved.
     */
    @Test
    fun thereIsNoWayPastTheLocationStepUntilThereIsALocation() {
        composeTestRule.setContent {
            OnboardingScreen(currentSettings = CalculationSettings(), onComplete = {})
        }

        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_get_started)).performClick()

        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_continue)).assertIsNotEnabled()
    }

    /**
     * MainActivity declares no configChanges, so a rotation - or a theme switch, or a
     * language change - destroys and recreates it. Held in plain `remember`, the step and
     * the half-finished draft went with it: someone who turned their phone while waiting
     * for a GPS fix was dropped back on the welcome screen with the fix thrown away.
     */
    @Test
    fun aRotationDoesNotThrowTheUserBackToStepOne() {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            OnboardingScreen(currentSettings = CalculationSettings(), onComplete = {})
        }

        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_get_started)).performClick()
        composeTestRule.onNodeWithText(string(R.string.onboarding_location_title)).assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText(string(R.string.onboarding_location_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.onboarding_welcome_title)).assertDoesNotExist()
    }

    /**
     * Onboarding is a draft until the last step: nothing is written while the user is still
     * walking through it, so backing out halfway leaves the stored settings alone and the
     * wizard waiting where it was.
     */
    @Test
    fun nothingIsSavedWhileTheUserIsStillWalkingThrough() {
        val spy = CompletionSpy()
        composeTestRule.setContent {
            OnboardingScreen(currentSettings = CalculationSettings(), onComplete = spy.onComplete)
        }

        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_get_started)).performClick()
        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_back)).performClick()
        composeTestRule.onNodeWithText(string(R.string.onboarding_btn_get_started)).performClick()

        assertNull(spy.completed)
    }
}
