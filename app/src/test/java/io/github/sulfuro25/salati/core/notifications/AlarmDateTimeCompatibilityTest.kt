package io.github.sulfuro25.salati.core.notifications

import io.github.sulfuro25.salati.core.computation.SalatiPrayerTimes
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AlarmDateTimeCompatibilityTest {
    @Test
    fun mainAndPreReminderEpochsAndIdentitiesRemainLegacyCompatible() {
        val date = LocalDate.of(2026, 7, 15)
        val times = SalatiPrayerTimes(
            date = date,
            fajr = Instant.ofEpochMilli(1_784_078_580_000L),
            sunrise = Instant.ofEpochMilli(1_784_087_160_000L),
            dhuhr = Instant.ofEpochMilli(1_784_116_080_000L),
            asr = Instant.ofEpochMilli(1_784_131_560_000L),
            maghrib = Instant.ofEpochMilli(1_784_145_060_000L),
            isha = Instant.ofEpochMilli(1_784_066_700_000L),
            middleOfTheNight = Instant.ofEpochMilli(1_784_072_880_000L),
            lastThirdOfTheNight = Instant.ofEpochMilli(1_784_077_620_000L),
            hijri = null
        )

        val result = AlarmScheduler.buildPreparedAlarms(
            timesByDate = mapOf(date to times),
            hijriMetadata = emptyMap(),
            settings = CalculationSettings(prePrayerMinutes = 10),
            nowMillis = 1_784_000_000_000L
        )
        val main = result.alarms.single { it.prayerKey == "fajr" && !it.isPreReminder }
        val pre = result.alarms.single { it.prayerKey == "fajr" && it.isPreReminder }

        assertEquals(1_784_078_580_000L, main.triggerAtMillis)
        assertEquals(1_784_077_980_000L, pre.triggerAtMillis)
        assertEquals(660_770, main.requestCode)
        assertEquals(660_771, pre.requestCode)
        assertEquals("salati://alarm/2026-07-15/fajr/main", main.uri)
        assertEquals("salati://alarm/2026-07-15/fajr/pre", pre.uri)
        assertFalse(result.alarms.any { it.prayerKey == "sunrise" })
    }
}
