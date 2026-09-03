package io.github.sulfuro25.salati.core.computation

import io.github.sulfuro25.salati.data.settings.CalculationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerTimesCalculatorTest {

    @Test
    fun testParsePrayerTimes() {
        val settings = CalculationSettings(
            latitude = 50.8503,
            longitude = 4.3517,
            cityName = "Brussels",
            timezoneId = "Europe/Brussels",
            calculationMethod = "MUSLIM_WORLD_LEAGUE",
            madhab = "SHAFI",
            highLatitudeRule = "MIDDLE_OF_THE_NIGHT"
        )

        // Mock day data from Aladhan API response
        val timings = AladhanTimings(
            Fajr = "03:23",
            Sunrise = "05:46",
            Dhuhr = "13:48",
            Asr = "18:06",
            Sunset = "21:51",
            Maghrib = "21:51",
            Isha = "00:05",
            Midnight = "01:48",
            Lastthird = "03:07"
        )
        
        val dateComponents = AladhanDate(
            readable = "15 Jul 2026",
            timestamp = "1784091600",
            gregorian = AladhanGregorianDate(
                date = "15-07-2026",
                day = "15",
                month = AladhanMonth(7),
                year = "2026"
            )
        )

        val dayData = AladhanDayData(timings, dateComponents)
        val parsedTimes = PrayerRepository.parsePrayerTimes(dayData, settings)

        assertNotNull(parsedTimes.fajr)
        assertNotNull(parsedTimes.sunrise)
        assertNotNull(parsedTimes.dhuhr)
        assertNotNull(parsedTimes.asr)
        assertNotNull(parsedTimes.maghrib)
        assertNotNull(parsedTimes.isha)
        assertNotNull(parsedTimes.middleOfTheNight)
        assertNotNull(parsedTimes.lastThirdOfTheNight)

        // Assert true chronological order across the whole prayer day. Isha (00:05) and the
        // night-midpoint fields occur after local midnight, so they fall on 16 July even
        // though the API reports them on the same row as 15 July's other prayers.
        assertTrue("Fajr (03:23) is before Sunrise (05:46)", parsedTimes.fajr.isBefore(parsedTimes.sunrise))
        assertTrue("Sunrise (05:46) is before Dhuhr (13:48)", parsedTimes.sunrise.isBefore(parsedTimes.dhuhr))
        assertTrue("Dhuhr (13:48) is before Asr (18:06)", parsedTimes.dhuhr.isBefore(parsedTimes.asr))
        assertTrue("Asr (18:06) is before Maghrib (21:51)", parsedTimes.asr.isBefore(parsedTimes.maghrib))
        assertTrue("Maghrib (21:51) is before Isha (next-day 00:05)", parsedTimes.maghrib.isBefore(parsedTimes.isha))
        assertTrue("Isha is before Midnight", parsedTimes.isha.isBefore(parsedTimes.middleOfTheNight))
        assertTrue("Midnight is before Last Third", parsedTimes.middleOfTheNight.isBefore(parsedTimes.lastThirdOfTheNight))

        val zone = java.time.ZoneId.of(settings.timezoneId)
        val nextDay = parsedTimes.date.plusDays(1)
        assertEquals(nextDay, parsedTimes.isha.atZone(zone).toLocalDate())
        assertEquals(nextDay, parsedTimes.middleOfTheNight.atZone(zone).toLocalDate())
        assertEquals(nextDay, parsedTimes.lastThirdOfTheNight.atZone(zone).toLocalDate())
    }
}
