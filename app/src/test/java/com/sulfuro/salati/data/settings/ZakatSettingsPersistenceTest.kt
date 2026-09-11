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
        val settings = CalculationSettings(zakat = ZakatPreferences(standard = 1, cashOnHand = 1200.50, bankBalance = 8400.0, investments = 250.0, receivables = 75.25, liabilities = 900.0, goldItems = listOf(
                ZakatGoldItem(id = "g1", label = "Necklace", karat = 18, weightGrams = 42.5),
                ZakatGoldItem(id = "g2", label = "Coins", karat = 24, weightGrams = 10.0)
            ), silverItems = listOf(
                ZakatSilverItem(id = "s1", label = "Cutlery", millesimal = 925, weightGrams = 300.0)
            )), appearance = AppearanceSettings(timeFormat = TimeFormatPreference.TWELVE_HOUR))

        val encoded = json.encodeToString(CalculationSettings.serializer(), settings)
        val decoded = json.decodeFromString(CalculationSettings.serializer(), encoded)

        assertEquals(settings, decoded)
        assertEquals(2, decoded.zakat.goldItems.size)
        assertEquals(18, decoded.zakat.goldItems.first().karat)
        assertEquals("Cutlery", decoded.zakat.silverItems.single().label)

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

        assertEquals(0, decoded.zakat.standard)
        assertEquals(0.0, decoded.zakat.cashOnHand, 0.0001)
        assertEquals(0.0, decoded.zakat.liabilities, 0.0001)
        assertTrue(decoded.zakat.goldItems.isEmpty())
        assertTrue(decoded.zakat.silverItems.isEmpty())
        assertEquals(TimeFormatPreference.SYSTEM, decoded.appearance.timeFormat)
        assertEquals("USD", decoded.zakat.currencyCode)
    }

    @Test
    fun preferencesPersistItemisedMetalsAcrossReads() = runBlocking {
        val preferences = SalatiPreferences(context)

        preferences.updateSettings {
            it.copy(zakat = it.zakat.copy(goldItems = listOf(
                    ZakatGoldItem(id = "ring", label = "Ring", karat = 21, weightGrams = 6.0)
                ), cashOnHand = 500.0, standard = 1))
        }

        val reloaded = SalatiPreferences(context).settings.first()

        assertEquals(1, reloaded.zakat.standard)
        assertEquals(500.0, reloaded.zakat.cashOnHand, 0.0001)
        assertEquals("Ring", reloaded.zakat.goldItems.single().label)
        assertEquals(21, reloaded.zakat.goldItems.single().karat)
        assertEquals(6.0, reloaded.zakat.goldItems.single().weightGrams, 0.0001)
    }

    @Test
    fun removingAnItemIsPersistedRatherThanLeftBehind() = runBlocking {
        val preferences = SalatiPreferences(context)
        preferences.updateSettings {
            it.copy(zakat = it.zakat.copy(goldItems = listOf(
                    ZakatGoldItem(id = "a", karat = 24, weightGrams = 1.0),
                    ZakatGoldItem(id = "b", karat = 18, weightGrams = 2.0)
                )))
        }

        preferences.updateSettings { current ->
            current.copy(zakat = current.zakat.copy(goldItems = current.zakat.goldItems.filterNot { it.id == "a" }))
        }

        val reloaded = preferences.settings.first()
        assertEquals(listOf("b"), reloaded.zakat.goldItems.map { it.id })
    }

    @Test
    fun locationChosenByCitySearchIsPersistedWithoutExposingCoordinates() = runBlocking {
        val preferences = SalatiPreferences(context)

        // What CitySearchSheet hands the settings screen after a suggestion is picked.
        preferences.updateSettings {
            it.copy(
                location = it.location.copy(
                    cityName = "Casablanca, Morocco",
                    countryName = "Morocco",
                    latitude = 33.5731,
                    longitude = -7.5898,
                    timezoneId = "Africa/Casablanca"
                )
            )
        }

        val reloaded = preferences.settings.first()
        assertEquals("Casablanca, Morocco", reloaded.location.cityName)
        assertEquals("Morocco", reloaded.location.countryName)
        assertEquals("Africa/Casablanca", reloaded.location.timezoneId)
        assertEquals(33.5731, reloaded.location.latitude, 0.0001)
        assertEquals(-7.5898, reloaded.location.longitude, 0.0001)
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
