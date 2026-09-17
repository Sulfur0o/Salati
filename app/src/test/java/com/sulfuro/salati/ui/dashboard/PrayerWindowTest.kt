package com.sulfuro.salati.ui.dashboard

import com.sulfuro.salati.core.computation.SalatiPrayerTimes
import com.sulfuro.salati.core.prayer.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * "Is there still time to pray Asr" is the question the prayer list does not answer, and
 * the one people open the app with. These pin what counts as still.
 */
class PrayerWindowTest {

    private val day = LocalDate.of(2026, 9, 17)
    private fun at(hour: Int, minute: Int = 0): Instant =
        Instant.parse("2026-09-17T%02d:%02d:00Z".format(hour, minute))

    private val times = SalatiPrayerTimes(
        date = day,
        fajr = at(5, 30),
        sunrise = at(7, 15),
        dhuhr = at(13, 30),
        asr = at(17, 0),
        maghrib = at(20, 0),
        isha = at(21, 30),
        middleOfTheNight = at(1, 0),
        lastThirdOfTheNight = at(3, 0),
        hijri = null
    )
    private val tomorrowFajr = at(5, 30).plus(Duration.ofDays(1))

    private fun windowAt(hour: Int, minute: Int = 0) =
        currentPrayerWindow(times, tomorrowFajr, at(hour, minute))

    @Test
    fun theAfternoonBelongsToAsrUntilMaghrib() {
        val window = requireNotNull(windowAt(18, 45))

        assertEquals(Prayer.ASR, window.event)
        assertEquals(times.asr, window.opensAt)
        assertEquals(times.maghrib, window.closesAt)
    }

    /**
     * Sunrise to Dhuhr owes nothing. Naming the prayer that has just expired would read as
     * though it were still due, which is the opposite of the truth.
     */
    @Test
    fun theMorningAfterSunriseOwesNothing() {
        assertNull(windowAt(9, 0))
        assertNull(windowAt(13, 29))
    }

    /** A prayer's window opens the moment it comes in, and the previous one ends there. */
    @Test
    fun aWindowOpensOnItsOwnPrayerAndNotAMomentLater() {
        assertEquals(Prayer.MAGHRIB, requireNotNull(windowAt(20, 0)).event)
        assertEquals(Prayer.ASR, requireNotNull(windowAt(19, 59)).event)
    }

    /**
     * Isha is the one window that crosses midnight, so it has to be found from both sides:
     * after it comes in tonight, and before dawn on the night that is still running.
     */
    @Test
    fun ishaIsStillOpenOnBothSidesOfMidnight() {
        val tonight = requireNotNull(windowAt(22, 30))
        assertEquals(Prayer.ISHA, tonight.event)
        assertEquals(tomorrowFajr, tonight.closesAt)

        val beforeDawn = requireNotNull(windowAt(3, 0))
        assertEquals(Prayer.ISHA, beforeDawn.event)
        assertEquals(times.fajr, beforeDawn.closesAt)
        assertEquals(times.isha.minus(Duration.ofDays(1)), beforeDawn.opensAt)
    }

    @Test
    fun halfwayThroughAWindowReportsHalfOfItGone() {
        val window = requireNotNull(windowAt(18, 30)) // Asr runs 17:00 to 20:00

        assertEquals(0.5f, window.elapsedFraction(at(18, 30)), 0.001f)
        assertEquals(Duration.ofMinutes(90).toMillis(), window.remainingMs(at(18, 30)))
    }

    /** Past the close, nothing is owed and nothing is left; no negative time appears. */
    @Test
    fun aClosedWindowHasNothingLeftRatherThanNegativeTime() {
        val window = requireNotNull(windowAt(19, 0))

        assertEquals(0L, window.remainingMs(at(21, 0)))
        assertEquals(1f, window.elapsedFraction(at(21, 0)), 0.001f)
    }

    @Test
    fun theRemainingTimeIsWrittenToTheMinute() {
        assertEquals("2h 14m", formatWindowRemaining(Duration.ofMinutes(134).toMillis()))
        assertEquals("14m", formatWindowRemaining(Duration.ofMinutes(14).toMillis()))
        assertEquals("1h 00m", formatWindowRemaining(Duration.ofHours(1).toMillis()))
    }

    /**
     * Rounded up, so the card never reads zero while a prayer can still be prayed: the
     * last second of a window is a minute in the only sense that matters here.
     */
    @Test
    fun theLastSecondsStillReadAsAMinute() {
        assertEquals("1m", formatWindowRemaining(1_000L))
        assertEquals("0m", formatWindowRemaining(0L))
        assertEquals("0m", formatWindowRemaining(-5_000L))
    }
}
