package com.sulfuro.salati.data.settings

import androidx.compose.runtime.Immutable
import kotlin.ConsistentCopyVisibility
import com.sulfuro.salati.core.zakat.ZakatGoldItem
import com.sulfuro.salati.core.zakat.ZakatSilverItem
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** How prayer times are rendered; [SYSTEM] follows the device's 12/24-hour setting. */
object TimeFormatPreference {
    const val SYSTEM = "SYSTEM"
    const val TWELVE_HOUR = "12H"
    const val TWENTY_FOUR_HOUR = "24H"
}

/**
 * Marked immutable for Compose's benefit, and it genuinely is: every property is a `val`,
 * the grouped objects are only ever replaced wholesale by `copy`, and instances come from
 * deserialising the settings store rather than being edited in place.
 *
 * Without the annotation Compose sees `List` and infers the whole class unstable, which
 * makes every composable taking a [CalculationSettings] non-skippable.
 *
 * The persisted JSON remains flat. Groups exist in source so location, prayer method,
 * alarms, Zakat, and appearance are not one undifferentiated bag of fields.
 */
@Immutable
@ConsistentCopyVisibility
@Serializable(with = CalculationSettings.FlatSerializer::class)
data class CalculationSettings private constructor(
    val hasCompletedOnboarding: Boolean = false,
    val location: LocationSettings = LocationSettings(),
    val prayer: PrayerMethodSettings = PrayerMethodSettings(),
    val alarms: AlarmPreferences = AlarmPreferences(),
    val zakat: ZakatPreferences = ZakatPreferences(),
    val appearance: AppearanceSettings = AppearanceSettings()
) {
    val latitude: Double get() = location.latitude
    val longitude: Double get() = location.longitude
    val cityName: String get() = location.cityName
    val countryName: String get() = location.countryName
    val timezoneId: String get() = location.timezoneId

    val calculationMethod: String get() = prayer.calculationMethod
    val madhab: String get() = prayer.madhab
    val highLatitudeRule: String get() = prayer.highLatitudeRule
    val hijriOffset: Int get() = prayer.hijriOffset

    val prePrayerMinutes: Int get() = alarms.prePrayerMinutes
    val vibrateEnabled: Boolean get() = alarms.vibrateEnabled
    val soundEnabled: Boolean get() = alarms.soundEnabled
    val adhanSoundId: String? get() = alarms.adhanSoundId
    val adhanSoundName: String? get() = alarms.adhanSoundName
    val fajrAdhanSoundId: String? get() = alarms.fajrAdhanSoundId
    val fajrAdhanSoundName: String? get() = alarms.fajrAdhanSoundName
    val notificationsMuted: Boolean get() = alarms.notificationsMuted
    val whiteDaysReminder: Boolean get() = alarms.whiteDaysReminder
    val silentModeAutomationEnabled: Boolean get() = alarms.silentModeAutomationEnabled
    val silentModeMinutesAfterAdhan: Int get() = alarms.silentModeMinutesAfterAdhan
    val silentModeDurationMinutes: Int get() = alarms.silentModeDurationMinutes

    val zakatGoldPrice: Double get() = zakat.zakatGoldPrice
    val zakatNisabGram: Double get() = zakat.zakatNisabGram
    val zakatSilverPrice: Double get() = zakat.zakatSilverPrice
    val zakatNisabSilverGram: Double get() = zakat.zakatNisabSilverGram
    val zakatCurrencyCode: String get() = zakat.zakatCurrencyCode
    val zakatPricesUpdatedAt: Long get() = zakat.zakatPricesUpdatedAt
    val zakatPricesCurrencyCode: String get() = zakat.zakatPricesCurrencyCode
    val zakatHawlStartEpochDay: Long? get() = zakat.zakatHawlStartEpochDay
    val zakatStandard: Int get() = zakat.zakatStandard
    val zakatCashOnHand: Double get() = zakat.zakatCashOnHand
    val zakatBankBalance: Double get() = zakat.zakatBankBalance
    val zakatInvestments: Double get() = zakat.zakatInvestments
    val zakatReceivables: Double get() = zakat.zakatReceivables
    val zakatLiabilities: Double get() = zakat.zakatLiabilities
    val zakatGoldItems: List<ZakatGoldItem> get() = zakat.zakatGoldItems
    val zakatSilverItems: List<ZakatSilverItem> get() = zakat.zakatSilverItems

    val isDarkMode: Boolean? get() = appearance.isDarkMode
    val appLanguageCode: String? get() = appearance.appLanguageCode
    val timeFormat: String get() = appearance.timeFormat

    fun copy(
        hasCompletedOnboarding: Boolean = this.hasCompletedOnboarding,
        latitude: Double = this.latitude,
        longitude: Double = this.longitude,
        cityName: String = this.cityName,
        countryName: String = this.countryName,
        timezoneId: String = this.timezoneId,
        calculationMethod: String = this.calculationMethod,
        madhab: String = this.madhab,
        highLatitudeRule: String = this.highLatitudeRule,
        hijriOffset: Int = this.hijriOffset,
        prePrayerMinutes: Int = this.prePrayerMinutes,
        vibrateEnabled: Boolean = this.vibrateEnabled,
        soundEnabled: Boolean = this.soundEnabled,
        adhanSoundId: String? = this.adhanSoundId,
        adhanSoundName: String? = this.adhanSoundName,
        fajrAdhanSoundId: String? = this.fajrAdhanSoundId,
        fajrAdhanSoundName: String? = this.fajrAdhanSoundName,
        notificationsMuted: Boolean = this.notificationsMuted,
        whiteDaysReminder: Boolean = this.whiteDaysReminder,
        silentModeAutomationEnabled: Boolean = this.silentModeAutomationEnabled,
        silentModeMinutesAfterAdhan: Int = this.silentModeMinutesAfterAdhan,
        silentModeDurationMinutes: Int = this.silentModeDurationMinutes,
        zakatGoldPrice: Double = this.zakatGoldPrice,
        zakatNisabGram: Double = this.zakatNisabGram,
        zakatSilverPrice: Double = this.zakatSilverPrice,
        zakatNisabSilverGram: Double = this.zakatNisabSilverGram,
        zakatCurrencyCode: String = this.zakatCurrencyCode,
        zakatPricesUpdatedAt: Long = this.zakatPricesUpdatedAt,
        zakatPricesCurrencyCode: String = this.zakatPricesCurrencyCode,
        zakatHawlStartEpochDay: Long? = this.zakatHawlStartEpochDay,
        zakatStandard: Int = this.zakatStandard,
        zakatCashOnHand: Double = this.zakatCashOnHand,
        zakatBankBalance: Double = this.zakatBankBalance,
        zakatInvestments: Double = this.zakatInvestments,
        zakatReceivables: Double = this.zakatReceivables,
        zakatLiabilities: Double = this.zakatLiabilities,
        zakatGoldItems: List<ZakatGoldItem> = this.zakatGoldItems,
        zakatSilverItems: List<ZakatSilverItem> = this.zakatSilverItems,
        isDarkMode: Boolean? = this.isDarkMode,
        appLanguageCode: String? = this.appLanguageCode,
        timeFormat: String = this.timeFormat
    ): CalculationSettings = CalculationSettings(
        hasCompletedOnboarding = hasCompletedOnboarding,
        location = LocationSettings(latitude, longitude, cityName, countryName, timezoneId),
        prayer = PrayerMethodSettings(calculationMethod, madhab, highLatitudeRule, hijriOffset),
        alarms = AlarmPreferences(
            prePrayerMinutes, vibrateEnabled, soundEnabled, adhanSoundId, adhanSoundName,
            fajrAdhanSoundId, fajrAdhanSoundName, notificationsMuted, whiteDaysReminder,
            silentModeAutomationEnabled, silentModeMinutesAfterAdhan, silentModeDurationMinutes
        ),
        zakat = ZakatPreferences(
            zakatGoldPrice, zakatNisabGram, zakatSilverPrice, zakatNisabSilverGram,
            zakatCurrencyCode, zakatPricesUpdatedAt, zakatPricesCurrencyCode, zakatHawlStartEpochDay,
            zakatStandard, zakatCashOnHand, zakatBankBalance, zakatInvestments, zakatReceivables,
            zakatLiabilities, zakatGoldItems, zakatSilverItems
        ),
        appearance = AppearanceSettings(isDarkMode, appLanguageCode, timeFormat)
    )

    companion object {
        operator fun invoke(
            hasCompletedOnboarding: Boolean = false,
            latitude: Double = 0.0,
            longitude: Double = 0.0,
            cityName: String = "",
            countryName: String = "",
            timezoneId: String = "",
            calculationMethod: String = "MUSLIM_WORLD_LEAGUE",
            madhab: String = "SHAFI",
            highLatitudeRule: String = "TWILIGHT_ANGLE",
            hijriOffset: Int = 0,
            prePrayerMinutes: Int = 10,
            vibrateEnabled: Boolean = true,
            soundEnabled: Boolean = false,
            adhanSoundId: String? = null,
            adhanSoundName: String? = null,
            fajrAdhanSoundId: String? = null,
            fajrAdhanSoundName: String? = null,
            notificationsMuted: Boolean = false,
            whiteDaysReminder: Boolean = false,
            silentModeAutomationEnabled: Boolean = false,
            silentModeMinutesAfterAdhan: Int = 0,
            silentModeDurationMinutes: Int = 20,
            zakatGoldPrice: Double = 70.0,
            zakatNisabGram: Double = 85.0,
            zakatSilverPrice: Double = 0.8,
            zakatNisabSilverGram: Double = 595.0,
            zakatCurrencyCode: String = "EUR",
            zakatPricesUpdatedAt: Long = 0L,
            zakatPricesCurrencyCode: String = "",
            zakatHawlStartEpochDay: Long? = null,
            zakatStandard: Int = 0,
            zakatCashOnHand: Double = 0.0,
            zakatBankBalance: Double = 0.0,
            zakatInvestments: Double = 0.0,
            zakatReceivables: Double = 0.0,
            zakatLiabilities: Double = 0.0,
            zakatGoldItems: List<ZakatGoldItem> = emptyList(),
            zakatSilverItems: List<ZakatSilverItem> = emptyList(),
            isDarkMode: Boolean? = null,
            appLanguageCode: String? = null,
            timeFormat: String = TimeFormatPreference.SYSTEM
        ): CalculationSettings = CalculationSettings(
            hasCompletedOnboarding = hasCompletedOnboarding,
            location = LocationSettings(latitude, longitude, cityName, countryName, timezoneId),
            prayer = PrayerMethodSettings(calculationMethod, madhab, highLatitudeRule, hijriOffset),
            alarms = AlarmPreferences(
                prePrayerMinutes, vibrateEnabled, soundEnabled, adhanSoundId, adhanSoundName,
                fajrAdhanSoundId, fajrAdhanSoundName, notificationsMuted, whiteDaysReminder,
                silentModeAutomationEnabled, silentModeMinutesAfterAdhan, silentModeDurationMinutes
            ),
            zakat = ZakatPreferences(
                zakatGoldPrice, zakatNisabGram, zakatSilverPrice, zakatNisabSilverGram,
                zakatCurrencyCode, zakatPricesUpdatedAt, zakatPricesCurrencyCode, zakatHawlStartEpochDay,
                zakatStandard, zakatCashOnHand, zakatBankBalance, zakatInvestments, zakatReceivables,
                zakatLiabilities, zakatGoldItems, zakatSilverItems
            ),
            appearance = AppearanceSettings(isDarkMode, appLanguageCode, timeFormat)
        )
    }

    /**
     * Stores [CalculationSettings] as the same flat JSON installs already have.
     * Nested groups are a source layout, not a file-format change.
     */
    internal object FlatSerializer : KSerializer<CalculationSettings> {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("CalculationSettings")

    override fun serialize(encoder: Encoder, value: CalculationSettings) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(toJson(value))
    }

    override fun deserialize(decoder: Decoder): CalculationSettings {
        val jsonDecoder = decoder as JsonDecoder
        return fromJson(jsonDecoder.decodeJsonElement())
    }

    fun toJson(value: CalculationSettings): JsonObject {
        val merged = LinkedHashMap<String, JsonElement>()
        merged["hasCompletedOnboarding"] = JsonPrimitive(value.hasCompletedOnboarding)
        merged.putAll(json.encodeToJsonElement(LocationSettings.serializer(), value.location).jsonObject)
        merged.putAll(json.encodeToJsonElement(PrayerMethodSettings.serializer(), value.prayer).jsonObject)
        merged.putAll(json.encodeToJsonElement(AlarmPreferences.serializer(), value.alarms).jsonObject)
        merged.putAll(json.encodeToJsonElement(ZakatPreferences.serializer(), value.zakat).jsonObject)
        merged.putAll(json.encodeToJsonElement(AppearanceSettings.serializer(), value.appearance).jsonObject)
        return JsonObject(merged)
    }

    fun fromJson(element: JsonElement): CalculationSettings {
        val obj = element.jsonObject
        return CalculationSettings(
            hasCompletedOnboarding = obj["hasCompletedOnboarding"]?.let {
                (it as? JsonPrimitive)?.booleanOrNull
            } ?: false,
            location = json.decodeFromJsonElement(LocationSettings.serializer(), obj),
            prayer = json.decodeFromJsonElement(PrayerMethodSettings.serializer(), obj),
            alarms = json.decodeFromJsonElement(AlarmPreferences.serializer(), obj),
            zakat = json.decodeFromJsonElement(ZakatPreferences.serializer(), obj),
            appearance = json.decodeFromJsonElement(AppearanceSettings.serializer(), obj)
        )
    }
    }
}

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

/**
 * Whether the user has actually chosen a prayer location.
 *
 * New installs start with empty coordinates and must set a city (GPS or search)
 * during onboarding. Older installs that already wrote coordinates keep working:
 * a named city, or any non-origin coordinate pair, counts as configured.
 */
fun CalculationSettings.hasConfiguredLocation(): Boolean {
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    if (kotlin.math.abs(latitude) > 90.0 || kotlin.math.abs(longitude) > 180.0) return false
    if (cityName.isNotBlank()) return true
    return latitude != 0.0 || longitude != 0.0
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
