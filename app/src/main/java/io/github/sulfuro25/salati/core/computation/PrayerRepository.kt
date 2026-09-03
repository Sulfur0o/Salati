package io.github.sulfuro25.salati.core.computation

import android.content.Context
import android.util.Log
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

data class SalatiPrayerTimes(
    val date: LocalDate,
    val fajr: Instant,
    val sunrise: Instant,
    val dhuhr: Instant,
    val asr: Instant,
    val maghrib: Instant,
    val isha: Instant,
    val middleOfTheNight: Instant,
    val lastThirdOfTheNight: Instant,
    val hijri: HijriDateParts?
)

sealed interface MonthlyPrayerResult {
    data class Success(val data: List<AladhanDayData>) : MonthlyPrayerResult
    data object CacheMiss : MonthlyPrayerResult
    data class TemporaryNetworkFailure(val cause: IOException) : MonthlyPrayerResult
    data class RetryableServerFailure(val statusCode: Int) : MonthlyPrayerResult
    data class PermanentHttpFailure(val statusCode: Int) : MonthlyPrayerResult
    data class InvalidApiResponse(val cause: Throwable) : MonthlyPrayerResult
    data class InvalidCachedData(val cause: Throwable) : MonthlyPrayerResult
    data class InvalidConfiguration(val cause: Throwable) : MonthlyPrayerResult
}

object PrayerRepository {
    private const val TAG = "PrayerRepository"
    private val gregorianDateFormatter = DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    /**
     * Indexes a month's API rows by their own reported Gregorian date instead of
     * array position, so a missing, duplicated, or reordered row can't silently
     * shift every subsequent day's prayer times onto the wrong date.
     */
    fun indexPrayerDataByDate(data: List<AladhanDayData>): Map<LocalDate, AladhanDayData> {
        val map = linkedMapOf<LocalDate, AladhanDayData>()
        for (dayData in data) {
            val parsedDate = runCatching {
                LocalDate.parse(dayData.date.gregorian.date, gregorianDateFormatter)
            }.getOrNull() ?: continue
            map[parsedDate] = dayData
        }
        return map
    }

    fun getAladhanMethodId(method: String): Int? {
        return when (method) {
            "MUSLIM_WORLD_LEAGUE" -> 3
            "ISNA" -> 2
            "EGYPT" -> 5
            "UMM_AL_QURA" -> 4
            "KARACHI" -> 1
            "KUWAIT" -> 9
            "QATAR" -> 10
            "DUBAI" -> 16
            "SINGAPORE" -> 11
            "MOON_SIGHTING" -> 15
            else -> null
        }
    }

    internal fun getCacheFileName(
        year: Int,
        month: Int,
        methodId: Int,
        schoolId: Int,
        latAdjustment: String,
        latitude: Double,
        longitude: Double
    ): String = cacheFileName(
        PrayerMonthRequest(year, month, methodId, schoolId, latAdjustment, latitude, longitude)
    )

    internal fun getCacheFile(
        context: Context,
        year: Int,
        month: Int,
        methodId: Int,
        schoolId: Int,
        latAdjustment: String,
        latitude: Double,
        longitude: Double
    ): File = File(
        context.filesDir,
        getCacheFileName(year, month, methodId, schoolId, latAdjustment, latitude, longitude)
    )

    suspend fun getMonthlyPrayers(
        context: Context,
        settings: CalculationSettings,
        year: Int,
        month: Int,
        requireCacheOnly: Boolean = false,
        apiClient: PrayerApiClient = UrlConnectionPrayerApiClient
    ): MonthlyPrayerResult = withContext(Dispatchers.IO) {
        val request = try {
            createRequest(settings, year, month)
        } catch (cause: IllegalArgumentException) {
            return@withContext MonthlyPrayerResult.InvalidConfiguration(cause)
        }

        getMonthlyPrayers(
            request = request,
            requireCacheOnly = requireCacheOnly,
            cacheDataSource = AtomicFilePrayerCacheDataSource(context.filesDir),
            remoteDataSource = AladhanPrayerRemoteDataSource(apiClient),
            responseParser = AladhanPrayerResponseParser
        )
    }

    suspend fun getHijriMetadataRange(
        context: Context,
        settings: CalculationSettings,
        startDate: LocalDate,
        endDate: LocalDate,
        requireCacheOnly: Boolean = false
    ): Map<LocalDate, HijriDateParts> {
        val actualStart = startDate.plusDays(settings.hijriOffset.toLong())
        val actualEnd = endDate.plusDays(settings.hijriOffset.toLong())

        var currentMonth = YearMonth.from(actualStart)
        val endMonth = YearMonth.from(actualEnd)

        val monthsToLoad = mutableSetOf<YearMonth>()
        while (!currentMonth.isAfter(endMonth)) {
            monthsToLoad.add(currentMonth)
            currentMonth = currentMonth.plusMonths(1)
        }

        val resultMap = mutableMapOf<LocalDate, HijriDateParts>()
        for (month in monthsToLoad) {
            val result = getMonthlyPrayers(
                context, settings, month.year, month.monthValue,
                requireCacheOnly = requireCacheOnly
            )
            if (result is MonthlyPrayerResult.Success) {
                for ((localDate, dayData) in indexPrayerDataByDate(result.data)) {
                    val parsedTimes = parsePrayerTimes(dayData, settings)
                    if (parsedTimes.hijri != null) {
                        resultMap[localDate] = parsedTimes.hijri
                    }
                }
            }
        }
        return resultMap
    }

