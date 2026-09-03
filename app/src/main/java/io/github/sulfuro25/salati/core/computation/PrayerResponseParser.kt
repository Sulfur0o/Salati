package io.github.sulfuro25.salati.core.computation

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

sealed interface PrayerResponseParseResult {
    data class Success(val data: List<AladhanDayData>) : PrayerResponseParseResult
    data class Failure(val cause: Throwable) : PrayerResponseParseResult
}

fun interface PrayerResponseParser {
    fun parse(rawJson: String): PrayerResponseParseResult
}

object AladhanPrayerResponseParser : PrayerResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    override fun parse(rawJson: String): PrayerResponseParseResult {
        return try {
            val response = json.decodeFromString<AladhanResponse>(rawJson)
            if (response.code != 200 || response.data.isEmpty()) {
                PrayerResponseParseResult.Failure(
                    IllegalStateException("Aladhan response is empty or unsuccessful")
                )
            } else {
                // Strictly pre-validate every day entry so malformed timing strings or corrupt dates
                // are rejected early and never saved into disk cache.
                for (day in response.data) {
                    SalatiPrayerTimeMapper.map(day, java.time.ZoneOffset.UTC)
                }
                PrayerResponseParseResult.Success(response.data)
            }
        } catch (cause: Exception) {
            PrayerResponseParseResult.Failure(cause)
        }
    }
}
