package io.github.sulfuro25.salati.core.location

import android.location.Address
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The geocoder itself is a platform service, so what is pinned here is the reduction
 * step Salati owns: turning a loosely populated [Address] into the two labels the UI
 * shows and the coordinates settings persist, without ever surfacing a raw lat/lng.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class CityGeocoderTest {

    private fun address(
        latitude: Double? = 50.8503,
        longitude: Double? = 4.3517,
        locality: String? = null,
        subAdminArea: String? = null,
        adminArea: String? = null,
        featureName: String? = null,
        countryName: String? = null
    ): Address = Address(Locale.ENGLISH).apply {
        latitude?.let { setLatitude(it) }
        longitude?.let { setLongitude(it) }
        this.locality = locality
        this.subAdminArea = subAdminArea
        this.adminArea = adminArea
        this.featureName = featureName
        this.countryName = countryName
    }

    @Test
    fun buildsDisplayAndStoredNamesFromAFullyPopulatedAddress() {
        val suggestion = CityGeocoder.toSuggestion(
            address(
                locality = "Brussels",
                adminArea = "Brussels-Capital",
                countryName = "Belgium"
            )
        )

        assertEquals("Brussels, Brussels-Capital, Belgium", suggestion?.displayName)
        // Stored name stays short: it is shown on the dashboard header and the widget.
        assertEquals("Brussels, Belgium", suggestion?.cityName)
        assertEquals("Belgium", suggestion?.countryName)
        assertEquals(50.8503, suggestion!!.latitude, 0.0001)
        assertEquals(4.3517, suggestion.longitude, 0.0001)
    }

    @Test
    fun fallsBackThroughAdministrativeFieldsWhenLocalityIsMissing() {
        // Small towns and regions frequently come back with a null locality.
        val fromSubAdmin = CityGeocoder.toSuggestion(
            address(locality = null, subAdminArea = "Nador", countryName = "Morocco")
        )
        assertEquals("Nador, Morocco", fromSubAdmin?.cityName)

        val fromFeature = CityGeocoder.toSuggestion(
            address(locality = null, subAdminArea = null, adminArea = null, featureName = "Tangier")
        )
        assertEquals("Tangier", fromFeature?.cityName)
    }

    @Test
    fun doesNotRepeatTheRegionWhenItEqualsTheCity() {
        val suggestion = CityGeocoder.toSuggestion(
            address(locality = "Singapore", adminArea = "Singapore", countryName = "Singapore")
        )
        assertEquals("Singapore, Singapore", suggestion?.displayName)
    }

    @Test
    fun rejectsAddressesThatCannotAnchorPrayerTimes() {
        // No coordinates means no prayer times, so the row must never be offered.
        assertNull(CityGeocoder.toSuggestion(address(latitude = null, longitude = null, locality = "Nowhere")))
        // No usable name means nothing to show in the list.
        assertNull(CityGeocoder.toSuggestion(address(locality = "  ", countryName = "Belgium")))
    }

    @Test
    fun blankQueriesNeverReachTheGeocoder() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        // Below the minimum length the search short-circuits rather than matching half the world.
        assertEquals(CitySearchResult.NoMatches, CityGeocoder.search(context, ""))
        assertEquals(CitySearchResult.NoMatches, CityGeocoder.search(context, " b "))
    }
}
