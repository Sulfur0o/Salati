package io.github.sulfuro25.salati.core.location

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CoordinateTimezoneLookupTest {
    private val utc = ZoneId.of("UTC")
    private val at = Instant.parse("2026-06-15T12:00:00Z")

    @Test
    fun knownCitiesResolveToTheLocalNamedZoneWhenTheDeviceZoneDoesNotMatch() {
        assertEquals("Europe/Brussels", zone("50.8503", "4.3517"))
        assertEquals("Asia/Riyadh", zone("21.4225", "39.8262")) // Makkah
        assertEquals("Asia/Tokyo", zone("35.6762", "139.6503"))
        assertEquals("Africa/Cairo", zone("30.0444", "31.2357"))
        assertEquals("Europe/Istanbul", zone("41.0082", "28.9784"))
        assertEquals("Asia/Karachi", zone("24.8607", "67.0011"))
        assertEquals("Asia/Dubai", zone("25.2048", "55.2708"))
        assertEquals("America/New_York", zone("40.7128", "-74.0060"))
        assertEquals("America/Los_Angeles", zone("34.0522", "-118.2437"))
        assertEquals("Asia/Singapore", zone("1.3521", "103.8198"))
        assertEquals("Africa/Casablanca", zone("33.5731", "-7.5898"))
        assertEquals("Asia/Jakarta", zone("-6.2088", "106.8456"))
    }

    @Test
    fun matchingDeviceOffsetKeepsTheNamedDeviceZone() {
        val brussels = ZoneId.of("Europe/Brussels")
        assertEquals(
            brussels,
            CoordinateTimezoneLookup.zoneIdFor(50.8503, 4.3517, brussels, at)
        )
    }

    @Test
    fun aStaleDeviceZoneIsNotKeptForADistantCity() {
        val brussels = ZoneId.of("Europe/Brussels")
        assertNotEquals(
            "Europe/Brussels",
            CoordinateTimezoneLookup.zoneIdFor(35.6762, 139.6503, brussels, at).id
        )
        assertEquals(
            "Asia/Tokyo",
            CoordinateTimezoneLookup.zoneIdFor(35.6762, 139.6503, brussels, at).id
        )
    }

    @Test
    fun unknownOceanPointFallsBackToALongitudeOffset() {
        val zone = CoordinateTimezoneLookup.zoneIdFor(0.0, 0.0, utc, at)
        assertEquals("UTC", zone.id)
    }

    private fun zone(lat: String, lng: String): String {
        return CoordinateTimezoneLookup.zoneIdFor(
            latitude = lat.toDouble(),
            longitude = lng.toDouble(),
            deviceZoneId = utc,
            at = at
        ).id
    }
}
