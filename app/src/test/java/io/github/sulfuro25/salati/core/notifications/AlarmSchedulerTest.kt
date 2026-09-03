package io.github.sulfuro25.salati.core.notifications

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sulfuro25.salati.core.computation.SalatiPrayerTimes
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [24], manifest = Config.NONE)
class AlarmSchedulerTest {
    private val brussels = ZoneId.of("Europe/Brussels")

    @Test
    fun supportedNotificationPrayerIdsExcludeSunrise() {
        assertEquals(1, AlarmScheduler.getPrayerBaseId("Fajr"))
        assertEquals(2, AlarmScheduler.getPrayerBaseId("Dhuhr"))
        assertEquals(3, AlarmScheduler.getPrayerBaseId("Asr"))
        assertEquals(4, AlarmScheduler.getPrayerBaseId("Maghrib"))
        assertEquals(5, AlarmScheduler.getPrayerBaseId("Isha"))
        assertFalse(AlarmScheduler.supportedNotificationPrayers.any { it.first == "Sunrise" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun sunriseCannotProduceNotificationPrayerId() {
        AlarmScheduler.getPrayerBaseId("Sunrise")
    }

    @Test
    fun schedulingWindowIncludesYesterdayThroughTodayPlusSix() {
        val clock = clockAt(LocalDateTime.of(2026, 7, 15, 23, 30))

        assertEquals(
            (-1L..6L).map { LocalDate.of(2026, 7, 15).plusDays(it) },
            AlarmScheduler.getSchedulingDates(clock, brussels)
        )
    }

    @Test
    fun schedulingDatesUseThePassedZoneIdNotTheClockOrDeviceZone() {
        // 23:30 Brussels on 15 July is already 00:30 in Tokyo (UTC+9) on 16 July, and the
        // clock's own embedded zone is Brussels. If the device/clock zone leaked into date
        // selection instead of the explicit zoneId argument, this would return 15 July.
        val instant = LocalDateTime.of(2026, 7, 15, 23, 30).atZone(brussels).toInstant()
        val clock = Clock.fixed(instant, brussels)
        val tokyo = ZoneId.of("Asia/Tokyo")

        val brusselsDates = AlarmScheduler.getSchedulingDates(clock, brussels)
        val tokyoDates = AlarmScheduler.getSchedulingDates(clock, tokyo)

        assertEquals(LocalDate.of(2026, 7, 14), brusselsDates.first())
        assertEquals(LocalDate.of(2026, 7, 15), tokyoDates.first())
        assertEquals(LocalDate.of(2026, 7, 15), brusselsDates[1])
        assertEquals(LocalDate.of(2026, 7, 16), tokyoDates[1])
    }

    @Test
    fun monthBoundaryRequiresBothMonths() {
        val dates = AlarmScheduler.getSchedulingDates(
            clockAt(LocalDateTime.of(2026, 1, 29, 12, 0)),
            brussels
        )

        assertEquals(
            listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2)),
            AlarmScheduler.getRequiredMonths(dates)
        )
        assertEquals(LocalDate.of(2026, 2, 4), dates.last())
    }

    @Test
    fun yearBoundaryUsesBrusselsCalendarDates() {
        val dates = AlarmScheduler.getSchedulingDates(
            clockAt(LocalDateTime.of(2026, 12, 29, 12, 0)),
            brussels
        )

        assertEquals(
            listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 1)),
            AlarmScheduler.getRequiredMonths(dates)
        )
        assertEquals(LocalDate.of(2027, 1, 4), dates.last())
    }

    @Test
    fun sevenFullDatesProduceMaximumThirtyFiveMainAndSeventyTotalAlarms() {
        val today = LocalDate.of(2026, 7, 15)
        val times = (0L..6L).associate { dateOffset ->
            val date = today.plusDays(dateOffset)
            date to prayerTimes(date)
        }

        val result = AlarmScheduler.buildPreparedAlarms(
            timesByDate = times,
            hijriMetadata = emptyMap(),
            settings = CalculationSettings(prePrayerMinutes = 10),
            nowMillis = at(today, 0, 0).toEpochMilli()
        )

        assertEquals(35, result.alarms.count { !it.isPreReminder })
        assertEquals(35, result.alarms.count { it.isPreReminder })
        assertEquals(70, result.alarms.size)
        assertFalse(result.alarms.any { it.prayerKey == "sunrise" })
    }

    @Test
    fun silentModeConfigurationIsAttachedOnlyToMainPrayerAlarms() {
        val date = LocalDate.of(2026, 7, 15)
        val result = AlarmScheduler.buildPreparedAlarms(
            timesByDate = mapOf(date to prayerTimes(date)),
            hijriMetadata = emptyMap(),
            settings = CalculationSettings(
                prePrayerMinutes = 10,
                silentModeAutomationEnabled = true,
                silentModeMinutesAfterAdhan = 5,
                silentModeDurationMinutes = 30
            ),
            nowMillis = at(date, 0, 0).toEpochMilli()
        )

        val mainAlarm = result.alarms.first { !it.isPreReminder }
        val preReminder = result.alarms.first { it.isPreReminder }
        assertTrue(mainAlarm.silentModeAutomationEnabled)
        assertEquals(5, mainAlarm.silentModeMinutesAfterAdhan)
        assertEquals(30, mainAlarm.silentModeDurationMinutes)
        assertFalse(preReminder.silentModeAutomationEnabled)
    }

    @Test
    fun passedPrayersTodayAreExcludedWhileAllFutureDatesRemain() {
        val today = LocalDate.of(2026, 7, 15)
        val times = (0L..6L).associate { dateOffset ->
            val date = today.plusDays(dateOffset)
            date to prayerTimes(date)
        }

        val result = AlarmScheduler.buildPreparedAlarms(
            timesByDate = times,
            hijriMetadata = emptyMap(),
            settings = CalculationSettings(prePrayerMinutes = 10),
            nowMillis = at(today, 13, 0).toEpochMilli()
        )

        assertFalse(result.alarms.any { it.uri.contains("$today/fajr/") })
        assertFalse(result.alarms.any { it.uri.contains("$today/dhuhr/") })
        assertEquals(33, result.alarms.count { !it.isPreReminder })
        assertEquals(33, result.alarms.count { it.isPreReminder })
        assertFalse(result.alarms.any { it.prayerKey == "sunrise" })
    }

    @Test
    fun alarmUriDistinguishesMainAndPreReminderUsingBrusselsDate() {
        val timeMs = LocalDateTime.of(2026, 7, 15, 0, 30)
            .atZone(brussels)
            .toInstant()
            .toEpochMilli()

        assertEquals(
            "salati://alarm/2026-07-15/fajr/main",
            AlarmScheduler.getAlarmUriString(timeMs, "Fajr", false, brussels)
        )
        assertEquals(
            "salati://alarm/2026-07-15/fajr/pre",
            AlarmScheduler.getAlarmUriString(timeMs, "Fajr", true, brussels)
        )
    }

    @Test
    fun requestCodesAreUniqueForEverySupportedIdentityAcrossDateBoundaries() {
        val dates = listOf(
            LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 7, 16),
            LocalDate.of(2026, 7, 31),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2027, 1, 1)
        )
        val requestCodes = mutableSetOf<Int>()
        val uris = mutableSetOf<String>()

        for (date in dates) {
            for ((prayerName, prayerId) in AlarmScheduler.supportedNotificationPrayers) {
                for (preReminder in listOf(false, true)) {
                    assertTrue(
                        requestCodes.add(
                            AlarmScheduler.createAlarmRequestCode(date, prayerId, preReminder)
                        )
                    )
                    assertTrue(
                        uris.add(AlarmScheduler.getAlarmUriString(date, prayerName, preReminder))
                    )
                }
            }
        }
    }

    @Test
    fun consecutiveDaysAndReminderTypesNeverCollide() {
        val today = LocalDate.of(2026, 7, 15)
        val tomorrow = today.plusDays(1)
        val todayMain = AlarmScheduler.createAlarmRequestCode(today, 1, false)
        val todayPre = AlarmScheduler.createAlarmRequestCode(today, 1, true)
        val tomorrowMain = AlarmScheduler.createAlarmRequestCode(tomorrow, 1, false)

        assertNotEquals(todayMain, todayPre)
        assertNotEquals(todayMain, tomorrowMain)
    }

    @Test(expected = IllegalArgumentException::class)
    fun removedSixthPrayerIdIsRejected() {
        AlarmScheduler.createAlarmRequestCode(LocalDate.of(2026, 7, 15), 6, false)
    }

    @Test
    fun javaTimeAlarmIdentityRunsUnderRobolectricApi24() {
        val date = LocalDate.of(2026, 7, 15)
        val requestCode = AlarmScheduler.createAlarmRequestCode(date, 5, false)
        val uri = AlarmScheduler.getAlarmUriString(date, "Isha", false)

        assertTrue(requestCode > 0)
        assertEquals("salati://alarm/2026-07-15/isha/main", uri)
    }

    @Test
    fun rolledIshaFromYesterdayRemainsScheduledAfterLocalMidnight() {
        val today = LocalDate.of(2026, 7, 16)
        val yesterday = today.minusDays(1)
        val rolledIsha = at(today, 0, 38)
        val result = AlarmScheduler.buildPreparedAlarms(
            timesByDate = mapOf(
                yesterday to prayerTimes(yesterday).copy(isha = rolledIsha),
                today to prayerTimes(today)
            ),
            hijriMetadata = emptyMap(),
            settings = CalculationSettings(prePrayerMinutes = 0),
            nowMillis = at(today, 0, 20).toEpochMilli()
        )

        assertTrue(
            result.alarms.any {
                it.prayerKey == "isha" && it.triggerAtMillis == rolledIsha.toEpochMilli()
            }
        )
    }

    private fun prayerTimes(date: LocalDate): SalatiPrayerTimes {
        return SalatiPrayerTimes(
            date = date,
            fajr = at(date, 5, 0),
            sunrise = at(date, 6, 30),
            dhuhr = at(date, 12, 0),
            asr = at(date, 16, 0),
            maghrib = at(date, 20, 0),
            isha = at(date, 22, 0),
            middleOfTheNight = at(date, 1, 0),
            lastThirdOfTheNight = at(date, 2, 0),
            hijri = null
        )
    }

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant {
        return date.atTime(hour, minute).atZone(brussels).toInstant()
    }

    private fun clockAt(dateTime: LocalDateTime): Clock {
        return Clock.fixed(dateTime.atZone(brussels).toInstant(), brussels)
    }
}
