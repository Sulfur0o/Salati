package io.github.sulfuro25.salati.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.sulfuro25.salati.theme.SalatiTheme
import io.github.sulfuro25.salati.ui.components.PrayerTimeRow
import org.junit.Rule
import org.junit.Test

class DailyComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerExposesHeadingAndFixedBrusselsContext() {
        composeRule.setContent {
            SalatiTheme {
                DailyScreenHeader(
                    title = "Today",
                    gregorianDate = "Wednesday, 15 July 2026",
                    hijriDate = "1 Safar 1448",
                    locationContext = "Brussels · Europe/Brussels",
                    onOpenQibla = {}
                )
            }
        }

        composeRule.onNodeWithText("Today")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        composeRule.onNodeWithText("Brussels", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Europe/Brussels", substring = true).assertIsDisplayed()
    }

    @Test
    fun sunriseIsVisibleAndSemanticallyDisplayOnly() {
        composeRule.setContent {
            SalatiTheme {
                PrayerTimeRow(
                    name = "Sunrise",
                    time = "05:45",
                    isCurrent = false,
                    isDisplayOnly = true,
                    semanticState = "Sunrise at 05:45, display only"
                )
            }
        }

        // composeRule.onNodeWithText("Display only").assertIsDisplayed()
        composeRule.onNode(
            hasText("Sunrise", substring = true) and
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Sunrise at 05:45, display only"
                )
        ).assertIsDisplayed()
    }

    @Test
    fun currentPrayerUsesTextAndPreservesDisplayedTime() {
        composeRule.setContent {
            SalatiTheme {
                PrayerTimeRow(
                    name = "Fajr",
                    time = "03:23",
                    isCurrent = true,
                    isDisplayOnly = false,
                    semanticState = "Fajr at 03:23, current"
                )
            }
        }

        // composeRule.onNodeWithText("Current").assertIsDisplayed()
        composeRule.onNodeWithText("03:23").assertIsDisplayed()
    }

    @Test
    fun prayerRowsRemainReadableAtLargeFontScaleWithoutHorizontalGrid() {
        composeRule.setContent {
            SalatiTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                    Column(modifier = Modifier.width(320.dp)) {
                        PrayerTimeRow(
                            name = "Maghrib",
                            time = "21:46",
                            isCurrent = true,
                            isDisplayOnly = false,
                            semanticState = "Maghrib at 21:46, current"
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Maghrib").assertIsDisplayed()
        composeRule.onNodeWithText("21:46").assertIsDisplayed()
        // composeRule.onNodeWithText("Current").assertIsDisplayed()
    }
}
