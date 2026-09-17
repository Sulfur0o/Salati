package com.sulfuro.salati.core.prayer

import androidx.annotation.StringRes
import com.sulfuro.salati.R
import com.sulfuro.salati.core.computation.SalatiPrayerTimes
import java.time.Duration
import java.time.Instant

/**
 * The daily events the app knows about, in the order they happen.
 *
 * This is the one place the app's list of prayers is written down. It used to be typed out
 * by hand in six: the dashboard's own enum, the window chain beside it, the calendar's six
 * literal rows, the scheduler's five `addAlarm("Fajr", ...)` calls, the widget snapshot's
 * five `PrayerInfo(...)` lines, and three parallel arrays of view ids in each widget
 * provider. Nothing tied those six to each other, so nothing caught a disagreement between
 * them - a missing prayer built cleanly and only showed up on the phone.
 *
 * Declaration order is load-bearing: it is the order of the day, so "the event that ends
 * this prayer's window" is simply the next entry (see [SalatiPrayerTimes.windowCloser]).
 *
 * @param labelRes the prayer's translated name.
 * @param key how the prayer is identified outside the app - in alarm URIs, the registry on
 *   disk, and the intent extra the receiver reads. It is written out rather than derived
 *   from [name] because alarms scheduled by an older version are still on the phone under
 *   these exact spellings; renaming a constant must not silently orphan them.
 * @param alarmId the fixed id that goes into a [android.app.PendingIntent] request code,
 *   for the same reason. Null for an event that raises no alarm.
 */
enum class Prayer(
    @param:StringRes val labelRes: Int,
    val key: String,
    val alarmId: Int?
) {
    FAJR(R.string.prayer_fajr, "fajr", 1),

    /** Not a prayer: the end of Fajr's window, and shown only so the day reads in order. */
    SUNRISE(R.string.prayer_sunrise, "sunrise", null),
    DHUHR(R.string.prayer_dhuhr, "dhuhr", 2),
    ASR(R.string.prayer_asr, "asr", 3),
    MAGHRIB(R.string.prayer_maghrib, "maghrib", 4),
    ISHA(R.string.prayer_isha, "isha", 5);

    /** Whether this event is prayed, and so can be alerted and have a window. */
    val isPrayer: Boolean get() = alarmId != null

    companion object {
        /** The five that are prayed, in order. Sunrise is a time of day, not a prayer. */
        val prayed: List<Prayer> = entries.filter { it.isPrayer }

        /** Resolves a [key], or the capitalised spelling an older alarm may still carry. */
        fun byKey(key: String): Prayer? = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

/** The time of one event on a given day. */
operator fun SalatiPrayerTimes.get(prayer: Prayer): Instant = when (prayer) {
    Prayer.FAJR -> fajr
    Prayer.SUNRISE -> sunrise
    Prayer.DHUHR -> dhuhr
    Prayer.ASR -> asr
    Prayer.MAGHRIB -> maghrib
    Prayer.ISHA -> isha
}

/** Every event of the day paired with its time, in order. */
fun SalatiPrayerTimes.byEvent(): List<Pair<Prayer, Instant>> =
    Prayer.entries.map { it to this[it] }

/**
 * When [prayer]'s window closes: the next event of the day.
 *
 * Fajr closes at sunrise, Dhuhr at Asr, Asr at Maghrib, Maghrib at Isha. Isha is the one
 * that runs past midnight and so has no successor today - it closes at [tomorrowFajr].
 */
fun SalatiPrayerTimes.windowCloser(prayer: Prayer, tomorrowFajr: Instant): Instant =
    Prayer.entries.getOrNull(prayer.ordinal + 1)?.let { this[it] } ?: tomorrowFajr

/**
 * The same day's times moved on by exactly 24 hours.
 *
 * A stand-in for tomorrow when tomorrow's are not to hand - good enough to keep a countdown
 * running to the next Fajr, and wrong by however much the sun moved, which is minutes.
 * Exact hours rather than a calendar day on purpose: these are instants, and adding a day
 * across a daylight-saving change would shift them by an hour.
 */
fun SalatiPrayerTimes.plusExactDay(): SalatiPrayerTimes {
    val elapsedDay = Duration.ofHours(24)
    return copy(
        fajr = fajr.plus(elapsedDay),
        sunrise = sunrise.plus(elapsedDay),
        dhuhr = dhuhr.plus(elapsedDay),
        asr = asr.plus(elapsedDay),
        maghrib = maghrib.plus(elapsedDay),
        isha = isha.plus(elapsedDay)
    )
}
