package com.sulfuro.salati.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSettingsTest {
    @Test
    fun freshInstallHasNoPrayerLocation() {
        val fresh = CalculationSettings()
        assertEquals("", fresh.location.cityName)
        assertEquals("", fresh.location.timezoneId)
        assertEquals(0.0, fresh.location.latitude, 0.0)
        assertEquals(0.0, fresh.location.longitude, 0.0)
        assertFalse(fresh.hasConfiguredLocation())
    }

    @Test
    fun aNamedCityCountsAsConfigured() {
        val set = CalculationSettings(
            location = LocationSettings(
                cityName = "Istanbul, Türkiye",
                countryName = "Türkiye",
                latitude = 41.0082,
                longitude = 28.9784,
                timezoneId = "Europe/Istanbul"
            )
        )
        assertTrue(set.hasConfiguredLocation())
    }

    @Test
    fun legacyCoordinatesWithoutACityLabelStillCount() {
        val legacy = CalculationSettings(location = LocationSettings(cityName = "", latitude = 50.8503, longitude = 4.3517, timezoneId = "Europe/Brussels"))
        assertTrue(legacy.hasConfiguredLocation())
    }
}
