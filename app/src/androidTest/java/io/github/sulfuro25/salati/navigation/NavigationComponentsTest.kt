package io.github.sulfuro25.salati.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sulfuro25.salati.MainNavigation
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import io.github.sulfuro25.salati.data.settings.SalatiPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class NavigationComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `navigation_bottom_bar_displays_all_text_labels_correctly`() {
        // We use dummy instances since we only want to test navigation bar presence.
        // The actual inner content will just render Dashboard initially.
        val dummySettings = CalculationSettings()

        // Setup a real preferences instance using the TEST context (not the target app context)
        // This completely isolates the DataStore file to the test APK's isolated storage
        val context = InstrumentationRegistry.getInstrumentation().context
        val realPreferences = SalatiPreferences(context)

        composeTestRule.setContent {
            MainNavigation(settings = dummySettings, preferences = realPreferences)
        }

        // Verify bottom navigation labels are always displayed
        composeTestRule.onNodeWithText("Daily").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monthly").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zakat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }
}
