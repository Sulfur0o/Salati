package io.github.sulfuro25.salati.core.computation

data class PrayerMonthRequest(
    val year: Int,
    val month: Int,
    val methodId: Int,
    val schoolId: Int,
    val latitudeAdjustmentId: String,
    val latitude: Double,
    val longitude: Double
)
