package com.sulfuro.salati.data.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.sulfuro.salati.core.zakat.ZakatGoldItem
import com.sulfuro.salati.core.zakat.ZakatSilverItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Zakat walkthrough writes every answer straight to preferences, because the
 * assessment is revisited a lunar year later and re-entering each piece of jewellery is
 * the main thing that makes the tool tedious. These tests cover that the new fields
 * survive a round trip and that installs predating them still decode.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class ZakatSettingsPersistenceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val json = Json { encodeDefaults = true }

    @Test
    fun walkthroughAnswersSurviveASerializationRoundTrip() {
        val settings = CalculationSettings(
            zakatStandard = 1,
            zakatCashOnHand = 1200.50,
            zakatBankBalance = 8400.0,
            zakatInvestments = 250.0,
            zakatReceivables = 75.25,
            zakatLiabilities = 900.0,
            zakatGoldItems = listOf(
                ZakatGoldItem(id = "g1", label = "Necklace", karat = 18, weightGrams = 42.5),
                ZakatGoldItem(id = "g2", label = "Coins", karat = 24, weightGrams = 10.0)
            ),
            zakatSilverItems = listOf(
                ZakatSilverItem(id = "s1", label = "Cutlery", millesimal = 925, weightGrams = 300.0)
            ),
            timeFormat = TimeFormatPreference.TWELVE_HOUR
        )

        val encoded = json.encodeToString(CalculationSettings.serializer(), settings)
        val decoded = json.decodeFromString(CalculationSettings.serializer(), encoded)

        assertEquals(settings, decoded)
        assertEquals(2, decoded.zakatGoldItems.size)
        assertEquals(18, decoded.zakatGoldItems.first().karat)
        assertEquals("Cutlery", decoded.zakatSilverItems.single().label)

        listOf(
            "zakatStandard",
            "zakatCashOnHand",
            "zakatBankBalance",
            "zakatInvestments",
            "zakatReceivables",
            "zakatLiabilities",
            "zakatGoldItems",
            "zakatSilverItems",
            "timeFormat"
        ).forEach { assertTrue("missing persisted key $it", encoded.contains("\"$it\"")) }
    }

    @Test
    fun settingsSavedBeforeTheWalkthroughReceiveEmptyDefaults() {
        val legacyJson = """{"hasCompletedOnboarding":true,"zakatCurrencyCode":"USD"}"""

        val decoded = json.decodeFromString(CalculationSettings.serializer(), legacyJson)

        assertEquals(0, decoded.zakatStandard)
        assertEquals(0.0, decoded.zakatCashOnHand, 0.0001)
        assertEquals(0.0, decoded.zakatLiabilities, 0.0001)
        assertTrue(decoded.zakatGoldItems.isEmpty())
        assertTrue(decoded.zakatSilverItems.isEmpty())
        assertEquals(TimeFormatPreference.SYSTEM, decoded.timeFormat)
        assertEquals("USD", decoded.zakatCurrencyCode)
    }

    @Test
    fun preferencesPersistItemisedMetalsAcrossReads() = runBlocking {
        val preferences = SalatiPreferences(context)

        preferences.updateSettings {
            it.copy(
                zakatGoldItems = listOf(
                    ZakatGoldItem(id = "ring", label = "Ring", karat = 21, weightGrams = 6.0)
                ),
                zakatCashOnHand = 500.0,
                zakatStandard = 1
            )
        }

        val reloaded = SalatiPreferences(context).settings.first()

        assertEquals(1, reloaded.zakatStandard)
        assertEquals(500.0, reloaded.zakatCashOnHand, 0.0001)
        assertEquals("Ring", reloaded.zakatGoldItems.single().label)
        assertEquals(21, reloaded.zakatGoldItems.single().karat)
        assertEquals(6.0, reloaded.zakatGoldItems.single().weightGrams, 0.0001)
    }

    @Test
    fun removingAnItemIsPersistedRatherThanLeftBehind() = runBlocking {
        val preferences = SalatiPreferences(context)
        preferences.updateSettings {
            it.copy(
                zakatGoldItems = listOf(
                    ZakatGoldItem(id = "a", karat = 24, weightGrams = 1.0),
                    ZakatGoldItem(id = "b", karat = 18, weightGrams = 2.0)
                )
            )
        }

        preferences.updateSettings { current ->
            current.copy(zakatGoldItems = current.zakatGoldItems.filterNot { it.id == "a" })
        }

        val reloaded = preferences.settings.first()
        assertEquals(listOf("b"), reloaded.zakatGoldItems.map { it.id })
    }

    @Test
    fun locationChosenByCitySearchIsPersistedWithoutExposingCoordinates() = runBlocking {
        val preferences = SalatiPreferences(context)

        // What CitySearchSheet hands the settings screen after a suggestion is picked.
        preferences.updateSettings {
            it.copy(
                cityName = "Casablanca, Morocco",
                countryName = "Morocco",
                latitude = 33.5731,
                longitude = -7.5898,
                timezoneId = "Africa/Casablanca"
            )
        }

        val reloaded = preferences.settings.first()
        assertEquals("Casablanca, Morocco", reloaded.cityName)
        assertEquals("Morocco", reloaded.countryName)
        assertEquals("Africa/Casablanca", reloaded.timezoneId)
        assertEquals(33.5731, reloaded.latitude, 0.0001)
        assertEquals(-7.5898, reloaded.longitude, 0.0001)
    }

    @Test
    fun timeFormatPreferenceOverridesTheDeviceSettingInBothDirections() {
        assertEquals(true, resolveUses24HourClock(TimeFormatPreference.TWENTY_FOUR_HOUR, false))
        assertEquals(false, resolveUses24HourClock(TimeFormatPreference.TWELVE_HOUR, true))
        // "System" defers to the device, including for values written by older builds.
        assertEquals(true, resolveUses24HourClock(TimeFormatPreference.SYSTEM, true))
        assertEquals(false, resolveUses24HourClock(TimeFormatPreference.SYSTEM, false))
        assertEquals(true, resolveUses24HourClock(null, true))
    }
}
