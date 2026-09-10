package io.github.sulfuro25.salati.core.computation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/**
 * The on-device fallback exists so that being offline does not mean having no prayer
 * times and no alarms. It is only worth having if it agrees with the network answer, so
 * the golden values below are real Aladhan responses, captured across a spread of
 * latitudes, methods and seasons, and the calculator is held to landing on the same
 * minute.
 */
class LocalPrayerTimeCalculatorTest {

    private data class Expected(
        val fajr: String,
        val sunrise: String,
        val dhuhr: String,
        val asr: String,
        val maghrib: String,
        val isha: String
    )

    private fun timesFor(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: String,
        methodId: Int,
        schoolId: Int = 0,
        highLatitude: String = "3"
    ): AladhanTimings {
        val request = PrayerMonthRequest(
            year = date.year,
            month = date.monthValue,
            methodId = methodId,
            schoolId = schoolId,
            latitudeAdjustmentId = highLatitude,
            latitude = latitude,
            longitude = longitude
        )
        val month = LocalPrayerTimeCalculator.calculateMonth(request, ZoneId.of(zone))
        val day = month.single { it.date.gregorian.day.toInt() == date.dayOfMonth }
        return day.timings
    }

    private fun assertMatches(actual: AladhanTimings, expected: Expected, tolerance: Int = 1) {
        val pairs = listOf(
            "Fajr" to (expected.fajr to actual.Fajr),
            "Sunrise" to (expected.sunrise to actual.Sunrise),
            "Dhuhr" to (expected.dhuhr to actual.Dhuhr),
            "Asr" to (expected.asr to actual.Asr),
            "Maghrib" to (expected.maghrib to actual.Maghrib),
            "Isha" to (expected.isha to actual.Isha)
        )
        for ((label, pair) in pairs) {
            val delta = minutesApart(pair.first, pair.second)
            assertTrue(
                "$label: Aladhan says ${pair.first}, computed ${pair.second} ($delta min apart)",
                delta <= tolerance
            )
        }
    }

    private fun minutesApart(a: String, b: String): Int {
        fun minutes(value: String): Int {
            val (hour, minute) = value.split(":").map(String::toInt)
            return hour * 60 + minute
        }
        val raw = abs(minutes(a) - minutes(b))
        // Times either side of midnight are minutes apart, not most of a day.
        return minOf(raw, 24 * 60 - raw)
    }

    @Test
    fun matchesAladhanForMuslimWorldLeagueInWinter() {
        assertMatches(
            timesFor(LocalDate.of(2026, 1, 15), 50.8503, 4.3517, "Europe/Brussels", methodId = 3),
            Expected("06:40", "08:39", "12:52", "14:45", "17:06", "18:58")
        )
    }

    @Test
    fun matchesAladhanWhereTheSunBarelySetsInMidsummer() {
        // Brussels in June is the case the high-latitude rule exists for: Isha lands
        // after midnight and Fajr is clamped to a portion of a very short night.
        assertMatches(
            timesFor(LocalDate.of(2026, 6, 15), 50.8503, 4.3517, "Europe/Brussels", methodId = 3),
            Expected("03:13", "05:28", "13:43", "18:04", "21:58", "00:06")
        )
    }

    @Test
    fun matchesAladhanForTheFixedIntervalIshaOfUmmAlQura() {
        // Umm al-Qura calls Isha ninety minutes after Maghrib rather than at an angle.
        val timings = timesFor(LocalDate.of(2026, 1, 15), 21.3891, 39.8579, "Asia/Riyadh", methodId = 4)
        assertMatches(
            timings,
            Expected("05:40", "07:01", "12:30", "15:37", "17:59", "19:29")
        )
        assertEquals(90, minutesApart(timings.Maghrib, timings.Isha))
    }

    @Test
    fun matchesAladhanForTheHanafiAsrShadow() {
        assertMatches(
            timesFor(
                LocalDate.of(2026, 1, 15), 40.7128, -74.0060, "America/New_York",
                methodId = 2, schoolId = 1
            ),
            Expected("05:58", "07:18", "12:06", "15:12", "16:53", "18:14")
        )
    }

