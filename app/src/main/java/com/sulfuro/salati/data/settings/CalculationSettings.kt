package com.sulfuro.salati.data.settings

import androidx.compose.runtime.Immutable
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
 * App settings, grouped the way the screens are: location, prayer method, alarms,
 * Zakat, and appearance.
 *
 * Marked immutable for Compose. The persisted JSON is still flat so existing installs
 * keep their values; [FlatSerializer] is the only place that knows that layout.
 */
@Immutable
@Serializable(with = CalculationSettings.FlatSerializer::class)
data class CalculationSettings(
    val hasCompletedOnboarding: Boolean = false,
    val location: LocationSettings = LocationSettings(),
    val prayer: PrayerMethodSettings = PrayerMethodSettings(),
    val alarms: AlarmPreferences = AlarmPreferences(),
    val zakat: ZakatPreferences = ZakatPreferences(),
    val appearance: AppearanceSettings = AppearanceSettings()
) {
    /**
     * Stores [CalculationSettings] as the same flat JSON installs already have.
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

private const val FAJR_KEY = "fajr"

/**
 * The recitation to play for one prayer.
 *
 * The Fajr adhan is not the same call as the other four: it adds
 * "as-salatu khayrun min an-nawm" - prayer is better than sleep - which is only said at
 * dawn. Falls back to the general choice when no Fajr recording has been picked.
 */
fun CalculationSettings.adhanSoundIdFor(prayerKey: String): String? {
    return if (prayerKey.equals(FAJR_KEY, ignoreCase = true)) {
        alarms.fajrAdhanSoundId ?: alarms.adhanSoundId
    } else {
        alarms.adhanSoundId
    }
}

fun CalculationSettings.safeZoneId(): java.time.ZoneId {
    return safeZoneId(location.timezoneId)
}

/**
 * Whether the user has actually chosen a prayer location.
 *
 * New installs start with empty coordinates and must set a city (GPS or search)
 * during onboarding. Older installs that already wrote coordinates keep working:
 * a named city, or any non-origin coordinate pair, counts as configured.
 */
fun CalculationSettings.hasConfiguredLocation(): Boolean {
    val latitude = location.latitude
    val longitude = location.longitude
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    if (kotlin.math.abs(latitude) > 90.0 || kotlin.math.abs(longitude) > 180.0) return false
    if (location.cityName.isNotBlank()) return true
    return latitude != 0.0 || longitude != 0.0
}

fun safeZoneId(timezoneId: String?): java.time.ZoneId {
    if (timezoneId.isNullOrBlank()) return java.time.ZoneId.systemDefault()
    return runCatching { java.time.ZoneId.of(timezoneId) }.getOrElse { java.time.ZoneId.systemDefault() }
}

/**
 * Resolves the stored [AppearanceSettings.timeFormat] against the device setting so
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
