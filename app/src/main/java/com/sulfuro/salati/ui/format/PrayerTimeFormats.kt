package com.sulfuro.salati.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How the app writes dates and times on screen.
 *
 * The Daily and Monthly screens each had their own copy of these, identical down to the
 * pattern string, which is how two screens end up spelling the same date two ways after one
 * of them is edited.
 */

/**
 * A prayer time - `05:13`, or `5:13 AM` where the user has asked for a 12-hour clock.
 *
 * Bound to [zoneId] rather than the device's: the times belong to the place prayers are
 * being calculated for, which is not always where the phone is.
 */
fun prayerTimeFormatter(locale: Locale, zoneId: ZoneId, is24Hour: Boolean = true): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale).withZone(zoneId)

/** `Wednesday, 15 July 2026` - the day a screen is showing, written out in full. */
fun longDateFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu", locale)

/** `July 2026` - the heading over a month of the calendar. */
fun monthHeading(yearMonth: YearMonth, locale: Locale): String =
    DateTimeFormatter.ofPattern("MMMM uuuu", locale).format(yearMonth.atDay(1))

/** The date it is at [epochMillis], where prayers are being calculated. */
fun localDateAt(epochMillis: Long, zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
