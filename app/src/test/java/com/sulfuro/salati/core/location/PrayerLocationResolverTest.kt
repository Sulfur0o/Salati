package com.sulfuro.salati.core.location

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PrayerLocationResolverTest {
    private val utc = ZoneId.of("UTC")
    private val at = Instant.parse("2026-06-15T12:00:00Z")

    @Test
    fun networkTimezoneWinsWhenAladhanAnswered() {
        assertEquals(
            "Asia/Tokyo",
            PrayerLocationResolver.resolveTimezoneId(
                latitude = 35.6762,
                longitude = 139.6503,
                networkTimezoneId = "Asia/Tokyo",
                deviceZoneId = ZoneId.of("Europe/Brussels"),
                at = at
            )
        )
    }

    @Test
    fun blankOrGarbageNetworkTimezoneIsIgnored() {
        assertEquals(
            "Asia/Tokyo",
            PrayerLocationResolver.resolveTimezoneId(
                latitude = 35.6762,
                longitude = 139.6503,
                networkTimezoneId = "",
                deviceZoneId = utc,
                at = at
            )
        )
        assertEquals(
            "Asia/Tokyo",
            PrayerLocationResolver.resolveTimezoneId(
                latitude = 35.6762,
                longitude = 139.6503,
                networkTimezoneId = "Not/A_Zone",
                deviceZoneId = utc,
                at = at
            )
        )
    }

    @Test
    fun offlineLookupUsesTheNewCoordinatesNotThePreviousCity() {
        val resolved = PrayerLocationResolver.resolveTimezoneId(
            latitude = 35.6762,
            longitude = 139.6503,
            networkTimezoneId = null,
            deviceZoneId = ZoneId.of("Europe/Brussels"),
            at = at
        )
        assertEquals("Asia/Tokyo", resolved)
        assertNotEquals("Europe/Brussels", resolved)
    }
}
