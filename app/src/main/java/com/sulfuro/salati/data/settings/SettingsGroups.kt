package com.sulfuro.salati.data.settings

import androidx.compose.runtime.Immutable
import com.sulfuro.salati.core.zakat.ZakatGoldItem
import com.sulfuro.salati.core.zakat.ZakatSilverItem
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
    val zakatGoldPrice: Double = 70.0,
    val zakatNisabGram: Double = 85.0,
    val zakatSilverPrice: Double = 0.8,
    val zakatNisabSilverGram: Double = 595.0,
    val zakatCurrencyCode: String = "EUR",
    val zakatPricesUpdatedAt: Long = 0L,
    val zakatPricesCurrencyCode: String = "",
    val zakatHawlStartEpochDay: Long? = null,
    val zakatStandard: Int = 0,
    val zakatCashOnHand: Double = 0.0,
    val zakatBankBalance: Double = 0.0,
    val zakatInvestments: Double = 0.0,
    val zakatReceivables: Double = 0.0,
    val zakatLiabilities: Double = 0.0,
    val zakatGoldItems: List<ZakatGoldItem> = emptyList(),
    val zakatSilverItems: List<ZakatSilverItem> = emptyList()
)

@Immutable
@Serializable
data class AppearanceSettings(
    val isDarkMode: Boolean? = null,
    val appLanguageCode: String? = null,
    val timeFormat: String = TimeFormatPreference.SYSTEM
)
