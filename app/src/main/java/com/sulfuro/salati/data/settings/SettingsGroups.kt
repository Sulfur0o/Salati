package com.sulfuro.salati.data.settings

import androidx.compose.runtime.Immutable
import com.sulfuro.salati.core.zakat.ZakatGoldItem
import com.sulfuro.salati.core.zakat.ZakatSilverItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LocationSettings(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val cityName: String = "",
    val countryName: String = "",
    val timezoneId: String = ""
)

@Immutable
@Serializable
data class PrayerMethodSettings(
    val calculationMethod: String = "MUSLIM_WORLD_LEAGUE",
    val madhab: String = "SHAFI",
    val highLatitudeRule: String = "TWILIGHT_ANGLE",
    val hijriOffset: Int = 0
)

@Immutable
@Serializable
data class AlarmPreferences(
    val prePrayerMinutes: Int = 10,
    val vibrateEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val adhanSoundId: String? = null,
    val adhanSoundName: String? = null,
    val fajrAdhanSoundId: String? = null,
    val fajrAdhanSoundName: String? = null,
    val notificationsMuted: Boolean = false,
    val whiteDaysReminder: Boolean = false,
    val silentModeAutomationEnabled: Boolean = false,
    val silentModeMinutesAfterAdhan: Int = 0,
    val silentModeDurationMinutes: Int = 20
)

@Immutable
@Serializable
data class ZakatPreferences(
    @SerialName("zakatGoldPrice") val goldPrice: Double = 70.0,
    @SerialName("zakatNisabGram") val nisabGram: Double = 85.0,
    @SerialName("zakatSilverPrice") val silverPrice: Double = 0.8,
    @SerialName("zakatNisabSilverGram") val nisabSilverGram: Double = 595.0,
    @SerialName("zakatCurrencyCode") val currencyCode: String = "EUR",
    @SerialName("zakatPricesUpdatedAt") val pricesUpdatedAt: Long = 0L,
    @SerialName("zakatPricesCurrencyCode") val pricesCurrencyCode: String = "",
    /**
     * The quote date the rate source reports, e.g. "2026-07-18", as distinct from when the
     * app fetched it. The source republishes daily, so a fetch two minutes ago can carry a
     * price from three days back - and this figure decides a religious obligation, so it
     * is the quote date that belongs on screen.
     */
    @SerialName("zakatPricesRateDate") val pricesRateDate: String = "",
    @SerialName("zakatHawlStartEpochDay") val hawlStartEpochDay: Long? = null,
    @SerialName("zakatStandard") val standard: Int = 0,
    @SerialName("zakatCashOnHand") val cashOnHand: Double = 0.0,
    @SerialName("zakatBankBalance") val bankBalance: Double = 0.0,
    @SerialName("zakatInvestments") val investments: Double = 0.0,
    @SerialName("zakatReceivables") val receivables: Double = 0.0,
    /**
     * Trade goods: stock, merchandise or raw materials held for resale, valued at what
     * they cost rather than what they are hoped to fetch. All four schools agree these
     * are zakatable, and until now a shopkeeper had nowhere to put them.
     */
    @SerialName("zakatBusinessInventory") val businessInventory: Double = 0.0,
    /**
     * Whether the user is declaring gold and silver at all. Null until they have been
     * asked - the metals step opens with the question rather than with an empty list.
     *
     * It is a real question, not a convenience: the Maliki, Shafi'i and Hanbali schools
     * exempt jewellery kept for personal wear, and only the Hanafi school includes it, so
     * a majority-school user with no bullion owes nothing here and should not be made to
     * weigh their rings to find that out.
     */
    @SerialName("zakatDeclaresMetals") val declaresMetals: Boolean? = null,
    @SerialName("zakatLiabilities") val liabilities: Double = 0.0,
    @SerialName("zakatGoldItems") val goldItems: List<ZakatGoldItem> = emptyList(),
    @SerialName("zakatSilverItems") val silverItems: List<ZakatSilverItem> = emptyList()
)

@Immutable
@Serializable
data class AppearanceSettings(
    val isDarkMode: Boolean? = null,
    val appLanguageCode: String? = null,
    val timeFormat: String = TimeFormatPreference.SYSTEM
)
