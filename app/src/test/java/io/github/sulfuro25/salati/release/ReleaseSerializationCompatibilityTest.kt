package io.github.sulfuro25.salati.release

import android.content.Context
import androidx.work.WorkerParameters
import io.github.sulfuro25.salati.Calendar
import io.github.sulfuro25.salati.Dashboard
import io.github.sulfuro25.salati.Settings
import io.github.sulfuro25.salati.Zakat
import io.github.sulfuro25.salati.core.computation.AladhanDate
import io.github.sulfuro25.salati.core.computation.AladhanDayData
import io.github.sulfuro25.salati.core.computation.AladhanGregorianDate
import io.github.sulfuro25.salati.core.computation.AladhanMonth
import io.github.sulfuro25.salati.core.computation.AladhanResponse
import io.github.sulfuro25.salati.core.computation.AladhanTimings
import io.github.sulfuro25.salati.core.notifications.AlarmCacheRestorationWorker
import io.github.sulfuro25.salati.core.notifications.AlarmMaintenanceWorker
import io.github.sulfuro25.salati.core.notifications.AlarmNetworkRefreshWorker
import io.github.sulfuro25.salati.core.notifications.AlarmSettingsRefreshDebounceWorker
import io.github.sulfuro25.salati.core.notifications.RegisteredAlarm
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSerializationCompatibilityTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun calculationSettingsRoundTripKeepsPersistedFieldNamesAndValues() {
        val settings = CalculationSettings(
            calculationMethod = "EGYPT",
            madhab = "HANAFI",
            highLatitudeRule = "TWILIGHT_ANGLE",
            hijriOffset = -1,
            prePrayerMinutes = 17,
            vibrateEnabled = false,
            soundEnabled = true,
            notificationsMuted = true,
            silentModeAutomationEnabled = true,
            silentModeMinutesAfterAdhan = 10,
            silentModeDurationMinutes = 30,
            zakatGoldPrice = 72.5,
            zakatGoldCarat = 21,
            zakatCurrencyCode = "USD",
            zakatHawlStartEpochDay = 20_000L
        )

        val encoded = json.encodeToString(CalculationSettings.serializer(), settings)
        assertEquals(settings, json.decodeFromString(CalculationSettings.serializer(), encoded))
        listOf(
            "calculationMethod",
            "madhab",
            "highLatitudeRule",
            "notificationsMuted",
            "prePrayerMinutes",
            "vibrateEnabled",
            "soundEnabled",
            "silentModeAutomationEnabled",
            "silentModeMinutesAfterAdhan",
            "silentModeDurationMinutes",
            "zakatCurrencyCode",
            "zakatGoldCarat",
            "zakatHawlStartEpochDay"
        ).forEach { assertTrue(encoded.contains("\"$it\"")) }
    }

    @Test
    fun settingsSavedBeforeSilentModeAndHawlFeaturesReceiveSafeDefaults() {
        val legacyJson = """{"hasCompletedOnboarding":true,"timezoneId":"Europe/Brussels"}"""

        val decoded = json.decodeFromString(CalculationSettings.serializer(), legacyJson)

        assertEquals(false, decoded.silentModeAutomationEnabled)
        assertEquals(0, decoded.silentModeMinutesAfterAdhan)
        assertEquals(20, decoded.silentModeDurationMinutes)
        assertEquals(null, decoded.zakatHawlStartEpochDay)
        assertEquals(24, decoded.zakatGoldCarat)
    }

    @Test
    fun alarmRegistryRoundTripKeepsIdentityAndEpochFields() {
        val alarms = listOf(
            RegisteredAlarm(
                requestCode = 12345,
                uri = "salati://alarm/2026-07-15/fajr/main",
                prayerKey = "fajr",
                isPreReminder = false,
                triggerAtMillis = 1_768_459_200_000L,
                vibrateEnabled = true,
                silentModeAutomationEnabled = true,
                silentModeMinutesAfterAdhan = 15,
                silentModeDurationMinutes = 20
            )
        )
        val serializer = ListSerializer(RegisteredAlarm.serializer())
        val encoded = json.encodeToString(serializer, alarms)

        assertEquals(alarms, json.decodeFromString(serializer, encoded))
        listOf(
            "requestCode",
            "uri",
            "prayerKey",
            "isPreReminder",
            "triggerAtMillis",
            "vibrateEnabled",
            "silentModeAutomationEnabled",
            "silentModeMinutesAfterAdhan",
            "silentModeDurationMinutes"
        )
            .forEach { assertTrue(encoded.contains("\"$it\"")) }
    }

    @Test
    fun aladhanDtoRoundTripPreservesMonthlyTransportShape() {
        val response = AladhanResponse(
            code = 200,
            status = "OK",
            data = listOf(
                AladhanDayData(
                    timings = AladhanTimings(
                        Fajr = "03:30",
                        Sunrise = "05:42",
                        Dhuhr = "13:45",
                        Asr = "18:02",
                        Sunset = "21:46",
                        Maghrib = "21:46",
                        Isha = "23:20",
                        Midnight = "00:38",
                        Lastthird = "01:35"
                    ),
                    date = AladhanDate(
                        readable = "15 Jul 2026",
                        timestamp = "1784073600",
                        gregorian = AladhanGregorianDate(
                            date = "15-07-2026",
                            day = "15",
                            month = AladhanMonth(7),
                            year = "2026"
                        )
                    )
                )
            )
        )

        val encoded = json.encodeToString(AladhanResponse.serializer(), response)
        assertEquals(response, json.decodeFromString(AladhanResponse.serializer(), encoded))
        assertTrue(encoded.contains("\"Sunrise\""))
        assertTrue(encoded.contains("\"Lastthird\""))
    }

    @Test
    fun navigationKeysRetainGeneratedSerializers() {
        assertEquals(Dashboard, json.decodeFromString(Dashboard.serializer(), json.encodeToString(Dashboard.serializer(), Dashboard)))
        assertEquals(Calendar, json.decodeFromString(Calendar.serializer(), json.encodeToString(Calendar.serializer(), Calendar)))
        assertEquals(Zakat, json.decodeFromString(Zakat.serializer(), json.encodeToString(Zakat.serializer(), Zakat)))
        assertEquals(Settings, json.decodeFromString(Settings.serializer(), json.encodeToString(Settings.serializer(), Settings)))
    }

    @Test
    fun everyWorkManagerWorkerHasTheDefaultFactoryConstructor() {
        listOf(
            AlarmCacheRestorationWorker::class.java,
            AlarmNetworkRefreshWorker::class.java,
            AlarmMaintenanceWorker::class.java,
            AlarmSettingsRefreshDebounceWorker::class.java
        ).forEach { workerClass ->
            workerClass.getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)
        }
    }
}
