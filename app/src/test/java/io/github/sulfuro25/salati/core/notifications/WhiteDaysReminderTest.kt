package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.sulfuro25.salati.core.computation.AladhanDate
import io.github.sulfuro25.salati.core.computation.AladhanDayData
import io.github.sulfuro25.salati.core.computation.AladhanGregorianDate
import io.github.sulfuro25.salati.core.computation.AladhanHijriDate
import io.github.sulfuro25.salati.core.computation.AladhanMonth
import io.github.sulfuro25.salati.core.computation.AladhanTimings
import io.github.sulfuro25.salati.core.computation.SalatiPrayerTimeMapper
import io.github.sulfuro25.salati.core.computation.HijriCalendarHelper
import io.github.sulfuro25.salati.core.computation.HijriDateParts
import io.github.sulfuro25.salati.core.computation.SalatiPrayerTimes
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33], instrumentedPackages = ["androidx.loader.content"])
class WhiteDaysReminderTest {

    // --- Original 7 tests ---
    @Test
    fun `hijri parsing successfully extracts valid metadata`() {
        val json = """
            {
              "readable": "01 Jan 2024",
              "timestamp": "1704067200",
              "gregorian": {
                "date": "01-01-2024",
                "day": "01",
                "month": { "number": 1 },
                "year": "2024"
              },
              "hijri": {
                "day": "19",
                "month": { "number": 6 },
                "year": "1445"
              }
            }
        """.trimIndent()
        
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<AladhanDate>(json)
        assertNotNull(parsed.hijri)
        assertEquals("19", parsed.hijri?.day)
        assertEquals(6, parsed.hijri?.month?.number)
        assertEquals("1445", parsed.hijri?.year)
    }

    @Test
    fun `hijri parsing gracefully handles missing metadata`() {
        val json = """
            {
              "readable": "01 Jan 2024",
              "timestamp": "1704067200",
              "gregorian": {
                "date": "01-01-2024",
                "day": "01",
                "month": { "number": 1 },
                "year": "2024"
              }
            }
        """.trimIndent()
        
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<AladhanDate>(json)
        assertNull(parsed.hijri)
    }

    @Test
    fun `prayer time mapper safely maps hijri metadata`() {
        val dayData = AladhanDayData(
            timings = AladhanTimings("05:00", "06:00", "12:00", "15:00", "17:00", "17:00", "18:00", "23:00", "02:00"),
            date = AladhanDate(
                readable = "01 Jan 2024",
                timestamp = "1704067200",
                gregorian = AladhanGregorianDate("01-01-2024", "01", AladhanMonth(1), "2024"),
                hijri = AladhanHijriDate("19", AladhanMonth(6), "1445")
            )
        )
        val result = SalatiPrayerTimeMapper.map(dayData, ZoneId.of("Europe/Brussels"))
        assertEquals(HijriDateParts(19, 6, 1445), result.hijri)
    }

    @Test
    fun `prayer time mapper ignores malformed hijri metadata`() {
        val dayData = AladhanDayData(
            timings = AladhanTimings("05:00", "06:00", "12:00", "15:00", "17:00", "17:00", "18:00", "23:00", "02:00"),
            date = AladhanDate(
                readable = "01 Jan 2024",
                timestamp = "1704067200",
                gregorian = AladhanGregorianDate("01-01-2024", "01", AladhanMonth(1), "2024"),
                hijri = AladhanHijriDate("abc", AladhanMonth(6), "def")
            )
        )
        val result = SalatiPrayerTimeMapper.map(dayData, ZoneId.of("Europe/Brussels"))
        assertNull(result.hijri)
    }

    @Test
    fun `scheduling suppresses white days for dhu al-hijjah`() {
        val date12th = LocalDate.of(2024, 6, 18)
        val settings = CalculationSettings(whiteDaysReminder = true, hijriOffset = 0)
        val timesByDate = mapOf(date12th to createTimes(date12th))
        val hijriMetadata = mapOf(date12th to HijriDateParts(12, 12, 1445))
        val nowMillis = Instant.parse("2024-06-18T10:00:00Z").toEpochMilli()
        val result = AlarmScheduler.buildPreparedAlarms(timesByDate, hijriMetadata, settings, nowMillis)
        val whiteDaysAlarms = (result as AlarmPreparationResult.Success).alarms.filter { it.prayerKey == "white_days" }
        assertEquals("Should suppress Dhu al-Hijjah", 0, whiteDaysAlarms.size)
    }
    
