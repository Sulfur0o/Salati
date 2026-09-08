package io.github.sulfuro25.salati.data.settings

import androidx.compose.runtime.Immutable
import io.github.sulfuro25.salati.core.computation.ZakatGoldItem
import io.github.sulfuro25.salati.core.computation.ZakatSilverItem
import kotlinx.serialization.Serializable

/** How prayer times are rendered; [SYSTEM] follows the device's 12/24-hour setting. */
object TimeFormatPreference {
    const val SYSTEM = "SYSTEM"
    const val TWELVE_HOUR = "12H"
    const val TWENTY_FOUR_HOUR = "24H"
}

/**
 * Marked immutable for Compose's benefit, and it genuinely is: every property is a `val`,
 * the two lists are only ever replaced wholesale by `copy`, and instances come from
 * deserialising the settings store rather than being edited in place.
 *
 * Without the annotation Compose sees `List` and infers the whole class unstable, which
 * makes every composable taking a [CalculationSettings] non-skippable - all four settings
 * cards, the Zakat steps, the dashboard - so any recomposition redraws all of them.
 */
@Immutable
@Serializable
data class CalculationSettings(
    val hasCompletedOnboarding: Boolean = false,
    val latitude: Double = 50.8503, // Default fallback coordinates
    val longitude: Double = 4.3517,
    val cityName: String = "Brussels, Belgium",
    val countryName: String = "Belgium",
    val timezoneId: String = "Europe/Brussels",
    val calculationMethod: String = "MUSLIM_WORLD_LEAGUE",
    val madhab: String = "SHAFI",
    val highLatitudeRule: String = "TWILIGHT_ANGLE",
    val hijriOffset: Int = 0,
    val prePrayerMinutes: Int = 10,
    val vibrateEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    // Id of a downloaded adhan recording, or null to use the device notification tone.
    val adhanSoundId: String? = null,
    // Stored alongside the id so Settings can name the choice without refetching the
    // catalogue, which would leave the row blank whenever the user is offline.
    val adhanSoundName: String? = null,
    // The Fajr adhan, kept separate because it is a different call: it carries
    // "as-salatu khayrun min an-nawm", which belongs at dawn and nowhere else. Null means
    // the user has not chosen one, and Fajr falls back to [adhanSoundId].
    val fajrAdhanSoundId: String? = null,
    val fajrAdhanSoundName: String? = null,
    val notificationsMuted: Boolean = false,
    val whiteDaysReminder: Boolean = false,
    val silentModeAutomationEnabled: Boolean = false,
    val silentModeMinutesAfterAdhan: Int = 0, // Options: 0, 5, 10, 15 minutes
    val silentModeDurationMinutes: Int = 20, // Options: 15, 20, 30 minutes
    
    // Zakat Parameters
    val zakatGoldPrice: Double = 70.0,      // Default 24k gold price per gram, in zakatCurrencyCode
    val zakatNisabGram: Double = 85.0,      // Gold threshold in grams
    val zakatSilverPrice: Double = 0.8,     // Default silver price per gram, in zakatCurrencyCode
    val zakatNisabSilverGram: Double = 595.0, // Silver threshold in grams
    val zakatCurrencyCode: String = "EUR",  // Currency the Zakat amounts are displayed in
    val zakatPricesUpdatedAt: Long = 0L,    // Epoch millis of the last successful price fetch (0 = never)
    val zakatPricesCurrencyCode: String = "", // Currency the stored metal prices were quoted in
    val zakatHawlStartEpochDay: Long? = null, // Date wealth first reached Nisab; due after 354 days

    // Zakat walkthrough inputs. Persisted rather than held in UI state because the
    // assessment is revisited once a Hijri year later, and re-entering every piece of
    // jewellery from scratch each time is the main thing that makes the tool tedious.
    val zakatStandard: Int = 0,             // 0 = gold Nisab (85g), 1 = silver Nisab (595g)
    val zakatCashOnHand: Double = 0.0,
    val zakatBankBalance: Double = 0.0,
    val zakatInvestments: Double = 0.0,
    val zakatReceivables: Double = 0.0,
    val zakatLiabilities: Double = 0.0,
    val zakatGoldItems: List<ZakatGoldItem> = emptyList(),
    val zakatSilverItems: List<ZakatSilverItem> = emptyList(),

    // Theme & Language
    val isDarkMode: Boolean? = null,
    val appLanguageCode: String? = null, // null = System default, "en", "ar", "fr", "nl"
    val timeFormat: String = TimeFormatPreference.SYSTEM
)

/** Prayer key the scheduler uses for the dawn prayer; see `AlarmScheduler.addAlarm`. */
private const val FAJR_KEY = "fajr"

/**
 * The recitation to play for one prayer.
 *
 * The Fajr adhan is not the same call as the other four: it adds
 * "as-salatu khayrun min an-nawm" - prayer is better than sleep - which is only said at
 * dawn. Playing a Fajr recording at Asr would announce it four times a day at the wrong
 * time, so the two are stored separately and chosen per prayer here.
 *
 * Falls back to the general choice when no Fajr recording has been picked, which keeps
 * every install that predates this setting behaving exactly as it did.
 */
fun CalculationSettings.adhanSoundIdFor(prayerKey: String): String? {
    return if (prayerKey.equals(FAJR_KEY, ignoreCase = true)) {
        fajrAdhanSoundId ?: adhanSoundId
    } else {
        adhanSoundId
    }
}

fun CalculationSettings.safeZoneId(): java.time.ZoneId {
    return safeZoneId(timezoneId)
}

fun safeZoneId(timezoneId: String?): java.time.ZoneId {
    if (timezoneId.isNullOrBlank()) return java.time.ZoneId.systemDefault()
    return runCatching { java.time.ZoneId.of(timezoneId) }.getOrElse { java.time.ZoneId.systemDefault() }
}

/**
 * Resolves the stored [CalculationSettings.timeFormat] against the device setting so
 * every surface (Daily, Monthly, widget) renders the clock the same way.
 *
 * @param systemUses24Hour what `DateFormat.is24HourFormat` reports for this device.
 */
fun resolveUses24HourClock(timeFormat: String?, systemUses24Hour: Boolean): Boolean {
    return when (timeFormat) {
        TimeFormatPreference.TWELVE_HOUR -> false
        TimeFormatPreference.TWENTY_FOUR_HOUR -> true
        else -> systemUses24Hour
    }
}
