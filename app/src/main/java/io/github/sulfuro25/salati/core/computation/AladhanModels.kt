package io.github.sulfuro25.salati.core.computation

import kotlinx.serialization.Serializable

@Serializable
data class AladhanResponse(
    val code: Int,
    val status: String,
    val data: List<AladhanDayData>
)

@Serializable
data class AladhanDayData(
    val timings: AladhanTimings,
    val date: AladhanDate,
    val meta: AladhanMeta? = null
)

@Serializable
data class AladhanMeta(
    val timezone: String
)

@Serializable
data class AladhanTimings(
    val Fajr: String,
    val Sunrise: String,
    val Dhuhr: String,
    val Asr: String,
    val Sunset: String,
    val Maghrib: String,
    val Isha: String,
    val Midnight: String,
    val Lastthird: String
)

@Serializable
data class AladhanDate(
    val readable: String,
    val timestamp: String,
    val gregorian: AladhanGregorianDate,
    val hijri: AladhanHijriDate? = null
)

@Serializable
data class AladhanGregorianDate(
    val date: String,
    val day: String,
    val month: AladhanMonth,
    val year: String
)

@Serializable
data class AladhanHijriDate(
    val day: String,
    val month: AladhanMonth,
    val year: String
)

@Serializable
data class AladhanMonth(
    val number: Int
)

data class HijriDateParts(
    val day: Int,
    val month: Int,
    val year: Int
)
