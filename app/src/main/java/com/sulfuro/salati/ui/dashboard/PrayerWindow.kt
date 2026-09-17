package com.sulfuro.salati.ui.dashboard

import com.sulfuro.salati.core.computation.SalatiPrayerTimes
import com.sulfuro.salati.core.prayer.Prayer
import com.sulfuro.salati.core.prayer.get
import com.sulfuro.salati.core.prayer.windowCloser
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.ceil

/**
 * The stretch of time a prayer may still be prayed in, and how much of it is left.
 *
 * The list of times answers "when does Asr start"; this answers "is it too late", which is
 * the question people actually open the app with. Each window runs from its own prayer to
 * the next event that ends it: Fajr closes at sunrise, Dhuhr at Asr, Asr at Maghrib,
 * Maghrib at Isha, and Isha at the following dawn.
 *
 * @param opensAt when the prayer came in, so a bar can show how much has gone.
 * @param closesAt when it can no longer be prayed on time.
 */
internal data class PrayerWindow(
    val event: Prayer,
    val opensAt: Instant,
    val closesAt: Instant
) {
    fun remainingMs(now: Instant): Long = Duration.between(now, closesAt).toMillis().coerceAtLeast(0L)

    /**
     * How much of the window has gone, from 0 to 1. Zero-length windows report full: a
     * window with no time left in it has none left whatever the arithmetic says.
     */
    fun elapsedFraction(now: Instant): Float {
        val total = Duration.between(opensAt, closesAt).toMillis()
        if (total <= 0L) return 1f
        val gone = Duration.between(opensAt, now).toMillis()
        return (gone.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}

/**
 * The window open at [now], or null when none is.
 *
 * Null is a real answer and not a gap in the data: between sunrise and Dhuhr no prayer is
 * due, and saying so is more honest than naming the one that has just expired.
 *
 * @param tomorrowFajr ends the Isha window. Isha is the one prayer whose window crosses
 *   midnight, so it appears twice here - once for tonight and once for the night that is
 *   still running when the app is opened before dawn.
 */
internal fun currentPrayerWindow(
    times: SalatiPrayerTimes,
    tomorrowFajr: Instant,
    now: Instant
): PrayerWindow? {
    val aDay = Duration.ofDays(1)
    val windows = buildList {
        // Last night's Isha, still open until this morning's Fajr.
        add(PrayerWindow(Prayer.ISHA, times[Prayer.ISHA].minus(aDay), times[Prayer.FAJR]))
        // Then today's, each closing where the next event of the day opens. Sunrise is not
        // in this list because nothing is due between it and Dhuhr.
        Prayer.prayed.forEach { prayer ->
            add(PrayerWindow(prayer, times[prayer], times.windowCloser(prayer, tomorrowFajr)))
        }
    }
    return windows.firstOrNull { now >= it.opensAt && now < it.closesAt }
}

/**
 * A window's remaining time, to the minute.
 *
 * Minutes are rounded up so the card never reads zero while there is still time to pray,
 * and seconds are left off: this is a deadline, not a countdown, and a ticking second hand
 * on it would read as pressure rather than information.
 *
 * Locale.ROOT for the same reason the hero countdown uses it - the h/m markers are the
 * app's own shorthand and stay the same in every language it speaks.
 */
internal fun formatWindowRemaining(remainingMs: Long): String {
    val minutes = ceil(remainingMs.coerceAtLeast(0L) / 60_000.0).toLong()
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%dh %02dm", hours, rest)
    } else {
        String.format(Locale.ROOT, "%dm", rest)
    }
}
