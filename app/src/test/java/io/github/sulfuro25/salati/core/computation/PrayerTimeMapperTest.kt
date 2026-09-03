package io.github.sulfuro25.salati.core.computation

import io.github.sulfuro25.salati.data.settings.CalculationSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

class PrayerTimeMapperTest {
    @Test
    fun deviceDefaultTimezoneCannotAffectMappedTimestamps() {
        val original = TimeZone.getDefault()
        try {
            val settings = CalculationSettings(timezoneId = "Europe/Brussels")

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            val honoluluDevice = PrayerRepository.parsePrayerTimes(sampleDayData(), settings)

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val tokyoDevice = PrayerRepository.parsePrayerTimes(sampleDayData(), settings)

            assertEquals(honoluluDevice, tokyoDevice)
            assertEquals(Instant.parse("2026-07-15T01:23:00Z"), honoluluDevice.fajr)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun brusselsDaylightSavingTransitionsUseBrusselsOffsets() {
        val brusselsZone = ZoneId.of("Europe/Brussels")
        val spring = SalatiPrayerTimeMapper.map(sampleDayData("29-03-2026"), brusselsZone)
        val autumn = SalatiPrayerTimeMapper.map(sampleDayData("25-10-2026"), brusselsZone)

        assertEquals(Instant.parse("2026-03-29T01:23:00Z"), spring.fajr)
        assertEquals(Instant.parse("2026-10-25T02:23:00Z"), autumn.fajr)
        assertEquals(Instant.parse("2026-03-29T11:48:00Z"), spring.dhuhr)
        assertEquals(Instant.parse("2026-10-25T12:48:00Z"), autumn.dhuhr)
    }
}
