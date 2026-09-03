package io.github.sulfuro25.salati.data.settings

import kotlinx.serialization.Serializable

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
    val notificationsMuted: Boolean = false,
    val whiteDaysReminder: Boolean = false,
    val silentModeAutomationEnabled: Boolean = false,
    val silentModeMinutesAfterAdhan: Int = 0, // Options: 0, 5, 10, 15 minutes
    val silentModeDurationMinutes: Int = 20, // Options: 15, 20, 30 minutes
    
    // Zakat Parameters
    val zakatGoldPrice: Double = 70.0,      // Default 24k gold price per gram, in zakatCurrencyCode
    val zakatGoldCarat: Int = 24,           // Jewelry purity used for gold valuation (24, 21, 18, 14, 10)
    val zakatNisabGram: Double = 85.0,      // Gold threshold in grams
    val zakatSilverPrice: Double = 0.8,     // Default silver price per gram, in zakatCurrencyCode
    val zakatNisabSilverGram: Double = 595.0, // Silver threshold in grams
    val zakatCurrencyCode: String = "EUR",  // Currency the Zakat amounts are displayed in
    val zakatPricesUpdatedAt: Long = 0L,    // Epoch millis of the last successful price fetch (0 = never)
    val zakatPricesCurrencyCode: String = "", // Currency the stored metal prices were quoted in
    val zakatHawlStartEpochDay: Long? = null, // Date wealth first reached Nisab; due after 354 days
    
    // Theme & Language
    val isDarkMode: Boolean? = null,
    val appLanguageCode: String? = null // null = System default, "en", "ar", "fr", "nl"
)