    @Test
    fun `scheduling successfully adds reminder for ordinary month`() {
        val date12th = LocalDate.of(2024, 8, 16)
        val settings = CalculationSettings(whiteDaysReminder = true, hijriOffset = 0)
        val timesByDate = mapOf(date12th to createTimes(date12th))
        val hijriMetadata = mapOf(date12th to HijriDateParts(12, 2, 1445))
        val nowMillis = Instant.parse("2024-08-16T10:00:00Z").toEpochMilli()
        val result = AlarmScheduler.buildPreparedAlarms(timesByDate, hijriMetadata, settings, nowMillis)
        val whiteDaysAlarms = (result as AlarmPreparationResult.Success).alarms.filter { it.prayerKey == "white_days" }
        assertEquals("Should add White Days reminder for Safar", 1, whiteDaysAlarms.size)
    }
    
    @Test
    fun `scheduling honors hijri offset`() {
        val date11th = LocalDate.of(2024, 8, 15)
        val date12th = LocalDate.of(2024, 8, 16)
        val settings = CalculationSettings(whiteDaysReminder = true, hijriOffset = 1)
        val timesByDate = mapOf(date11th to createTimes(date11th))
        val hijriMetadata = mapOf(date12th to HijriDateParts(12, 2, 1445)) // Target date is date11th + 1 = date12th
        val nowMillis = Instant.parse("2024-08-15T10:00:00Z").toEpochMilli()
        val result = AlarmScheduler.buildPreparedAlarms(timesByDate, hijriMetadata, settings, nowMillis)
        val whiteDaysAlarms = (result as AlarmPreparationResult.Success).alarms.filter { it.prayerKey == "white_days" }
        assertEquals("Should offset by 1", 1, whiteDaysAlarms.size)
    }

    // --- Additional Hijri Resolver Tests ---
    @Test
    fun `resolveHijriDate API mapping used instead of HijrahDate when available`() {
        val date = LocalDate.of(2024, 6, 18)
        val apiLookup: (LocalDate) -> HijriDateParts? = {
            if (it == date) HijriDateParts(25, 5, 1445) else null
        }
        val result = HijriCalendarHelper.resolveHijriDate(date, 0, false, apiLookup)
        assertEquals(25, result.day)
        assertEquals(5, result.monthNumber)
        assertEquals(1445, result.year)
    }

    @Test
    fun `resolveHijriDate positive offset looks up future date in API`() {
        val date = LocalDate.of(2024, 6, 18)
        val apiLookup: (LocalDate) -> HijriDateParts? = {
            if (it == date.plusDays(2)) HijriDateParts(27, 5, 1445) else null
        }
        val result = HijriCalendarHelper.resolveHijriDate(date, 2, false, apiLookup)
        assertEquals(27, result.day)
    }

    @Test
    fun `resolveHijriDate negative offset looks up past date in API`() {
        val date = LocalDate.of(2024, 6, 18)
        val apiLookup: (LocalDate) -> HijriDateParts? = {
            if (it == date.minusDays(1)) HijriDateParts(24, 5, 1445) else null
        }
        val result = HijriCalendarHelper.resolveHijriDate(date, -1, false, apiLookup)
        assertEquals(24, result.day)
    }

    @Test
    fun `resolveHijriDate missing target day uses fallback`() {
        val date = LocalDate.of(2024, 6, 18)
        val apiLookup: (LocalDate) -> HijriDateParts? = { null }
        val result = HijriCalendarHelper.resolveHijriDate(date, 0, false, apiLookup)
        assertTrue(result.day > 0)
    }

    // --- Additional Scheduling Tests ---
    @Test
    fun `scheduling disabled schedules none`() {
        val date12th = LocalDate.of(2024, 8, 16)
        val settings = CalculationSettings(whiteDaysReminder = false, hijriOffset = 0)
        val timesByDate = mapOf(date12th to createTimes(date12th))
        val hijriMetadata = mapOf(date12th to HijriDateParts(12, 2, 1445))
        val nowMillis = Instant.parse("2024-08-16T10:00:00Z").toEpochMilli()
        val result = AlarmScheduler.buildPreparedAlarms(timesByDate, hijriMetadata, settings, nowMillis)
        val whiteDaysAlarms = (result as AlarmPreparationResult.Success).alarms.filter { it.prayerKey == "white_days" }
        assertEquals(0, whiteDaysAlarms.size)
    }

