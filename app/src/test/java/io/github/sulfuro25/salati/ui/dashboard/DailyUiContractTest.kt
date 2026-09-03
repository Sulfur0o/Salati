package io.github.sulfuro25.salati.ui.dashboard

import io.github.sulfuro25.salati.core.computation.SalatiPrayerTimes
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyUiContractTest {
    @Test
    fun nextEventOrderingStillIncludesSunriseBetweenFajrAndDhuhr() {
        val times = prayerTimes()
        val beforeFajr = getNextPrayer(times, tomorrowFajr(), times.fajr.minusSeconds(1))
        val beforeSunrise = getNextPrayer(times, tomorrowFajr(), times.fajr.plusSeconds(1))
        val beforeDhuhr = getNextPrayer(times, tomorrowFajr(), times.sunrise.plusSeconds(1))

        assertEquals(DailyEvent.FAJR, beforeFajr.event)
        assertEquals(times.fajr, beforeFajr.eventInstant)
        assertEquals(DailyEvent.SUNRISE, beforeSunrise.event)
        assertEquals(times.sunrise, beforeSunrise.eventInstant)
        assertEquals(DailyEvent.DHUHR, beforeDhuhr.event)
        assertEquals(times.dhuhr, beforeDhuhr.eventInstant)
    }

    @Test
    fun everyDisplayedEventKeepsItsOriginalInstant() {
        val times = prayerTimes()
        val expected = listOf(
            DailyEvent.FAJR to times.fajr,
            DailyEvent.SUNRISE to times.sunrise,
            DailyEvent.DHUHR to times.dhuhr,
            DailyEvent.ASR to times.asr,
            DailyEvent.MAGHRIB to times.maghrib,
            DailyEvent.ISHA to times.isha
        )

        expected.forEachIndexed { index, (event, instant) ->
            val now = if (index == 0) instant.minusSeconds(1) else expected[index - 1].second.plusSeconds(1)
            val result = getNextPrayer(times, tomorrowFajr(), now)
            assertEquals(event, result.event)
            assertEquals(instant, result.eventInstant)
        }
    }

    @Test
    fun dailyPresentationUsesResourcesAndHasNoNotificationImplicationOrGrid() {
        val dashboard = sourceFile(
            "src/main/java/io/github/sulfuro25/salati/ui/dashboard/DashboardScreen.kt"
        ).readText()
        val rows = sourceFile(
            "src/main/java/io/github/sulfuro25/salati/ui/components/SalatiRows.kt"
        ).readText()

        assertTrue(dashboard.contains("stringResource("))
        assertFalse(dashboard.contains("Icons.Default.Notifications"))
        assertFalse(dashboard.contains("Active Prayer"))
        assertFalse(dashboard.contains("PrayerColumn"))
        assertFalse(dashboard.contains("Text(" + '"'))
        assertTrue(rows.contains("stateDescription = semanticState"))
    }

    @Test
    fun dailyResourcesContainEnglishCopyAndNoFrenchCopy() {
        val resources = sourceFile("src/main/res/values/strings.xml").readText()
        listOf(
            "Today",
            "%1" + "$" + "s",
            "Next prayer",
            "Display only",
            "Retry"
        ).forEach { assertTrue(resources.contains(it)) }

        listOf(
            "daily_current",
            "daily_prayer_schedule",
            "daily_night_calculations"
        ).forEach { assertFalse(resources.contains("name=\"$it\"")) }

        listOf(
            "Connectez-vous",
            "RÃ©essayer",
            "priÃ¨re",
            "aujourd'hui",
            "Calculs nocturnes"
        ).forEach { assertFalse(resources.contains(it, ignoreCase = true)) }
    }

    private fun prayerTimes(): SalatiPrayerTimes {
        val base = Instant.parse("2026-07-15T01:00:00Z")
        return SalatiPrayerTimes(
            date = LocalDate.of(2026, 7, 15),
            fajr = base,
            sunrise = base.plusSeconds(3_600),
            dhuhr = base.plusSeconds(7_200),
            asr = base.plusSeconds(10_800),
            maghrib = base.plusSeconds(14_400),
            isha = base.plusSeconds(18_000),
            middleOfTheNight = base.plusSeconds(21_600),
            lastThirdOfTheNight = base.plusSeconds(25_200),
            hijri = null
        )
    }

    private fun tomorrowFajr() = Instant.parse("2026-07-16T01:00:00Z")

    private fun sourceFile(pathWithinApp: String): File {
        return listOf(File("app", pathWithinApp), File(pathWithinApp))
            .firstOrNull(File::isFile)
            ?: error("Missing source file: $pathWithinApp")
    }
}
