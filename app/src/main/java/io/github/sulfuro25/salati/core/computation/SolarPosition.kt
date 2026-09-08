package io.github.sulfuro25.salati.core.computation

import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Low-precision solar ephemeris, accurate to well under a minute of prayer time for the
 * years this app can plausibly be used in.
 *
 * The formulae are the ones published by the US Naval Observatory in the Astronomical
 * Almanac and used by the reference PrayTimes implementation, which is also what the
 * Aladhan service computes from. Keeping the same source means the on-device fallback
 * lands on the same minute as the network answer instead of drifting a minute or two
 * away and making the two look like they disagree.
 */
internal object SolarPosition {

    private const val DEGREES = Math.PI / 180.0

    data class Sun(
        /** Solar declination in degrees. */
        val declination: Double,
        /** Equation of time in hours. */
        val equationOfTime: Double
    )

    /**
     * @param julianDay Julian day at 00:00 Universal Time, optionally offset by a
     *   fraction of a day to evaluate the sun at a particular moment.
     */
    fun sunAt(julianDay: Double): Sun {
        val daysSinceEpoch = julianDay - 2451545.0
        val meanAnomaly = fixAngle(357.529 + 0.98560028 * daysSinceEpoch)
        val meanLongitude = fixAngle(280.459 + 0.98564736 * daysSinceEpoch)
        val eclipticLongitude = fixAngle(
            meanLongitude + 1.915 * dSin(meanAnomaly) + 0.020 * dSin(2 * meanAnomaly)
        )
        val obliquity = 23.439 - 0.00000036 * daysSinceEpoch
        val rightAscension = fixHour(
            dArcTan2(dCos(obliquity) * dSin(eclipticLongitude), dCos(eclipticLongitude)) / 15.0
        )
        return Sun(
            declination = dArcSin(dSin(obliquity) * dSin(eclipticLongitude)),
            equationOfTime = meanLongitude / 15.0 - rightAscension
        )
    }

    /** Julian day at 00:00 UT on [date], using the Gregorian calendar. */
    fun julianDay(date: LocalDate): Double {
        var year = date.year
        var month = date.monthValue
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val century = floor(year / 100.0)
        val gregorianCorrection = 2 - century + floor(century / 4.0)
        return floor(365.25 * (year + 4716)) +
            floor(30.6001 * (month + 1)) +
            date.dayOfMonth + gregorianCorrection - 1524.5
    }

    fun dSin(degrees: Double): Double = sin(degrees * DEGREES)

    fun dCos(degrees: Double): Double = cos(degrees * DEGREES)

    fun dTan(degrees: Double): Double = tan(degrees * DEGREES)

    fun dArcSin(value: Double): Double = asin(value) / DEGREES

    fun dArcCos(value: Double): Double = acos(value) / DEGREES

    fun dArcTan2(y: Double, x: Double): Double = atan2(y, x) / DEGREES

    /** Inverse cotangent in degrees, used for the Asr shadow angle. */
    fun dArcCot(value: Double): Double = atan2(1.0, value) / DEGREES

    fun fixAngle(value: Double): Double = wrap(value, 360.0)

    fun fixHour(value: Double): Double = wrap(value, 24.0)

    private fun wrap(value: Double, range: Double): Double {
        if (!value.isFinite()) return value
        val wrapped = value - range * floor(value / range)
        return if (wrapped < 0) wrapped + range else wrapped
    }
}
