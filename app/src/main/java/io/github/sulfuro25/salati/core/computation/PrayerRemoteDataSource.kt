package io.github.sulfuro25.salati.core.computation

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class PrayerHttpResponse(
    val statusCode: Int,
    val body: String
)

fun interface PrayerApiClient {
    @Throws(IOException::class)
    fun fetch(url: String): PrayerHttpResponse
}

object UrlConnectionPrayerApiClient : PrayerApiClient {
    override fun fetch(url: String): PrayerHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            PrayerHttpResponse(
                statusCode = statusCode,
                body = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            )
        } finally {
            connection.disconnect()
        }
    }
}

fun interface PrayerRemoteDataSource {
    @Throws(IOException::class)
    fun fetchMonth(request: PrayerMonthRequest): PrayerHttpResponse
}

class AladhanPrayerRemoteDataSource(
    private val apiClient: PrayerApiClient = UrlConnectionPrayerApiClient
) : PrayerRemoteDataSource {
    override fun fetchMonth(request: PrayerMonthRequest): PrayerHttpResponse {
        return apiClient.fetch(buildUrl(request))
    }

    internal fun buildUrl(request: PrayerMonthRequest): String {
        return "https://api.aladhan.com/v1/calendar/${request.year}/${request.month}" +
            "?latitude=${request.latitude}" +
            "&longitude=${request.longitude}" +
            "&method=${request.methodId}" +
            "&school=${request.schoolId}" +
            "&latitudeAdjustmentMethod=${request.latitudeAdjustmentId}"
    }
}
