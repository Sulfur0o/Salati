package io.github.sulfuro25.salati.core.computation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaCalculatorTest {

    /**
     * Reference bearings are the widely published Qibla directions for these cities.
     * A 1.5 degree tolerance covers rounding differences between published sources.
     */
    @Test
    fun bearingsMatchPublishedQiblaDirectionsForKnownCities() {
        assertEquals(123.5, QiblaCalculator.bearingToKaaba(50.8503, 4.3517), 1.5)   // Brussels
        assertEquals(93.7, QiblaCalculator.bearingToKaaba(33.5731, -7.5898), 1.5)   // Casablanca
        assertEquals(119.0, QiblaCalculator.bearingToKaaba(51.5074, -0.1278), 1.5)  // London
        assertEquals(58.5, QiblaCalculator.bearingToKaaba(40.7128, -74.0060), 1.5)  // New York
        assertEquals(295.2, QiblaCalculator.bearingToKaaba(-6.2088, 106.8456), 1.5) // Jakarta
        assertEquals(266.6, QiblaCalculator.bearingToKaaba(28.6139, 77.2090), 1.5)  // New Delhi
        assertEquals(151.6, QiblaCalculator.bearingToKaaba(41.0082, 28.9784), 1.5)  // Istanbul
    }

    @Test
    fun bearingFromDueNorthOfTheKaabaPointsSouth() {
        val bearing = QiblaCalculator.bearingToKaaba(
            QiblaCalculator.KAABA_LATITUDE + 10.0,
            QiblaCalculator.KAABA_LONGITUDE
        )
        assertEquals(180.0, bearing, 0.001)
    }

    @Test
    fun bearingFromDueSouthOfTheKaabaPointsNorth() {
        val bearing = QiblaCalculator.bearingToKaaba(
            QiblaCalculator.KAABA_LATITUDE - 10.0,
            QiblaCalculator.KAABA_LONGITUDE
        )
        assertEquals(0.0, bearing, 0.001)
    }

    @Test
    fun bearingIsAlwaysNormalizedIntoZeroTo360() {
        val samples = listOf(
            -89.0 to -179.0,
            89.0 to 179.0,
            0.0 to 0.0,
            -33.8688 to 151.2093,
            60.0 to -150.0
        )
        samples.forEach { (lat, lon) ->
            val bearing = QiblaCalculator.bearingToKaaba(lat, lon)
            assertTrue("Bearing $bearing out of range for $lat,$lon", bearing >= 0.0 && bearing < 360.0)
        }
    }

    @Test
    fun normalizeWrapsNegativeAndOversizedAngles() {
        assertEquals(350.0, QiblaCalculator.normalizeDegrees(-10.0), 0.001)
        assertEquals(10.0, QiblaCalculator.normalizeDegrees(370.0), 0.001)
        assertEquals(0.0, QiblaCalculator.normalizeDegrees(360.0), 0.001)
        assertEquals(180.0, QiblaCalculator.normalizeDegrees(-180.0), 0.001)
    }

    @Test
    fun shortestRotationTakesTheNearWayAroundTheDial() {
        // Crossing north must rotate 20 degrees, not 340 the other way.
        assertEquals(20f, QiblaCalculator.shortestRotation(350f, 10f), 0.001f)
        assertEquals(-20f, QiblaCalculator.shortestRotation(10f, 350f), 0.001f)
        assertEquals(0f, QiblaCalculator.shortestRotation(90f, 90f), 0.001f)
        assertTrue(kotlin.math.abs(QiblaCalculator.shortestRotation(0f, 180f)) == 180f)
    }

    @Test
    fun compassPointsLabelTheSixteenSectors() {
        assertEquals("N", QiblaCalculator.compassPointFor(0.0))
        assertEquals("N", QiblaCalculator.compassPointFor(359.0))
        assertEquals("NE", QiblaCalculator.compassPointFor(45.0))
        assertEquals("E", QiblaCalculator.compassPointFor(90.0))
        assertEquals("SE", QiblaCalculator.compassPointFor(127.1))
        assertEquals("S", QiblaCalculator.compassPointFor(180.0))
        assertEquals("W", QiblaCalculator.compassPointFor(270.0))
        assertEquals("NW", QiblaCalculator.compassPointFor(315.0))
    }

    @Test
    fun alignmentUsesShortestAngleSoItWorksAcrossNorth() {
        assertTrue(QiblaCalculator.isAligned(deviceHeading = 358f, qiblaBearing = 2.0))
        assertTrue(QiblaCalculator.isAligned(deviceHeading = 2f, qiblaBearing = 358.0))
        assertFalse(QiblaCalculator.isAligned(deviceHeading = 340f, qiblaBearing = 2.0))
        assertTrue(QiblaCalculator.isAligned(deviceHeading = 127f, qiblaBearing = 127.1))
        assertFalse(QiblaCalculator.isAligned(deviceHeading = 100f, qiblaBearing = 127.1))
    }
}