    @Test
    fun hanafiAsrAlwaysFallsAfterShafi() {
        val date = LocalDate.of(2026, 4, 12)
        val shafi = timesFor(date, 33.5731, -7.5898, "Africa/Casablanca", methodId = 3, schoolId = 0)
        val hanafi = timesFor(date, 33.5731, -7.5898, "Africa/Casablanca", methodId = 3, schoolId = 1)

        assertTrue(
            "Hanafi Asr (${hanafi.Asr}) should follow Shafi (${shafi.Asr})",
            hanafi.Asr > shafi.Asr
        )
        // Everything else is shared, so nothing but Asr may move.
        assertEquals(shafi.Fajr, hanafi.Fajr)
        assertEquals(shafi.Maghrib, hanafi.Maghrib)
    }

    @Test
    fun appliesTheThreeMinuteDubaiConventionToDhuhrAndMaghrib() {
        // Dubai is the one offered method that shifts prayers off the astronomy by fiat.
        assertMatches(
            timesFor(LocalDate.of(2026, 6, 15), 25.2048, 55.2708, "Asia/Dubai", methodId = 16),
            Expected("03:58", "05:28", "12:22", "15:42", "19:13", "20:41")
        )
    }

    @Test
    fun matchesAladhanNearTheEquatorAndForEgypt() {
        assertMatches(
            timesFor(LocalDate.of(2026, 9, 10), -6.2088, 106.8456, "Asia/Jakarta", methodId = 11),
            Expected("04:31", "05:49", "11:50", "15:05", "17:51", "19:00")
        )
        assertMatches(
            timesFor(LocalDate.of(2026, 9, 15), 30.0444, 31.2357, "Africa/Cairo", methodId = 5),
            Expected("05:12", "06:39", "12:50", "16:21", "19:01", "20:19")
        )
    }

    @Test
    fun coversEveryDayOfTheRequestedMonthIncludingLeapDay() {
        val request = PrayerMonthRequest(2028, 2, 3, 0, "3", 50.8503, 4.3517)
        val month = LocalPrayerTimeCalculator.calculateMonth(request, ZoneId.of("Europe/Brussels"))

        assertEquals(29, month.size)
        assertEquals("29-02-2028", month.last().date.gregorian.date)
        assertNotNull(month.first().date.hijri)
        assertEquals("Europe/Brussels", month.first().meta?.timezone)
    }

    @Test
    fun everyDayParsesBackIntoAnOrderedSetOfInstants() {
        // The output is only useful if the shared mapper accepts it, so run a whole
        // month through the real one rather than trusting the strings.
        val zone = ZoneId.of("Europe/Brussels")
        val request = PrayerMonthRequest(2026, 6, 3, 0, "3", 50.8503, 4.3517)
        val month = LocalPrayerTimeCalculator.calculateMonth(request, zone)

        assertEquals(30, month.size)
        for (day in month) {
            val times = SalatiPrayerTimeMapper.map(day, zone)
            val label = day.date.gregorian.date
            assertTrue("$label fajr before sunrise", times.fajr.isBefore(times.sunrise))
            assertTrue("$label sunrise before dhuhr", times.sunrise.isBefore(times.dhuhr))
            assertTrue("$label dhuhr before asr", times.dhuhr.isBefore(times.asr))
            assertTrue("$label asr before maghrib", times.asr.isBefore(times.maghrib))
            assertTrue("$label maghrib before isha", times.maghrib.isBefore(times.isha))
            assertTrue("$label isha before last third", times.isha.isBefore(times.lastThirdOfTheNight))
        }
        assertEquals(zone.id, month.first().meta?.timezone)
    }

    @Test
    fun theHighLatitudeRuleChangesFajrAndIshaButNotTheSun() {
        val date = LocalDate.of(2026, 6, 15)
        val byAngle = timesFor(date, 59.9139, 10.7522, "Europe/Oslo", methodId = 3, highLatitude = "3")
        val bySeventh = timesFor(date, 59.9139, 10.7522, "Europe/Oslo", methodId = 3, highLatitude = "2")
        val byMidnight = timesFor(date, 59.9139, 10.7522, "Europe/Oslo", methodId = 3, highLatitude = "1")

        // Sunrise and sunset are astronomy; no rule may move them.
        assertEquals(byAngle.Sunrise, bySeventh.Sunrise)
        assertEquals(byAngle.Sunrise, byMidnight.Sunrise)
        assertEquals(byAngle.Maghrib, byMidnight.Maghrib)
        // The rules genuinely differ, otherwise the setting would be decorative.
        assertTrue(setOf(byAngle.Fajr, bySeventh.Fajr, byMidnight.Fajr).size > 1)
    }

