package io.github.sulfuro25.salati.ui.calendar

import io.github.sulfuro25.salati.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.YearMonth

class MonthlyUiContractTest {
    @Test
    fun weekdaysRemainMondayFirstAndUseEnglishResources() {
        assertEquals(0, mondayFirstOffset(YearMonth.of(2026, 6)))
        assertEquals(
            listOf(
                R.string.monthly_weekday_monday_short,
                R.string.monthly_weekday_tuesday_short,
                R.string.monthly_weekday_wednesday_short,
                R.string.monthly_weekday_thursday_short,
                R.string.monthly_weekday_friday_short,
                R.string.monthly_weekday_saturday_short,
                R.string.monthly_weekday_sunday_short
            ),
            monthlyWeekdayLabels()
        )
    }

    @Test
    fun monthSelectionBehaviorRemainsUnchanged() {
        val today = LocalDate.of(2026, 7, 15)
        assertEquals(15, initialCalendarDay(YearMonth.of(2026, 7), today))
        assertEquals(1, initialCalendarDay(YearMonth.of(2026, 6), today))
        assertEquals(1, initialCalendarDay(YearMonth.of(2027, 1), today))
    }

    @Test
    fun selectedDateHeadingIsEnglishAndBrusselsDateIsDeviceTimezoneIndependent() {
        assertEquals(
            "Wednesday, 15 July 2026",
            calendarSelectedDateHeading(LocalDate.of(2026, 7, 15), java.util.Locale.ENGLISH)
        )
        assertEquals(
            LocalDate.of(2026, 7, 15),
            calendarDateAt(
                java.time.Instant.parse("2026-07-14T22:30:00Z").toEpochMilli(),
                java.time.ZoneId.of("Europe/Brussels")
            )
        )
    }

    @Test
    fun monthlySourceUsesCompactTwoColumnLayoutAndContainsNoFrenchOrLegacyPrayerNames() {
        val source = String(Files.readAllBytes(projectPath("src/main/java/io/github/sulfuro25/salati/ui/calendar/CalendarScreen.kt")))
        val strings = String(Files.readAllBytes(projectPath("src/main/res/values/strings.xml")))

        assertTrue(source.contains("CompactPrayerTimeItem("))
        assertFalse(source.contains("PrayerTimeRow("))
        assertFalse(source.contains("PrayerColumn"))
        assertFalse(source.contains("Chourouk"))
        assertFalse(source.contains("\"Duhr\""))
        assertFalse(source.contains("lun."))
        assertFalse(source.contains("Réessayer"))
        assertFalse(source.contains("Connectez-vous"))
        assertTrue(strings.contains(">Sunrise<"))
        assertTrue(strings.contains(">Dhuhr<"))
        assertFalse(source.contains("fontScale"))
        assertTrue(source.contains("verticalScroll"))
    }

    @Test
    fun monthlyUserCopyComesFromResources() {
        val source = String(Files.readAllBytes(projectPath("src/main/java/io/github/sulfuro25/salati/ui/calendar/CalendarScreen.kt")))
        assertFalse(Regex("Text\\(\\s*\\\"").containsMatchIn(source))
        assertFalse(Regex("contentDescription\\s*=\\s*\\\"").containsMatchIn(source))
        assertTrue(source.contains("R.string.monthly_loading"))
        assertTrue(source.contains("R.string.monthly_error_title"))
        assertTrue(source.contains("R.string.daily_location_context"))
        assertFalse(source.contains("HawlMilestoneCard"))
        assertFalse(source.contains("onHawlStartDateChanged"))
    }

    private fun projectPath(relative: String): Path {
        val direct = Path.of(relative)
        return if (Files.exists(direct)) direct else Path.of("app").resolve(relative)
    }
}
