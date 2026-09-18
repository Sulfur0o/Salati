package com.sulfuro.salati.core.computation

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Locale

object HijriCalendarHelper {

    /**
     * A Hijri date as numbers, which is all of it that does not depend on who is reading.
     *
     * The month's name is asked for rather than stored, because the app's language can
     * change while a date is on screen and a name captured when the date was worked out
     * would still be in the old one.
     */
    data class HijriDateComponents(
        val day: Int,
        val monthNumber: Int,
        val year: Int
    ) {
        /** The month, in the reader's language. See [HijriMonthNames]. */
        fun monthName(locale: Locale = Locale.getDefault()): String =
            HijriMonthNames.of(monthNumber, locale)

        /** `7 Rabi' al-Thani 1448`, in the reader's language. */
        fun format(locale: Locale = Locale.getDefault()): String =
            "$day ${monthName(locale)} $year"
    }



    fun resolveHijriDate(
        gregorianDate: LocalDate,
        offsetDays: Int,
        isAfterMaghrib: Boolean = false,
        apiLookup: (LocalDate) -> HijriDateParts? = { null }
    ): HijriDateComponents {
        val totalOffset = offsetDays + if (isAfterMaghrib) 1 else 0
        val targetGregorianDate = gregorianDate.plusDays(totalOffset.toLong())

        val apiData = apiLookup(targetGregorianDate)
        if (apiData != null) {
            return HijriDateComponents(
                day = apiData.day,
                monthNumber = apiData.month,
                year = apiData.year
            )
        }

        // Fallback for missing API data
        val hijriDate = HijrahDate.from(targetGregorianDate)
        val day = hijriDate.get(ChronoField.DAY_OF_MONTH)
        val monthNumber = hijriDate.get(ChronoField.MONTH_OF_YEAR)
        val year = hijriDate.get(ChronoField.YEAR)

        return HijriDateComponents(day, monthNumber, year)
    }

    fun getHijriDate(
        date: LocalDate,
        offsetDays: Int,
        isAfterMaghrib: Boolean = false
    ): HijriDateComponents {
        return resolveHijriDate(date, offsetDays, isAfterMaghrib) { null }
    }
}