    @Test
    fun omitsDaysWhereTheSunNeverRisesInsteadOfInventingThem() {
        // Longyearbyen in December: polar night, so there is no sunrise to anchor to.
        val request = PrayerMonthRequest(2026, 12, 3, 0, "3", 78.2232, 15.6267)
        val month = LocalPrayerTimeCalculator.calculateMonth(request, ZoneId.of("Arctic/Longyearbyen"))

        assertTrue("polar night should yield no fabricated days", month.isEmpty())
    }

    @Test
    fun rejectsAnImpossibleMonthRatherThanThrowing() {
        val request = PrayerMonthRequest(2026, 13, 3, 0, "3", 50.8503, 4.3517)
        assertTrue(LocalPrayerTimeCalculator.calculateMonth(request, ZoneId.of("UTC")).isEmpty())
    }

    @Test
    fun daysSinceSolsticeCountsFromEachHemispheresWinter() {
        // Northern hemisphere: day zero is 21 December, ten days before the new year.
        assertEquals(0, LocalPrayerTimeCalculator.daysSinceSolstice(LocalDate.of(2026, 12, 21), 50.0))
        assertEquals(1, LocalPrayerTimeCalculator.daysSinceSolstice(LocalDate.of(2026, 12, 22), 50.0))
        assertEquals(11, LocalPrayerTimeCalculator.daysSinceSolstice(LocalDate.of(2026, 1, 1), 50.0))
        // Southern hemisphere: counted from the June solstice instead.
        assertEquals(0, LocalPrayerTimeCalculator.daysSinceSolstice(LocalDate.of(2026, 6, 21), -33.0))
        assertEquals(
            LocalPrayerTimeCalculator.daysSinceSolstice(LocalDate.of(2026, 6, 22), -33.0),
            1
        )
    }

    @Test
    fun roundsToTheNearestMinuteAndWrapsPastMidnight() {
        assertEquals("00:00", LocalPrayerTimeCalculator.formatHour(0.0))
        assertEquals("12:30", LocalPrayerTimeCalculator.formatHour(12.5))
        assertEquals("12:31", LocalPrayerTimeCalculator.formatHour(12.51))
        // 23:59:45 rounds up to the next day's midnight rather than to an invalid 24:00.
        assertEquals("00:00", LocalPrayerTimeCalculator.formatHour(23.99583))
        assertEquals("23:00", LocalPrayerTimeCalculator.formatHour(-1.0))
    }

    @Test
    fun usesTheZoneOffsetInEffectOnEachSideOfADstTransition() {
        // Europe/Brussels springs forward on 29 March 2026.
        val request = PrayerMonthRequest(2026, 3, 3, 0, "3", 50.8503, 4.3517)
        val month = LocalPrayerTimeCalculator.calculateMonth(request, ZoneId.of("Europe/Brussels"))
            .associateBy { it.date.gregorian.date }

        val beforeDhuhr = month.getValue("28-03-2026").timings.Dhuhr
        val afterDhuhr = month.getValue("30-03-2026").timings.Dhuhr

        // Solar noon barely moves in two days, so the hour jump is the clock change.
        val jump = minutesApart(beforeDhuhr, afterDhuhr)
        assertTrue("expected roughly an hour jump, got $jump min", jump in 55..65)
    }

    @Test
    fun aFullYearOfDaysStaysChronologicallySane() {
        // A cheap sweep for wrap-around bugs that only show up in particular weeks.
        val zone = ZoneId.of("Europe/Brussels")
        for (month in 1..12) {
            val request = PrayerMonthRequest(2026, month, 3, 0, "3", 50.8503, 4.3517)
            val days = LocalPrayerTimeCalculator.calculateMonth(request, zone)
            assertEquals(
                "month $month should be complete",
                YearMonth.of(2026, month).lengthOfMonth(),
                days.size
            )
            for (day in days) {
                val times = SalatiPrayerTimeMapper.map(day, zone)
                assertTrue(
                    "${day.date.gregorian.date} out of order",
                    times.fajr.isBefore(times.dhuhr) && times.dhuhr.isBefore(times.maghrib)
                )
            }
        }
    }
}
