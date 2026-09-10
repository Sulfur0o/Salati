package com.sulfuro.salati.core.computation

/** How Isha is derived once Maghrib is known. */
internal sealed interface IshaRule {
    /** Isha begins when the sun is [degrees] below the horizon. */
    data class TwilightAngle(val degrees: Double) : IshaRule

    /** Isha begins a fixed number of minutes after Maghrib, as used in the Gulf. */
    data class FixedInterval(val minutes: Int) : IshaRule

    /**
     * The Moonsighting Committee's seasonal rule, which varies the twilight span with
     * latitude and the distance from the solstice instead of using a single angle.
     */
    data object Seasonal : IshaRule
}

/** How Fajr is derived. */
internal sealed interface FajrRule {
    data class TwilightAngle(val degrees: Double) : FajrRule

    data object Seasonal : FajrRule
}

/**
 * Fixed per-prayer corrections a method applies on top of the astronomy.
 *
 * These are conventions rather than physics - a committee decides that Maghrib is called
 * three minutes after sunset - so they are carried as data instead of being folded into
 * the solar maths.
 */
internal data class MethodTuning(
    val dhuhrMinutes: Int = 0,
    val maghribMinutes: Int = 0
) {
    companion object {
        val NONE = MethodTuning()
    }
}

/**
 * The twilight parameters behind each calculation method Salati offers, keyed by the
 * Aladhan method id so the on-device fallback and the network answer stay in step.
 *
 * @see PrayerRepository.getAladhanMethodId for the settings-string to id mapping.
 */
internal data class PrayerMethodParameters(
    val fajr: FajrRule,
    val isha: IshaRule,
    val tuning: MethodTuning = MethodTuning.NONE
) {
    /**
     * The angle the high-latitude "twilight angle" rule uses to size the night portion.
     * Interval- and season-based rules have no angle of their own, so they borrow the
     * conventional 18 degrees, which is what the reference implementations do.
     */
    val fajrAngleForNightPortion: Double
        get() = (fajr as? FajrRule.TwilightAngle)?.degrees ?: DEFAULT_TWILIGHT_ANGLE

    val ishaAngleForNightPortion: Double
        get() = (isha as? IshaRule.TwilightAngle)?.degrees ?: DEFAULT_TWILIGHT_ANGLE

    companion object {
        private const val DEFAULT_TWILIGHT_ANGLE = 18.0

        private val MUSLIM_WORLD_LEAGUE = PrayerMethodParameters(
            fajr = FajrRule.TwilightAngle(18.0),
            isha = IshaRule.TwilightAngle(17.0)
        )

        /**
         * @param methodId an Aladhan method id.
         * @return the parameters for that method, falling back to Muslim World League
         *   for ids this build does not offer.
         */
        fun forAladhanMethodId(methodId: Int): PrayerMethodParameters = when (methodId) {
            1 -> PrayerMethodParameters(FajrRule.TwilightAngle(18.0), IshaRule.TwilightAngle(18.0))
            2 -> PrayerMethodParameters(FajrRule.TwilightAngle(15.0), IshaRule.TwilightAngle(15.0))
            3 -> MUSLIM_WORLD_LEAGUE
            4 -> PrayerMethodParameters(FajrRule.TwilightAngle(18.5), IshaRule.FixedInterval(90))
            5 -> PrayerMethodParameters(FajrRule.TwilightAngle(19.5), IshaRule.TwilightAngle(17.5))
            9 -> PrayerMethodParameters(FajrRule.TwilightAngle(18.0), IshaRule.TwilightAngle(17.5))
            10 -> PrayerMethodParameters(FajrRule.TwilightAngle(18.0), IshaRule.FixedInterval(90))
            11 -> PrayerMethodParameters(FajrRule.TwilightAngle(20.0), IshaRule.TwilightAngle(18.0))
            15 -> PrayerMethodParameters(FajrRule.Seasonal, IshaRule.Seasonal)
            // Dubai calls Dhuhr and Maghrib three minutes late by convention.
            16 -> PrayerMethodParameters(
                fajr = FajrRule.TwilightAngle(18.2),
                isha = IshaRule.TwilightAngle(18.2),
                tuning = MethodTuning(dhuhrMinutes = 3, maghribMinutes = 3)
            )
            else -> MUSLIM_WORLD_LEAGUE
        }
    }
}

/** How the night is divided when the sun never reaches the twilight angle. */
internal enum class HighLatitudeRule {
    /** Night split in half; Aladhan latitudeAdjustmentMethod 1. */
    MIDDLE_OF_THE_NIGHT,

    /** Fajr and Isha take a seventh of the night; Aladhan latitudeAdjustmentMethod 2. */
    SEVENTH_OF_THE_NIGHT,

    /** Night portion scales with the method's twilight angle; Aladhan method 3. */
    TWILIGHT_ANGLE;

    companion object {
        fun forAladhanId(id: String): HighLatitudeRule = when (id) {
            "1" -> MIDDLE_OF_THE_NIGHT
            "2" -> SEVENTH_OF_THE_NIGHT
            else -> TWILIGHT_ANGLE
        }
    }
}
