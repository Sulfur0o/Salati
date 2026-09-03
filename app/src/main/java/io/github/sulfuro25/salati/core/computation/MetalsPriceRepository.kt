package io.github.sulfuro25.salati.core.computation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class MetalPrices(
    val goldPricePerGram: Double,
    val silverPricePerGram: Double,
    val currencyCode: String,
    /** Quote date reported by the rate source, e.g. "2026-07-18". */
    val rateDate: String,
    val fetchedAtMillis: Long
)

sealed interface MetalPricesResult {
    data class Success(val prices: MetalPrices) : MetalPricesResult
    data object Unavailable : MetalPricesResult
}

object MetalsPriceRepository {
    private const val TAG = "MetalsPriceRepository"

    /** Exact troy ounce to gram conversion; metals are quoted per troy ounce. */
    private const val TROY_OUNCE_GRAMS = 31.1034768

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Primary jsDelivr CDN plus the rate source's documented fallback host. A single
     * CDN edge can serve a stale or failing response, which previously left the user
     * silently stuck on an old price with no indication anything went wrong.
     */
    internal fun endpointsFor(currencyCode: String): List<String> {
        val code = currencyCode.lowercase(Locale.ROOT)
        return listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/$code.json",
            "https://latest.currency-api.pages.dev/v1/currencies/$code.json"
        )
    }

    /**
     * Fetches the latest gold and silver prices per gram, quoted in [currencyCode].
     */
    suspend fun fetchLatestPrices(currencyCode: String): MetalPricesResult = withContext(Dispatchers.IO) {
        for (endpoint in endpointsFor(currencyCode)) {
            val prices = try {
                requestPrices(endpoint, currencyCode)
            } catch (e: Exception) {
                Log.w(TAG, "Metals price request failed for $endpoint", e)
                null
            }
            if (prices != null) return@withContext MetalPricesResult.Success(prices)
        }
        Log.w(TAG, "All metals price endpoints failed for $currencyCode")
        MetalPricesResult.Unavailable
    }

    private fun requestPrices(endpoint: String, currencyCode: String): MetalPrices? {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        val body = try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        return parsePrices(body, currencyCode)
    }

    /**
     * The payload is keyed by the requested currency, e.g. `{"date":"...","usd":{"xau":...}}`,
     * so it is parsed generically rather than against a single hardcoded currency field.
     */
    internal fun parsePrices(body: String, currencyCode: String): MetalPrices? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val code = currencyCode.lowercase(Locale.ROOT)
        val rates = (root[code] as? JsonObject) ?: return null

        val xauRate = rates["xau"]?.jsonPrimitive?.doubleOrNull
        val xagRate = rates["xag"]?.jsonPrimitive?.doubleOrNull
        if (xauRate == null || xagRate == null || xauRate <= 0.0 || xagRate <= 0.0) return null

        // Rates are "1 unit of currency = N troy ounces of metal"; invert then convert to grams.
        // Values are kept at full precision here and only rounded for display, so silver
        // (roughly 1.5 per gram) no longer loses meaningful accuracy to 2-decimal rounding.
        val goldPricePerGram = (1.0 / xauRate) / TROY_OUNCE_GRAMS
        val silverPricePerGram = (1.0 / xagRate) / TROY_OUNCE_GRAMS
        if (!goldPricePerGram.isFinite() || !silverPricePerGram.isFinite()) return null

        val rateDate = root["date"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()

        return MetalPrices(
            goldPricePerGram = goldPricePerGram,
            silverPricePerGram = silverPricePerGram,
            currencyCode = currencyCode.uppercase(Locale.ROOT),
            rateDate = rateDate,
            fetchedAtMillis = System.currentTimeMillis()
        )
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? {
        return runCatching { content }.getOrNull()
    }
}
