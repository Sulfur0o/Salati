package com.sulfuro.salati.core.computation

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
                // Filter and preserve all valid day entries rather than rejecting the entire month
                // if a single malformed item occurs.
                val validDays = response.data.filter { day ->
                    runCatching {
                        SalatiPrayerTimeMapper.map(day, java.time.ZoneOffset.UTC)
                    }.isSuccess
                }
                if (validDays.isEmpty()) {
                    PrayerResponseParseResult.Failure(
                        IllegalStateException("Aladhan response contains no valid day records")
                    )
                } else {
                    PrayerResponseParseResult.Success(validDays)
                }
            }
        } catch (cause: Exception) {
            PrayerResponseParseResult.Failure(cause)
        }
    }
}
