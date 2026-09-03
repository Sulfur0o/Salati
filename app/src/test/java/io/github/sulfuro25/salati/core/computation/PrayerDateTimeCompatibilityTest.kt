package io.github.sulfuro25.salati.core.computation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [24], manifest = Config.NONE)
class PrayerDateTimeCompatibilityTest {
    private val brusselsZone = ZoneId.of("Europe/Brussels")

    @Test
    fun julySamplePreservesEveryLegacyEpochConstant() {
        val times = SalatiPrayerTimeMapper.map(sampleDayData(), brusselsZone)

        assertEquals(LocalDate.of(2026, 7, 15), times.date)
        assertEpoch(1_784_078_580_000L, times.fajr)
        assertEpoch(1_784_087_160_000L, times.sunrise)
        assertEpoch(1_784_116_080_000L, times.dhuhr)
        assertEpoch(1_784_131_560_000L, times.asr)
        assertEpoch(1_784_145_060_000L, times.maghrib)
        assertEpoch(1_784_153_100_000L, times.isha)
        assertEpoch(1_784_159_280_000L, times.middleOfTheNight)
        assertEpoch(1_784_164_020_000L, times.lastThirdOfTheNight)
    }

    @Test
    fun normalWinterAndSummerOffsetsRemainBrusselsOffsets() {
        val winter = SalatiPrayerTimeMapper.map(sampleDayData("15-01-2026"), brusselsZone)
        val summer = SalatiPrayerTimeMapper.map(sampleDayData("15-07-2026"), brusselsZone)

        assertEquals(Instant.parse("2026-01-15T02:23:00Z"), winter.fajr)
        assertEquals(Instant.parse("2026-07-15T01:23:00Z"), summer.fajr)
    }

    @Test
    fun springGapIsRejectedAndAutumnOverlapUsesLaterOffset() {
        assertMappingFails(dayWithFajr("29-03-2026", "02:30"))

        val overlap = SalatiPrayerTimeMapper.map(dayWithFajr("25-10-2026", "02:30"), brusselsZone)
        assertEquals(Instant.parse("2026-10-25T01:30:00Z"), overlap.fajr)
        assertEpoch(1_792_891_800_000L, overlap.fajr)
    }

    @Test
    fun malformedDateAndTimeRemainRejected() {
        assertMappingFails(sampleDayData("31-02-2026"))
        assertMappingFails(dayWithFajr("15-07-2026", "25:00"))
        val sample = sampleDayData()
        val malformedDate = sample.copy(
            date = sample.date.copy(
                gregorian = sample.date.gregorian.copy(date = "15/07/2026")
            )
        )
        assertMappingFails(malformedDate)
    }

    @Test
    fun annotationsAreStrippedWithoutAcceptingAnotherTimeFormat() {
        val plain = SalatiPrayerTimeMapper.map(dayWithFajr("15-07-2026", "03:23"), brusselsZone)
        val annotated = SalatiPrayerTimeMapper.map(
            dayWithFajr("15-07-2026", "03:23 (CEST)"), brusselsZone
        )

        assertEquals(plain.fajr, annotated.fajr)
        assertMappingFails(dayWithFajr("15-07-2026", "3:23"))
    }

    @Test
    fun nightFieldsRollOverToTheFollowingCalendarDayAfterMaghrib() {
        val times = SalatiPrayerTimeMapper.map(sampleDayData(), brusselsZone)
        val zone = brusselsZone
        val nextDay = times.date.plusDays(1)

        assertEquals(nextDay, times.isha.atZone(zone).toLocalDate())
        assertEquals(nextDay, times.middleOfTheNight.atZone(zone).toLocalDate())
        assertEquals(nextDay, times.lastThirdOfTheNight.atZone(zone).toLocalDate())
        assertTrue(times.maghrib.isBefore(times.isha))
        assertTrue(times.isha.isBefore(times.middleOfTheNight))
        assertTrue(times.middleOfTheNight.isBefore(times.lastThirdOfTheNight))
    }

    @Test
    fun ishaBeforeMidnightRemainsOnTheSameCalendarDay() {
        val eveningIsha = sampleDayData().copy(timings = sampleDayData().timings.copy(Isha = "23:15"))
        val times = SalatiPrayerTimeMapper.map(eveningIsha, brusselsZone)

        assertEquals(times.date, times.isha.atZone(brusselsZone).toLocalDate())
        assertTrue(times.maghrib.isBefore(times.isha))
    }

    private fun dayWithFajr(date: String, fajr: String): AladhanDayData {
        val sample = sampleDayData(date)
        return sample.copy(timings = sample.timings.copy(Fajr = fajr))
    }

    private fun assertEpoch(expected: Long, actual: Instant) {
        assertEquals(expected, actual.toEpochMilli())
    }

    private fun assertMappingFails(dayData: AladhanDayData) {
        try {
            SalatiPrayerTimeMapper.map(dayData, brusselsZone)
            fail("Expected strict prayer timestamp parsing to fail")
        } catch (_: Exception) {
            // Expected.
        }
    }
}
