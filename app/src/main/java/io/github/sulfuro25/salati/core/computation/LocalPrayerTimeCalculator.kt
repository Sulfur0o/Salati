package io.github.sulfuro25.salati.core.computation

import io.github.sulfuro25.salati.core.computation.SolarPosition.dArcCos
import io.github.sulfuro25.salati.core.computation.SolarPosition.dArcCot
import io.github.sulfuro25.salati.core.computation.SolarPosition.dCos
import io.github.sulfuro25.salati.core.computation.SolarPosition.dSin
import io.github.sulfuro25.salati.core.computation.SolarPosition.dTan
import io.github.sulfuro25.salati.core.computation.SolarPosition.fixHour
import java.time.LocalDate
import java.time.LocalTime
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Computes prayer times on the device, so the app keeps working when Aladhan cannot be
 * reached.
 *
 * This is deliberately a *fallback*, never the primary source: [PrayerRepository] reaches
 * for it only after the cache misses and the network fails. Without it, a user who is
 * roaming, offline, or opening the app on the first of an uncached month gets no prayer
 * times at all - and, worse, no scheduled alarms either, because the alarm scheduler
 * feeds off the same repository. Silently failing to notify is the worst thing a prayer
 * app can do.
 *
 * The output is shaped as [AladhanDayData] rather than as a distinct type, so every
 * consumer downstream - the mapper, both screens, the widget and the alarm scheduler -
 * handles it without needing to know where it came from.
 */
object LocalPrayerTimeCalculator {

    /** Refraction and horizon dip at sunrise and sunset, in degrees below the horizon. */
    private const val HORIZON_ANGLE = 0.833

