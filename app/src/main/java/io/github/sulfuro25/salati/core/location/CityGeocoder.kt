package io.github.sulfuro25.salati.core.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/** One geocoded place the user can pick, already reduced to what Salati needs. */
data class CitySuggestion(
    /** Full label shown in the list, e.g. "Istanbul, Istanbul, Türkiye". */
    val displayName: String,
    /** Stored in settings and shown on the dashboard, e.g. "Istanbul, Türkiye". */
    val cityName: String,
    val countryName: String,
    val latitude: Double,
    val longitude: Double
)

sealed interface CitySearchResult {
    data class Success(val suggestions: List<CitySuggestion>) : CitySearchResult
    /** The query was well-formed but matched nothing. */
    data object NoMatches : CitySearchResult
    /** No geocoding backend on this device (common on de-Googled or emulator images). */
    data object Unavailable : CitySearchResult
    /** Backend reached but failed, typically offline. */
    data class Failure(val cause: Throwable) : CitySearchResult
}

/**
 * Wraps [Geocoder] so the settings screen can offer city-name search instead of asking
 * for raw coordinates.
 *
 * The platform geocoder has two shapes: a blocking call that is deprecated (and must
 * never run on the main thread) and an asynchronous callback added in API 33. Both are
 * bridged to one suspending function here so callers do not have to care.
 */
object CityGeocoder {

    const val MAX_RESULTS = 5

    /** Shorter queries match too much of the world to be useful as suggestions. */
    private const val MIN_QUERY_LENGTH = 2

    suspend fun search(
        context: Context,
        query: String,
        locale: Locale = Locale.getDefault(),
        maxResults: Int = MAX_RESULTS
    ): CitySearchResult {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return CitySearchResult.NoMatches
        if (!Geocoder.isPresent()) return CitySearchResult.Unavailable

        val geocoder = runCatching { Geocoder(context, locale) }
            .getOrElse { return CitySearchResult.Unavailable }

        return try {
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocodeAsync(geocoder, trimmed, maxResults)
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(trimmed, maxResults).orEmpty()
                }
            }
            val suggestions = addresses.mapNotNull(::toSuggestion).distinctBy {
                // Geocoders happily return the same town several times with different
                // administrative wrappers; collapse those to one row.
                "${it.displayName}|${"%.3f".format(Locale.ROOT, it.latitude)}"
            }
            if (suggestions.isEmpty()) CitySearchResult.NoMatches else CitySearchResult.Success(suggestions)
        } catch (io: IOException) {
            CitySearchResult.Failure(io)
        } catch (illegal: IllegalArgumentException) {
            CitySearchResult.Failure(illegal)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocodeAsync(
        geocoder: Geocoder,
        query: String,
        maxResults: Int
    ): List<Address> = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocationName(
            query,
            maxResults,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) continuation.resume(addresses)
                }

                override fun onError(errorMessage: String?) {
                    // Treated as "nothing found" rather than a crash: the platform uses
                    // this for ordinary conditions such as a backend timeout.
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            }
        )
    }

    /**
     * Builds the two labels the UI needs. [Address.getLocality] is frequently null for
     * regions and small towns, so the first non-blank of several fields is used before
     * giving up on the row entirely.
     */
    internal fun toSuggestion(address: Address): CitySuggestion? {
        if (!address.hasLatitude() || !address.hasLongitude()) return null

        val city = listOfNotNull(
            address.locality,
            address.subAdminArea,
            address.adminArea,
            address.featureName
        ).firstOrNull { it.isNotBlank() } ?: return null

        val country = address.countryName?.takeIf { it.isNotBlank() }
        val region = address.adminArea?.takeIf { it.isNotBlank() && it != city }

        val displayName = listOfNotNull(city, region, country).joinToString(", ")
        val cityName = listOfNotNull(city, country).joinToString(", ")

        return CitySuggestion(
            displayName = displayName,
            cityName = cityName,
            countryName = country.orEmpty(),
            latitude = address.latitude,
            longitude = address.longitude
        )
    }
}
