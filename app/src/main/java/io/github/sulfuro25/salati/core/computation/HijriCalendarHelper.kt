package io.github.sulfuro25.salati.core.computation

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

object HijriCalendarHelper {

    private val hijriMonths = arrayOf(
        "Muharram",
        "Safar",
        "Rabi' al-Awwal",
        "Rabi' al-Thani",
        "Jumada al-Awwal",
        "Jumada al-Thani",
        "Rajab",
        "Sha'ban",
        "Ramadan",
        "Shawwal",
        "Dhu al-Qi'dah",
        "Dhu al-Hijjah"
    )

    data class HijriDateComponents(
        val day: Int,
        val monthNumber: Int,
        val monthName: String,
        val year: Int
    ) {
        fun format(): String {
            return "$day $monthName $year"
        }
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
                monthName = hijriMonths.getOrNull(apiData.month - 1) ?: "",
                year = apiData.year
            )
        }

        // Fallback for missing API data
        val hijriDate = HijrahDate.from(targetGregorianDate)
        val day = hijriDate.get(ChronoField.DAY_OF_MONTH)
        val monthNumber = hijriDate.get(ChronoField.MONTH_OF_YEAR)
        val year = hijriDate.get(ChronoField.YEAR)
        val monthName = hijriMonths.getOrNull(monthNumber - 1) ?: ""

        return HijriDateComponents(day, monthNumber, monthName, year)
    }

    fun getHijriDate(
        date: LocalDate,
        offsetDays: Int,
        isAfterMaghrib: Boolean = false
    ): HijriDateComponents {
        return resolveHijriDate(date, offsetDays, isAfterMaghrib) { null }
    }
}