    /** Days from 21 December back to 1 January, for the seasonal twilight rule. */
    private const val NORTHERN_SOLSTICE_OFFSET = 10

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ROOT)

    /**
     * Each time is found by evaluating the sun at an estimate of when that prayer falls,
     * which makes this a fixed-point iteration that could be run to convergence.
     *
     * It deliberately is not. The reference implementation this and Aladhan both descend
     * from stops after one pass, and measuring against 27,300 days of live Aladhan output
     * showed a second pass moving *away* from the service on 327 more days than it moved
     * towards it - the extra passes converge on a slightly different answer than the one
     * the network returns. Since this class exists so the app keeps working offline, an
     * answer that matches what the user saw yesterday online is worth more than one that
     * is thirty seconds closer to the true solar position.
     */
    private const val REFINEMENT_PASSES = 1

    /** Where the iteration starts, in hours since local midnight. */
    private val INITIAL_GUESS = DayTimes(
        fajr = 5.0,
        sunrise = 6.0,
        dhuhr = 12.0,
        asr = 13.0,
        sunset = 18.0,
        maghrib = 18.0,
        isha = 18.0
    )

    /** Clock times for one day, as hours since local midnight. */
    internal data class DayTimes(
        val fajr: Double,
        val sunrise: Double,
        val dhuhr: Double,
        val asr: Double,
        val sunset: Double,
        val maghrib: Double,
        val isha: Double
    )

    /**
     * Computes every day of the requested month.
     *
     * @param zoneId the zone the times are expressed in, which is the prayer timezone
     *   configured in settings rather than the device default.
     * @return one entry per day on which the sun actually rises and sets. Days inside a
     *   polar day or night are omitted rather than guessed at, so callers show their
     *   usual "no times" state instead of a fabricated one.
     */
    fun calculateMonth(request: PrayerMonthRequest, zoneId: ZoneId): List<AladhanDayData> {
        val yearMonth = runCatching { YearMonth.of(request.year, request.month) }.getOrNull()
            ?: return emptyList()
        val parameters = PrayerMethodParameters.forAladhanMethodId(request.methodId)
        val highLatitudeRule = HighLatitudeRule.forAladhanId(request.latitudeAdjustmentId)
        val shadowFactor = if (request.schoolId == HANAFI_SCHOOL_ID) 2.0 else 1.0

        return (1..yearMonth.lengthOfMonth()).mapNotNull { dayOfMonth ->
            val date = yearMonth.atDay(dayOfMonth)
            val times = calculateDay(
                date = date,
                latitude = request.latitude,
                longitude = request.longitude,
                zoneId = zoneId,
                parameters = parameters,
                shadowFactor = shadowFactor,
                highLatitudeRule = highLatitudeRule
            ) ?: return@mapNotNull null
            toDayData(date, times, zoneId)
        }
    }

    /**
     * @return the day's times, or null when the sun does not cross the horizon and there
     *   is no sunrise or sunset to anchor the rest of the day to.
     */
    internal fun calculateDay(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId,
        parameters: PrayerMethodParameters,
        shadowFactor: Double,
        highLatitudeRule: HighLatitudeRule
    ): DayTimes? {
        // Anchoring on local solar midnight keeps the estimate centred on the day being
        // computed rather than on the Greenwich day.
        val julianDate = SolarPosition.julianDay(date) - longitude / (15.0 * 24.0)

        var solar = INITIAL_GUESS
        repeat(REFINEMENT_PASSES) {
            solar = refine(solar, julianDate, latitude, date, parameters, shadowFactor)
        }
        if (solar.sunrise.isNaN() || solar.sunset.isNaN()) return null

        // Shift from mean solar time at this longitude onto the wall clock of the zone.
        val shift = zoneOffsetHours(date, zoneId) - longitude / 15.0
        var times = DayTimes(
            fajr = solar.fajr + shift,
            sunrise = solar.sunrise + shift,
            dhuhr = solar.dhuhr + shift,
            asr = solar.asr + shift,
            sunset = solar.sunset + shift,
            maghrib = solar.sunset + shift,
            isha = solar.isha + shift
        )

        times = adjustForHighLatitude(times, parameters, highLatitudeRule)

        // Conventional offsets land before Isha is derived, so an interval-based Isha
        // counts from the Maghrib the user is actually shown.
        times = times.copy(
            dhuhr = times.dhuhr + parameters.tuning.dhuhrMinutes / 60.0,
            maghrib = times.maghrib + parameters.tuning.maghribMinutes / 60.0
        )

        // An interval-based Isha hangs off the finished Maghrib, so it is applied last
        // and is never subject to the high-latitude clamp.
        val ishaRule = parameters.isha
        if (ishaRule is IshaRule.FixedInterval) {
            times = times.copy(isha = times.maghrib + ishaRule.minutes / 60.0)
        }

        return times
    }

    /**
     * Runs one pass of the fixed-point iteration, evaluating the sun at the times
     * [previous] predicted. Values that came back NaN - a twilight angle the sun never
     * reaches - fall back to the initial guess so the remaining times still get a sane
     * evaluation point; the high-latitude clamp deals with them afterwards.
     */
    private fun refine(
        previous: DayTimes,
        julianDate: Double,
        latitude: Double,
        date: LocalDate,
        parameters: PrayerMethodParameters,
        shadowFactor: Double
    ): DayTimes {
        fun at(value: Double, fallback: Double) = if (value.isFinite()) value else fallback

        val sunrise = sunAngleTime(
            julianDate, latitude, HORIZON_ANGLE,
            at(previous.sunrise, INITIAL_GUESS.sunrise), afterNoon = false
        )
        val sunset = sunAngleTime(
            julianDate, latitude, HORIZON_ANGLE,
            at(previous.sunset, INITIAL_GUESS.sunset), afterNoon = true
        )
        val fajr = when (val rule = parameters.fajr) {
            is FajrRule.TwilightAngle -> sunAngleTime(
                julianDate, latitude, rule.degrees,
                at(previous.fajr, INITIAL_GUESS.fajr), afterNoon = false
            )
            FajrRule.Seasonal -> sunrise - seasonalMorningTwilightHours(latitude, date)
        }
        val isha = when (val rule = parameters.isha) {
            is IshaRule.TwilightAngle -> sunAngleTime(
                julianDate, latitude, rule.degrees,
                at(previous.isha, INITIAL_GUESS.isha), afterNoon = true
            )
            IshaRule.Seasonal -> sunset + seasonalEveningTwilightHours(latitude, date)
            is IshaRule.FixedInterval -> Double.NaN // Derived from Maghrib once it is final.
        }

        return DayTimes(
            fajr = fajr,
            sunrise = sunrise,
            dhuhr = midDay(julianDate, at(previous.dhuhr, INITIAL_GUESS.dhuhr)),
            asr = asrTime(
                julianDate, latitude, shadowFactor,
                at(previous.asr, INITIAL_GUESS.asr)
            ),
            sunset = sunset,
            maghrib = sunset,
            isha = isha
        )
    }

    private fun midDay(julianDate: Double, guessHours: Double): Double {
        val equationOfTime = SolarPosition.sunAt(julianDate + guessHours / 24.0).equationOfTime
        return fixHour(12.0 - equationOfTime)
    }

    /**
     * @param angle degrees below the horizon.
     * @return the hour at which the sun reaches [angle], or NaN when it never does on
     *   this day at this latitude.
     */
    private fun sunAngleTime(
        julianDate: Double,
        latitude: Double,
        angle: Double,
        guessHours: Double,
        afterNoon: Boolean
    ): Double {
        val declination = SolarPosition.sunAt(julianDate + guessHours / 24.0).declination
        val noon = midDay(julianDate, guessHours)
        val numerator = -dSin(angle) - dSin(declination) * dSin(latitude)
        val denominator = dCos(declination) * dCos(latitude)
        val ratio = numerator / denominator
        if (!ratio.isFinite() || ratio > 1.0 || ratio < -1.0) return Double.NaN
        val hourAngle = dArcCos(ratio) / 15.0
        return if (afterNoon) noon + hourAngle else noon - hourAngle
    }

    private fun asrTime(
        julianDate: Double,
        latitude: Double,
        shadowFactor: Double,
        guessHours: Double
    ): Double {
        val declination = SolarPosition.sunAt(julianDate + guessHours / 24.0).declination
        val angle = -dArcCot(shadowFactor + dTan(abs(latitude - declination)))
        return sunAngleTime(julianDate, latitude, angle, guessHours, afterNoon = true)
    }

    /**
     * Clamps Fajr and Isha to a portion of the night when the sun never reaches the
     * twilight angle, which happens every summer above roughly 48 degrees of latitude
     * and is exactly the case Brussels, the default location, runs into.
     */
    private fun adjustForHighLatitude(
        times: DayTimes,
        parameters: PrayerMethodParameters,
        rule: HighLatitudeRule
    ): DayTimes {
        val nightLength = fixHour(times.sunrise - times.sunset)

        fun portion(angle: Double): Double = when (rule) {
            HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> nightLength / 2.0
            HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> nightLength / 7.0
            HighLatitudeRule.TWILIGHT_ANGLE -> nightLength * angle / 60.0
        }

        val fajrLimit = portion(parameters.fajrAngleForNightPortion)
        val adjustedFajr =
            if (times.fajr.isNaN() || fixHour(times.sunrise - times.fajr) > fajrLimit) {
                times.sunrise - fajrLimit
            } else {
                times.fajr
            }

        val ishaLimit = portion(parameters.ishaAngleForNightPortion)
        val adjustedIsha =
            if (times.isha.isNaN() || fixHour(times.isha - times.sunset) > ishaLimit) {
                times.sunset + ishaLimit
            } else {
                times.isha
            }

        return times.copy(fajr = adjustedFajr, isha = adjustedIsha)
    }

    /**
     * The Moonsighting Committee varies the twilight span with latitude and the distance
     * from the solstice instead of fixing an angle, which is why it needs its own branch.
     */
    private fun seasonalMorningTwilightHours(latitude: Double, date: LocalDate): Double {
        val absLatitude = abs(latitude)
        return seasonalAdjustmentMinutes(
            date = date,
            latitude = latitude,
            atSolstice = 75 + 28.65 / 55.0 * absLatitude,
            atEquinoxSpring = 75 + 19.44 / 55.0 * absLatitude,
            atMidSummer = 75 + 32.74 / 55.0 * absLatitude,
            atSolsticeSummer = 75 + 48.10 / 55.0 * absLatitude
        ) / 60.0
    }

    private fun seasonalEveningTwilightHours(latitude: Double, date: LocalDate): Double {
        val absLatitude = abs(latitude)
        return seasonalAdjustmentMinutes(
            date = date,
            latitude = latitude,
            atSolstice = 75 + 25.60 / 55.0 * absLatitude,
            atEquinoxSpring = 75 + 2.050 / 55.0 * absLatitude,
            atMidSummer = 75 - 9.210 / 55.0 * absLatitude,
            atSolsticeSummer = 75 + 6.140 / 55.0 * absLatitude
        ) / 60.0
    }

    /** Interpolates between the four seasonal anchor values of the Moonsighting rule. */
    private fun seasonalAdjustmentMinutes(
        date: LocalDate,
        latitude: Double,
        atSolstice: Double,
        atEquinoxSpring: Double,
        atMidSummer: Double,
        atSolsticeSummer: Double
    ): Double {
        val elapsed = daysSinceSolstice(date, latitude)
        return when {
            elapsed < 91 ->
                atSolstice + (atEquinoxSpring - atSolstice) / 91.0 * elapsed
            elapsed < 137 ->
                atEquinoxSpring + (atMidSummer - atEquinoxSpring) / 46.0 * (elapsed - 91)
            elapsed < 183 ->
                atMidSummer + (atSolsticeSummer - atMidSummer) / 46.0 * (elapsed - 137)
            elapsed < 229 ->
                atSolsticeSummer + (atMidSummer - atSolsticeSummer) / 46.0 * (elapsed - 183)
            elapsed < 275 ->
                atMidSummer + (atEquinoxSpring - atMidSummer) / 46.0 * (elapsed - 229)
            else ->
                atEquinoxSpring + (atSolstice - atEquinoxSpring) / 46.0 * (elapsed - 275)
        }
    }

    /** Counts from the winter solstice of whichever hemisphere [latitude] sits in. */
    internal fun daysSinceSolstice(date: LocalDate, latitude: Double): Int {
        val dayOfYear = date.dayOfYear
        val isLeapYear = Year.isLeap(date.year.toLong())
        val daysInYear = if (isLeapYear) 366 else 365
        return if (latitude >= 0) {
            val northern = dayOfYear + NORTHERN_SOLSTICE_OFFSET
            if (northern >= daysInYear) northern - daysInYear else northern
        } else {
            val southernOffset = if (isLeapYear) 173 else 172
            val southern = dayOfYear - southernOffset
            if (southern < 0) southern + daysInYear else southern
        }
    }

    private fun zoneOffsetHours(date: LocalDate, zoneId: ZoneId): Double {
        // Sampled at local noon so a transition at midnight cannot hand back the offset
        // belonging to the neighbouring day.
        return zoneId.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
    }

    private fun toDayData(date: LocalDate, times: DayTimes, zoneId: ZoneId): AladhanDayData {
        val nightLength = fixHour(times.sunrise - times.sunset)
        val midnight = times.sunset + nightLength / 2.0
        val lastThird = times.sunset + nightLength * 2.0 / 3.0
        val hijriDate = HijrahDate.from(date)
        val formattedDate = date.format(dateFormatter)

        return AladhanDayData(
            timings = AladhanTimings(
                Fajr = formatHour(times.fajr),
                Sunrise = formatHour(times.sunrise),
                Dhuhr = formatHour(times.dhuhr),
                Asr = formatHour(times.asr),
                Sunset = formatHour(times.sunset),
                Maghrib = formatHour(times.maghrib),
                Isha = formatHour(times.isha),
                Midnight = formatHour(midnight),
                Lastthird = formatHour(lastThird)
            ),
            date = AladhanDate(
                readable = formattedDate,
                timestamp = date.atStartOfDay(zoneId).toEpochSecond().toString(),
                gregorian = AladhanGregorianDate(
                    date = formattedDate,
                    day = date.dayOfMonth.toString(),
                    month = AladhanMonth(date.monthValue),
                    year = date.year.toString()
                ),
                hijri = AladhanHijriDate(
                    day = hijriDate.get(ChronoField.DAY_OF_MONTH).toString(),
                    month = AladhanMonth(hijriDate.get(ChronoField.MONTH_OF_YEAR)),
                    year = hijriDate.get(ChronoField.YEAR).toString()
                )
            ),
            meta = AladhanMeta(timezone = zoneId.id)
        )
    }

    /** Rounds to the nearest minute, which is how Aladhan renders its own timings. */
    internal fun formatHour(hours: Double): String {
        val minuteOfDay = (fixHour(hours) * 60.0).roundToInt() % MINUTES_PER_DAY
        return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(timeFormatter)
    }

    private const val MINUTES_PER_DAY = 24 * 60
    private const val HANAFI_SCHOOL_ID = 1
}
