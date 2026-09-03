package io.github.sulfuro25.salati.core.location

import android.content.Context
import io.github.sulfuro25.salati.core.computation.MonthlyPrayerResult
import io.github.sulfuro25.salati.core.computation.PrayerRepository
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import java.time.YearMonth
import java.time.ZoneId

internal object PrayerLocationResolver {
    suspend fun withResolvedTimezone(
        context: Context,
        current: CalculationSettings,
        cityName: String,
        latitude: Double,
        longitude: Double
    ): CalculationSettings {
        val currentZone = runCatching { ZoneId.of(current.timezoneId) }
            .getOrElse { ZoneId.systemDefault() }
        val now = YearMonth.now(currentZone)
        val prayerResult = PrayerRepository.getMonthlyPrayers(
            context = context,
            settings = current.copy(
                cityName = cityName,
                latitude = latitude,
                longitude = longitude
            ),
            year = now.year,
            month = now.monthValue
        )
        val resolvedZoneId = (prayerResult as? MonthlyPrayerResult.Success)?.data
            ?.firstOrNull()?.meta?.timezone
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        return current.copy(
            cityName = cityName,
            latitude = latitude,
            longitude = longitude,
            timezoneId = resolvedZoneId?.id ?: current.timezoneId
        )
    }
}