    internal fun getMonthlyPrayers(
        request: PrayerMonthRequest,
        requireCacheOnly: Boolean,
        cacheDataSource: PrayerCacheDataSource,
        remoteDataSource: PrayerRemoteDataSource,
        responseParser: PrayerResponseParser
    ): MonthlyPrayerResult {
        when (val cacheRead = cacheDataSource.read(request)) {
            is PrayerCacheReadResult.Success -> {
                when (val parsed = responseParser.parse(cacheRead.rawJson)) {
                    is PrayerResponseParseResult.Success -> {
                        return MonthlyPrayerResult.Success(parsed.data)
                    }
                    is PrayerResponseParseResult.Failure -> {
                        handleInvalidCache(cacheDataSource, request, parsed.cause)
                        if (requireCacheOnly) {
                            return MonthlyPrayerResult.InvalidCachedData(parsed.cause)
                        }
                    }
                }
            }
            is PrayerCacheReadResult.Failure -> {
                handleInvalidCache(cacheDataSource, request, cacheRead.cause)
                if (requireCacheOnly) {
                    return MonthlyPrayerResult.InvalidCachedData(cacheRead.cause)
                }
            }
            PrayerCacheReadResult.Missing -> Unit
        }

        if (requireCacheOnly) {
            return MonthlyPrayerResult.CacheMiss
        }

        val response = try {
            remoteDataSource.fetchMonth(request)
        } catch (cause: IOException) {
            Log.e(TAG, "Temporary network error fetching prayer times: ${cause.message}", cause)
            return MonthlyPrayerResult.TemporaryNetworkFailure(cause)
        } catch (cause: IllegalArgumentException) {
            return MonthlyPrayerResult.InvalidConfiguration(cause)
        } catch (cause: Exception) {
            return MonthlyPrayerResult.InvalidApiResponse(cause)
        }

        when {
            response.statusCode == 429 || response.statusCode in 500..599 -> {
                return MonthlyPrayerResult.RetryableServerFailure(response.statusCode)
            }
            response.statusCode in 400..499 -> {
                return MonthlyPrayerResult.PermanentHttpFailure(response.statusCode)
            }
            response.statusCode != HttpURLConnection.HTTP_OK -> {
                return MonthlyPrayerResult.PermanentHttpFailure(response.statusCode)
            }
        }

        return when (val parsed = responseParser.parse(response.body)) {
            is PrayerResponseParseResult.Failure -> {
                MonthlyPrayerResult.InvalidApiResponse(parsed.cause)
            }
            is PrayerResponseParseResult.Success -> {
                when (val cacheWrite = cacheDataSource.write(request, response.body)) {
                    PrayerCacheWriteResult.Success -> Unit
                    is PrayerCacheWriteResult.Failure -> {
                        Log.w(TAG, "Prayer response succeeded but could not be cached", cacheWrite.cause)
                    }
                }
                MonthlyPrayerResult.Success(parsed.data)
            }
        }
    }

    fun parsePrayerTimes(dayData: AladhanDayData, settings: CalculationSettings): SalatiPrayerTimes {
        return SalatiPrayerTimeMapper.map(dayData, ZoneId.of(settings.timezoneId))
    }

    internal fun buildApiUrl(
        year: Int,
        month: Int,
        methodId: Int,
        schoolId: Int,
        latAdjustment: String,
        latitude: Double,
        longitude: Double
    ): String = AladhanPrayerRemoteDataSource().buildUrl(
        PrayerMonthRequest(year, month, methodId, schoolId, latAdjustment, latitude, longitude)
    )

    private fun createRequest(
        settings: CalculationSettings,
        year: Int,
        month: Int
    ): PrayerMonthRequest {
        require(year in 1..9999 && month in 1..12) { "Invalid year/month: $year/$month" }
        val methodId = requireNotNull(getAladhanMethodId(settings.calculationMethod)) {
            "Unsupported calculation method: ${settings.calculationMethod}"
        }
        val latitudeAdjustmentId = when (settings.highLatitudeRule) {
            "MIDDLE_OF_THE_NIGHT" -> "1"
            "SEVENTH_OF_THE_NIGHT" -> "2"
            "TWILIGHT_ANGLE" -> "3"
            else -> throw IllegalArgumentException(
                "Unsupported high-latitude rule: ${settings.highLatitudeRule}"
            )
        }
        val schoolId = when (settings.madhab) {
            "SHAFI" -> 0
            "HANAFI" -> 1
            else -> throw IllegalArgumentException("Unsupported Madhab: ${settings.madhab}")
        }
        return PrayerMonthRequest(
            year, month, methodId, schoolId, latitudeAdjustmentId,
            settings.latitude, settings.longitude
        )
    }

    private fun handleInvalidCache(
        cacheDataSource: PrayerCacheDataSource,
        request: PrayerMonthRequest,
        cause: Throwable
    ) {
        Log.e(TAG, "Error reading cached prayer times: ${cause.message}", cause)
        when (val invalidation = cacheDataSource.invalidate(request)) {
            PrayerCacheInvalidationResult.Success,
            PrayerCacheInvalidationResult.Missing -> Unit
            is PrayerCacheInvalidationResult.Failure -> {
                Log.w(TAG, "Invalid prayer cache could not be removed", invalidation.cause)
            }
        }
    }
}
