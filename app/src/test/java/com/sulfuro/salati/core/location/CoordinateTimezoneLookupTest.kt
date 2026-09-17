package com.sulfuro.salati.core.location

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateTimezoneLookupTest {
    private val utc = ZoneId.of("UTC")
    private val at = Instant.parse("2026-06-15T12:00:00Z")
    private val january = Instant.parse("2026-01-15T12:00:00Z")

    /** Torshavn: no box contains it, and Europe/London's is the nearest. */
    private val FAROES_LAT = 62.01
    private val FAROES_LNG = -6.77

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

    /**
     * The Faroes sit in no box. They keep London's clock exactly - UTC in winter, an hour
     * ahead in summer - and the old answer was a bare UTC+0 that never moved, so every
     * prayer time there was an hour early from late March to late October.
     */
    @Test
    fun aPointBesideABoxBorrowsItsNeighboursClockChanges() {
        val zone = CoordinateTimezoneLookup.fallbackZone(FAROES_LAT, FAROES_LNG, at)

        assertEquals("Europe/London", zone.id)
        assertEquals(ZoneOffset.ofHours(0), zone.rules.getOffset(january))
        assertEquals(ZoneOffset.ofHours(1), zone.rules.getOffset(at))
    }

    /**
     * Yakutsk's nearest box is Shanghai, eight degrees south and on one offset all year.
     * Daylight saving is the only thing a named zone has that a number does not, so a
     * neighbour without it buys nothing and brings a border - and the longitude offset,
     * UTC+9, is the closer guess anyway.
     */
    @Test
    fun aNeighbourThatNeverMovesItsClocksIsNotWorthTheBorderItBrings() {
        val zone = CoordinateTimezoneLookup.fallbackZone(62.03, 129.73, at)

        assertTrue(zone.rules.isFixedOffset)
        assertEquals(ZoneOffset.ofHours(9), zone.rules.getOffset(at))
    }

    /** Mid-Pacific: the nearest box is two thousand kilometres off and proves nothing. */
    @Test
    fun aPointWithNoNeighbourWithinReachKeepsTheLongitudeOffset() {
        assertNull(CoordinateTimezoneLookup.nearestBoxZone(0.0, -140.0))

        val zone = CoordinateTimezoneLookup.fallbackZone(0.0, -140.0, at)
        assertTrue(zone.rules.isFixedOffset)
        assertEquals(ZoneOffset.ofHours(-9), zone.rules.getOffset(at))
    }

    /** A box that contains the point still wins; the neighbour search is the fallback. */
    @Test
    fun containmentIsStillPreferredToProximity() {
        assertEquals("Europe/Brussels", CoordinateTimezoneLookup.lookupBox(50.8503, 4.3517)?.id)
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
