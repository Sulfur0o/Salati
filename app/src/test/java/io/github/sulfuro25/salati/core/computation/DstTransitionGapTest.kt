package io.github.sulfuro25.salati.core.computation

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.ZoneId

class DstTransitionGapTest {

    private fun createSampleDayData(dateString: String, midnightTime: String = "00:30", lastThirdTime: String = "02:41"): AladhanDayData {
        return AladhanDayData(
            timings = AladhanTimings(
                Fajr = "05:15",
                Sunrise = "06:45",
                Dhuhr = "13:00",
                Asr = "16:30",
                Sunset = "19:15",
                Maghrib = "19:15",
                Isha = "20:45",
                Midnight = midnightTime,
                Lastthird = lastThirdTime
            ),
            date = AladhanDate(
                readable = dateString,
                timestamp = "1774742400",
                gregorian = AladhanGregorianDate(
                    date = dateString,
                    day = dateString.substring(0, 2),
                    month = AladhanMonth(3),
                    year = "2026"
                ),
                hijri = null
            ),
            meta = null
        )
    }

    @Test
    fun resolvesDstSpringForwardGapWithoutThrowing() {
        // Sample test cases from audit report where the astronomical time falls into the nonexistent DST spring-forward hour
        val testCases = listOf(
            Triple("28-03-2026", "02:41", "Europe/Brussels"),
            Triple("28-03-2026", "02:39", "Europe/Amsterdam"),
            Triple("28-03-2026", "02:50", "Europe/Paris"),
            Triple("28-03-2026", "01:59", "Europe/London"),
            Triple("07-03-2026", "02:12", "America/New_York"),
            Triple("07-03-2026", "02:06", "America/Chicago"),
            Triple("07-03-2026", "02:34", "America/Toronto")
        )

        for ((dateStr, lastThird, zoneStr) in testCases) {
            val dayData = createSampleDayData(dateStr, lastThirdTime = lastThird)
            val result = SalatiPrayerTimeMapper.map(dayData, ZoneId.of(zoneStr))
            assertNotNull("Prayer times should be successfully mapped for $zoneStr on $dateStr", result)
            assertNotNull("Last third should be mapped", result.lastThirdOfTheNight)
        }
    }
}
