package io.github.sulfuro25.salati.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sulfuro25.salati.core.computation.SalatiPrayerTimes
import io.github.sulfuro25.salati.ui.calendar.calendarDateAt
import io.github.sulfuro25.salati.ui.calendar.calendarMonthHeading
import io.github.sulfuro25.salati.ui.calendar.calendarTimeFormatter
import io.github.sulfuro25.salati.ui.calendar.mondayFirstOffset
import io.github.sulfuro25.salati.ui.dashboard.addExactDashboardFallbackDay
import io.github.sulfuro25.salati.ui.dashboard.zonedDateAt
import io.github.sulfuro25.salati.ui.dashboard.dashboardDateFormatter
import io.github.sulfuro25.salati.ui.dashboard.dashboardTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
@Config(sdk = [24], manifest = Config.NONE)
class PrayerUiDateTimeTest {
    private val brusselsZone = ZoneId.of("Europe/Brussels")

    @Test
    fun brusselsDateSelectionAroundUtcMidnightIgnoresDeviceTimezone() {
        val epoch = Instant.parse("2026-07-14T22:30:00Z").toEpochMilli()
        val expected = LocalDate.of(2026, 7, 15)
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(expected, zonedDateAt(epoch, brusselsZone))
            assertEquals(expected, calendarDateAt(epoch, brusselsZone))
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(expected, zonedDateAt(epoch, brusselsZone))
            assertEquals(expected, calendarDateAt(epoch, brusselsZone))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun timeDateAndMonthHeadingsRemainUnchanged() {
        val summer = Instant.parse("2026-07-15T01:23:00Z")
        val winter = Instant.parse("2026-01-15T02:23:00Z")

        assertEquals("03:23", dashboardTimeFormatter(Locale.US, brusselsZone).format(summer))
        assertEquals("03:23", calendarTimeFormatter(Locale.US, brusselsZone).format(winter))
        assertEquals(
            "Wednesday, 15 July 2026",
            dashboardDateFormatter(Locale.US).format(LocalDate.of(2026, 7, 15))
        )
        assertEquals("July 2026", calendarMonthHeading(YearMonth.of(2026, 7), Locale.US))
    }

    @Test
    fun dashboardFallbackAddsExactlyTwentyFourElapsedHoursAcrossDst() {
        val spring = prayerTimes(LocalDate.of(2026, 3, 28), Instant.parse("2026-03-28T19:00:00Z"))
        val autumn = prayerTimes(LocalDate.of(2026, 10, 24), Instant.parse("2026-10-24T18:00:00Z"))

        for (source in listOf(spring, autumn)) {
            val fallback = addExactDashboardFallbackDay(source)
            assertEquals(Duration.ofHours(24), Duration.between(source.fajr, fallback.fajr))
            assertEquals(Duration.ofHours(24), Duration.between(source.sunrise, fallback.sunrise))
            assertEquals(Duration.ofHours(24), Duration.between(source.dhuhr, fallback.dhuhr))
            assertEquals(Duration.ofHours(24), Duration.between(source.asr, fallback.asr))
            assertEquals(Duration.ofHours(24), Duration.between(source.maghrib, fallback.maghrib))
            assertEquals(Duration.ofHours(24), Duration.between(source.isha, fallback.isha))
            assertEquals(source.date, fallback.date)
            assertEquals(source.middleOfTheNight, fallback.middleOfTheNight)
            assertEquals(source.lastThirdOfTheNight, fallback.lastThirdOfTheNight)
        }
    }

    @Test
    fun monthLengthsAndMondayFirstOffsetsRemainUnchanged() {
        assertEquals(29, YearMonth.of(2024, 2).lengthOfMonth())
        assertEquals(28, YearMonth.of(2026, 2).lengthOfMonth())
        assertEquals(3, mondayFirstOffset(YearMonth.of(2026, 1)))
        assertEquals(6, mondayFirstOffset(YearMonth.of(2026, 2)))
    }

    private fun prayerTimes(date: LocalDate, base: Instant): SalatiPrayerTimes {
        return SalatiPrayerTimes(
            date = date,
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
}
