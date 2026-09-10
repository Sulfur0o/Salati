package io.github.sulfuro25.salati.core.location

import android.content.Context
import io.github.sulfuro25.salati.core.computation.MonthlyPrayerResult
import io.github.sulfuro25.salati.core.computation.PrayerDataOrigin
import io.github.sulfuro25.salati.core.computation.PrayerRepository
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

internal object PrayerLocationResolver {
    suspend fun withResolvedTimezone(
        context: Context,
        current: CalculationSettings,
        cityName: String,
        latitude: Double,
        longitude: Double,
        countryName: String? = null,
        deviceZoneId: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now()
    ): CalculationSettings {
        val estimatedZone = CoordinateTimezoneLookup.zoneIdFor(
            latitude = latitude,
            longitude = longitude,
            deviceZoneId = deviceZoneId,
            at = now
        )
        val calendarMonth = YearMonth.now(estimatedZone)
        val prayerResult = PrayerRepository.getMonthlyPrayers(
            context = context,
            settings = current.copy(
                cityName = cityName,
                latitude = latitude,
                longitude = longitude,
                timezoneId = estimatedZone.id
            ),
            year = calendarMonth.year,
            month = calendarMonth.monthValue
        )
        val networkTimezoneId = (prayerResult as? MonthlyPrayerResult.Success)
            ?.takeIf { it.origin == PrayerDataOrigin.NETWORK }
            ?.data?.firstOrNull()?.meta?.timezone
        return current.copy(
            cityName = cityName,
            countryName = countryName?.takeIf { it.isNotBlank() } ?: current.countryName,
            latitude = latitude,
            longitude = longitude,
            timezoneId = resolveTimezoneId(
                latitude = latitude,
                longitude = longitude,
                networkTimezoneId = networkTimezoneId,
                deviceZoneId = deviceZoneId,
                at = now
            )
        )
    }

    /**
     * Aladhan is authoritative when it actually answered. Otherwise the on-device
     * estimate for the *new* coordinates is used. The previous city's zone is never
     * kept just because the network was down.
     */
    internal fun resolveTimezoneId(
        latitude: Double,
        longitude: Double,
        networkTimezoneId: String?,
        deviceZoneId: ZoneId = ZoneId.systemDefault(),
        at: Instant = Instant.now()
    ): String {
        val fromNetwork = networkTimezoneId
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        if (fromNetwork != null) return fromNetwork.id
        return CoordinateTimezoneLookup.zoneIdFor(
            latitude = latitude,
            longitude = longitude,
            deviceZoneId = deviceZoneId,
            at = at
        ).id
    }
}