    @Test
    fun `scheduling day 11 schedules none`() {
        val date = LocalDate.of(2024, 8, 15)
        val settings = CalculationSettings(whiteDaysReminder = true, hijriOffset = 0)
        val timesByDate = mapOf(date to createTimes(date))
        val hijriMetadata = mapOf(date to HijriDateParts(11, 2, 1445))
        val nowMillis = Instant.parse("2024-08-15T10:00:00Z").toEpochMilli()
        val result = AlarmScheduler.buildPreparedAlarms(timesByDate, hijriMetadata, settings, nowMillis)
        val whiteDaysAlarms = (result as AlarmPreparationResult.Success).alarms.filter { it.prayerKey == "white_days" }
        assertEquals(0, whiteDaysAlarms.size)
    }

    @Test
    fun `scheduling day 13 schedules none`() {
        val date = LocalDate.of(2024, 8, 17)
        val settings = CalculationSettings(whiteDaysReminder = true, hijriOffset = 0)
        val timesByDate = mapOf(date to createTimes(date))
        val hijriMetadata = mapOf(date to HijriDateParts(13, 2, 1445))
        val nowMillis = Instant.parse("2024-08-17T10:00:00Z").toEpochMilli()
        val result = AlarmScheduler.buildPreparedAlarms(timesByDate, hijriMetadata, settings, nowMillis)
        val whiteDaysAlarms = (result as AlarmPreparationResult.Success).alarms.filter { it.prayerKey == "white_days" }
        assertEquals(0, whiteDaysAlarms.size)
    }

    @Test
    fun `scheduling past Maghrib does not schedule`() {
        val date = LocalDate.of(2024, 8, 16)
        val settings = CalculationSettings(whiteDaysReminder = true, hijriOffset = 0)
        val timesByDate = mapOf(date to createTimes(date))
        val hijriMetadata = mapOf(date to HijriDateParts(12, 2, 1445))
        val nowMillis = Instant.parse("2024-08-16T21:00:00Z").toEpochMilli() // After Maghrib
        val result = AlarmScheduler.buildPreparedAlarms(timesByDate, hijriMetadata, settings, nowMillis)
        val whiteDaysAlarms = (result as AlarmPreparationResult.Success).alarms.filter { it.prayerKey == "white_days" }
        assertEquals(0, whiteDaysAlarms.size)
    }

    @Test
    fun `scheduling one reminder maximum across horizon`() {
        val dates = listOf(
            LocalDate.of(2024, 8, 16), // 12th
            LocalDate.of(2024, 8, 17)  // 13th
        )
        val settings = CalculationSettings(whiteDaysReminder = true, hijriOffset = 0)
        val timesByDate = dates.associateWith { createTimes(it) }
        val hijriMetadata = mapOf(
            dates[0] to HijriDateParts(12, 2, 1445),
            dates[1] to HijriDateParts(13, 2, 1445)
        )
        val nowMillis = Instant.parse("2024-08-16T10:00:00Z").toEpochMilli()
        val result = AlarmScheduler.buildPreparedAlarms(timesByDate, hijriMetadata, settings, nowMillis)
        val whiteDaysAlarms = (result as AlarmPreparationResult.Success).alarms.filter { it.prayerKey == "white_days" }
        assertEquals(1, whiteDaysAlarms.size)
    }

    private fun createDayData(date: LocalDate, hijriDay: Int, hijriMonth: Int): AladhanDayData {
        val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        return AladhanDayData(
            timings = AladhanTimings("05:00", "06:00", "12:00", "15:00", "17:00", "17:00", "18:00", "23:00", "02:00"),
            date = AladhanDate(
                readable = "01 Jan 2024",
                timestamp = "1704067200",
                gregorian = AladhanGregorianDate(dateStr, "01", AladhanMonth(1), "2024"),
                hijri = AladhanHijriDate(hijriDay.toString(), AladhanMonth(hijriMonth), "1445")
            )
        )
    }

    private fun createTimes(date: LocalDate): SalatiPrayerTimes {
        val base = date.atStartOfDay(ZoneId.of("Europe/Brussels")).toInstant()
        return SalatiPrayerTimes(
            date = date,
            fajr = base.plusSeconds(3600 * 5),
            sunrise = base.plusSeconds(3600 * 6),
            dhuhr = base.plusSeconds(3600 * 12),
            asr = base.plusSeconds(3600 * 16),
            maghrib = base.plusSeconds(3600 * 20),
            isha = base.plusSeconds(3600 * 22),
            middleOfTheNight = base.plusSeconds(3600 * 23),
            lastThirdOfTheNight = base.plusSeconds(3600 * 2),
            hijri = null
        )
    }
}
