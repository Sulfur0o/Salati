package io.github.sulfuro25.salati.core.computation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs

/**
 * Direction of the Kaaba from an arbitrary point on Earth.
 *
 * The Qibla is the great-circle (shortest path) direction, which is why it can differ
 * noticeably from the straight line drawn on a flat map projection.
 */
object QiblaCalculator {

    /** Kaaba, Masjid al-Haram, Mecca. */
    const val KAABA_LATITUDE = 21.4224779
    const val KAABA_LONGITUDE = 39.8251832

    /**
     * Initial great-circle bearing from ([latitude], [longitude]) to the Kaaba,
     * in degrees clockwise from **true** north, normalised to [0, 360).
     */
    fun bearingToKaaba(latitude: Double, longitude: Double): Double {
        val startLatRad = Math.toRadians(latitude)
        val kaabaLatRad = Math.toRadians(KAABA_LATITUDE)
        val deltaLonRad = Math.toRadians(KAABA_LONGITUDE - longitude)

        val y = sin(deltaLonRad) * cos(kaabaLatRad)
        val x = cos(startLatRad) * sin(kaabaLatRad) -
            sin(startLatRad) * cos(kaabaLatRad) * cos(deltaLonRad)

        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    /** Wraps any angle into [0, 360). */
    fun normalizeDegrees(degrees: Double): Double {
        val wrapped = degrees % 360.0
        return if (wrapped < 0) wrapped + 360.0 else wrapped
    }

    /**
     * Smallest signed rotation from [from] to [to], in (-180, 180].
     * Used so the needle animates the short way around instead of spinning 359°.
     */
    fun shortestRotation(from: Float, to: Float): Float {
        var difference = (to - from) % 360f
        if (difference > 180f) difference -= 360f
        if (difference <= -180f) difference += 360f
        return difference
    }

    /** 16-point compass abbreviation for a bearing, e.g. 127° -> "SE". */
    fun compassPointFor(bearingDegrees: Double): String {
        val points = listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        )
        val normalized = normalizeDegrees(bearingDegrees)
        val index = Math.round(normalized / 22.5).toInt() % points.size
        return points[index]
    }

    /**
     * True when the device is pointing close enough to the Qibla to be considered aligned.
     * [toleranceDegrees] is deliberately generous: consumer magnetometers are rarely
     * accurate to better than a few degrees, so a tighter claim would be false precision.
     */
    fun isAligned(
        deviceHeading: Float,
        qiblaBearing: Double,
        toleranceDegrees: Float = 5f
    ): Boolean {
        return abs(shortestRotation(deviceHeading, qiblaBearing.toFloat())) <= toleranceDegrees
    }
}
