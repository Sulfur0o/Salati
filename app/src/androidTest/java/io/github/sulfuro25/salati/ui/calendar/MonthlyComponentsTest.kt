package io.github.sulfuro25.salati.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.sulfuro25.salati.core.computation.SalatiPrayerTimes
import io.github.sulfuro25.salati.theme.SalatiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset

class MonthlyComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedTodayCellPreservesBothSemanticStates() {
        composeRule.setContent {
            SalatiTheme {
                CalendarDateCell(
                    date = LocalDate.of(2026, 7, 15),
                    isSelected = true,
                    isToday = true,
                    events = emptyList(),
                    apiLookup = { null },
                    hijriOffset = 0,
                    selectedState = "Selected date",
                    todayState = "Today",
                    todaySelectedState = "Today, selected date",
                    onClick = {}
                )
            }
        }
        composeRule.onNodeWithText("15")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Today, selected date"))
    }

    @Test
    fun todayActionAndAutoMirroredNavigationAreExposed() {
        var todayClicks = 0
        composeRule.setContent {
            SalatiTheme {
                MonthNavigationHeader(
                    monthName = "June 2026",
                    showTodayAction = true,
                    onPrevious = {}, onNext = {}, onToday = { todayClicks++ }
                )
            }
        }
        composeRule.onNodeWithText("June 2026")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        composeRule.onNodeWithContentDescription("Previous month").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next month").assertIsDisplayed()
        composeRule.onNodeWithText("Today").performClick()
        assertEquals(1, todayClicks)
    }

    @Test
    fun prayerDetailsAreVerticalAndSunriseIsDisplayOnlyAtLargeFontScale() {
        val base = Instant.parse("2026-07-15T01:23:00Z")
        composeRule.setContent {
            SalatiTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                    Column(Modifier.width(320.dp)) {
                        SelectedDayPrayerDetails(
                            gregorianDate = "Wednesday, 15 July 2026",
                            hijriDate = "1 Safar 1448",
                            events = emptyList(),
                            locationContext = "Brussels · Europe/Brussels",
                            prayerTimes = times(base),
                            timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("Brussels", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Europe/Brussels", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Display only").assertIsDisplayed()
        composeRule.onNodeWithText("Sunrise")
            .assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Sunrise at 01:24, display only"
            ))
        listOf("01:23", "01:24", "01:25", "01:26", "01:27", "01:28").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    private fun times(base: Instant) = SalatiPrayerTimes(
        date = LocalDate.of(2026, 7, 15),
        fajr = base,
        sunrise = base.plusSeconds(60),
        dhuhr = base.plusSeconds(120),
        asr = base.plusSeconds(180),
        maghrib = base.plusSeconds(240),
        isha = base.plusSeconds(300),
        middleOfTheNight = base.plusSeconds(360),
        lastThirdOfTheNight = base.plusSeconds(420),
        hijri = null
    )
}
