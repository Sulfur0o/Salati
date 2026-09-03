package io.github.sulfuro25.salati.core.computation

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

fun interface PrayerTimeMapper {
    fun map(dayData: AladhanDayData, fallbackZoneId: ZoneId): SalatiPrayerTimes
}

object SalatiPrayerTimeMapper : PrayerTimeMapper {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    override fun map(dayData: AladhanDayData, fallbackZoneId: ZoneId): SalatiPrayerTimes {
        val zoneId = dayData.meta?.timezone?.let(ZoneId::of) ?: fallbackZoneId
        val dateText = dayData.date.gregorian.date
        val date = LocalDate.parse(dateText, dateFormatter)

        fun parseLocalTime(rawTime: String): LocalTime {
            val cleanTime = rawTime.substringBefore(" ").trim()
            return LocalTime.parse(cleanTime, timeFormatter)
        }

        val maghribTime = parseLocalTime(dayData.timings.Maghrib)

        // Isha and the night-midpoint fields are always after Maghrib; when their
        // clock time is not later than Maghrib's, they occurred after local midnight
        // and belong to the following calendar day.
        fun parseInstant(rawTime: String, rolloverAfterMaghrib: Boolean): Instant {
            val localTime = parseLocalTime(rawTime)
            val targetDate = if (rolloverAfterMaghrib && !localTime.isAfter(maghribTime)) {
                date.plusDays(1)
            } else {
                date
            }
            return resolveInstant(LocalDateTime.of(targetDate, localTime), zoneId)
        }

        val hijriParts = dayData.date.hijri?.let {
            val day = it.day.toIntOrNull()
            val month = it.month.number
            val year = it.year.toIntOrNull()
            if (day != null && year != null) HijriDateParts(day, month, year) else null
        }

        return SalatiPrayerTimes(
            date = date,
            fajr = parseInstant(dayData.timings.Fajr, rolloverAfterMaghrib = false),
            sunrise = parseInstant(dayData.timings.Sunrise, rolloverAfterMaghrib = false),
            dhuhr = parseInstant(dayData.timings.Dhuhr, rolloverAfterMaghrib = false),
            asr = parseInstant(dayData.timings.Asr, rolloverAfterMaghrib = false),
            maghrib = parseInstant(dayData.timings.Maghrib, rolloverAfterMaghrib = false),
            isha = parseInstant(dayData.timings.Isha, rolloverAfterMaghrib = true),
            middleOfTheNight = parseInstant(dayData.timings.Midnight, rolloverAfterMaghrib = true),
            lastThirdOfTheNight = parseInstant(dayData.timings.Lastthird, rolloverAfterMaghrib = true),
            hijri = hijriParts
        )
    }

    private fun resolveInstant(localDateTime: LocalDateTime, zoneId: ZoneId): Instant {
        val offsets = zoneId.rules.getValidOffsets(localDateTime)
        val offset = when (offsets.size) {
            0 -> throw DateTimeException("Nonexistent local time in $zoneId: $localDateTime")
            1 -> offsets.single()
            2 -> offsets.last()
            else -> error("Unexpected offset count for $zoneId: ${offsets.size}")
        }
        return localDateTime.toInstant(offset)
    }
}
