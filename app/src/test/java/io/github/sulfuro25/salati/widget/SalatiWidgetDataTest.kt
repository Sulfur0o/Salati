package io.github.sulfuro25.salati.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SalatiWidgetDataTest {

    @Test
    fun cleanTimeStripsOffsetAndWhitespace() {
        assertEquals("05:13", SalatiWidgetData.cleanTime("05:13 (CET)"))
        assertEquals("13:40", SalatiWidgetData.cleanTime("13:40"))
        assertEquals("20:08", SalatiWidgetData.cleanTime(" 20:08  "))
    }

    @Test
    fun parseTimeParsesValidTimes() {
        assertEquals(LocalTime.of(5, 13), SalatiWidgetData.parseTime("05:13"))
        assertEquals(LocalTime.of(21, 58), SalatiWidgetData.parseTime("21:58 (CET)"))
    }

    @Test
    fun isUpcomingPrayerCorrectlyIdentifiesUpcomingBeforeIsha() {
        val fajr = LocalTime.of(5, 15)
        val dhuhr = LocalTime.of(13, 30)
        val maghrib = LocalTime.of(20, 15)

        // At 10:00, Fajr has passed, Dhuhr is upcoming
        assertFalse(SalatiWidgetData.isUpcomingPrayer("Fajr", fajr, LocalTime.of(10, 0), maghrib))
        assertTrue(SalatiWidgetData.isUpcomingPrayer("Dhuhr", dhuhr, LocalTime.of(10, 0), maghrib))
    }

    @Test
    fun isUpcomingPrayerHandlesPastMidnightIshaRollover() {
        // Isha rolls past midnight (e.g., Maghrib at 23:30, Isha at 00:45 next day)
        val isha = LocalTime.of(0, 45)
        val maghrib = LocalTime.of(23, 30)

        // At 23:45, it is after Maghrib, so Isha is upcoming
        assertTrue(SalatiWidgetData.isUpcomingPrayer("Isha", isha, LocalTime.of(23, 45), maghrib))

        // At 00:30, it is before Isha (rolls past midnight), so Isha is upcoming
        assertTrue(SalatiWidgetData.isUpcomingPrayer("Isha", isha, LocalTime.of(0, 30), maghrib))

        // At 01:00, Isha has passed
        assertFalse(SalatiWidgetData.isUpcomingPrayer("Isha", isha, LocalTime.of(1, 0), maghrib))
    }
}
