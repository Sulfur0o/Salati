package io.github.sulfuro25.salati.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrayerSilentModeSchedulerTest {
    @Test
    fun windowStartsAtConfiguredOffsetAndLastsConfiguredDuration() {
        val prayerAt = 1_000_000L

        val window = calculatePrayerSilentWindow(
            prayerAtMillis = prayerAt,
            minutesAfterAdhan = 10,
            durationMinutes = 20,
            nowMillis = prayerAt
        )

        assertEquals(prayerAt + 10 * 60_000L, window?.startAtMillis)
        assertEquals(prayerAt + 30 * 60_000L, window?.endAtMillis)
    }

    @Test
    fun delayedAdhanDeliveryStartsImmediatelyButKeepsOriginalEnd() {
        val prayerAt = 1_000_000L
        val deliveredAt = prayerAt + 12 * 60_000L

        val window = calculatePrayerSilentWindow(
            prayerAtMillis = prayerAt,
            minutesAfterAdhan = 5,
            durationMinutes = 20,
            nowMillis = deliveredAt
        )

        assertEquals(deliveredAt, window?.startAtMillis)
        assertEquals(prayerAt + 25 * 60_000L, window?.endAtMillis)
    }

    @Test
    fun expiredOrUnsupportedWindowsAreRejected() {
        val prayerAt = 1_000_000L

        assertNull(calculatePrayerSilentWindow(prayerAt, 3, 20, prayerAt))
        assertNull(calculatePrayerSilentWindow(prayerAt, 5, 25, prayerAt))
        assertNull(calculatePrayerSilentWindow(prayerAt, 0, 15, prayerAt + 15 * 60_000L))
    }

    @Test
    fun startAndRestorePendingIntentCodesCannotCollideWithPrayerCode() {
        val prayerCode = 27_001_234
        val startCode = PrayerSilentModeScheduler.startRequestCode(prayerCode)
        val restoreCode = PrayerSilentModeScheduler.restoreRequestCode(prayerCode)

        assertNotEquals(prayerCode, startCode)
        assertNotEquals(prayerCode, restoreCode)
        assertNotEquals(startCode, restoreCode)
    }
}
