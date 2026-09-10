package com.sulfuro.salati.data.settings

import com.sulfuro.salati.core.audio.AdhanCatalog
import com.sulfuro.salati.core.audio.AdhanCatalogResult
import com.sulfuro.salati.core.alarms.alarmRelevantFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fajr has its own adhan because it is a different call, not a matter of taste: it adds
 * "as-salatu khayrun min an-nawm" - prayer is better than sleep - which belongs at dawn
 * and nowhere else. These pin the rule that decides which recitation each prayer gets.
 */
class FajrAdhanSelectionTest {

    private val settings = CalculationSettings(
        adhanSoundId = "makkah_mullah",
        fajrAdhanSoundId = "fajr_makkah"
    )

    @Test
    fun fajrTakesTheFajrRecordingAndEveryOtherPrayerTakesTheGeneralOne() {
        assertEquals("fajr_makkah", settings.adhanSoundIdFor("fajr"))
        for (prayer in listOf("dhuhr", "asr", "maghrib", "isha", "sunrise")) {
            assertEquals(
                "$prayer must not get the Fajr recitation",
                "makkah_mullah",
                settings.adhanSoundIdFor(prayer)
            )
        }
    }

    /**
     * The scheduler lower-cases prayer names before they become keys, but nothing forces
     * it to stay that way, and a capitalisation change must not silently move Fajr onto
     * the wrong recording.
     */
    @Test
    fun theFajrKeyIsMatchedWhateverItsCase() {
        assertEquals("fajr_makkah", settings.adhanSoundIdFor("Fajr"))
        assertEquals("fajr_makkah", settings.adhanSoundIdFor("FAJR"))
    }

    /** An install that predates this setting has to behave exactly as it did before. */
    @Test
    fun withoutAFajrChoiceEveryPrayerKeepsTheGeneralAdhan() {
        val unset = CalculationSettings(adhanSoundId = "madinah")

        assertEquals("madinah", unset.adhanSoundIdFor("fajr"))
        assertEquals("madinah", unset.adhanSoundIdFor("isha"))
    }

    /** And with nothing chosen at all, nothing plays - the device tone is used. */
    @Test
    fun noAdhanAtAllStaysNull() {
        assertNull(CalculationSettings().adhanSoundIdFor("fajr"))
        assertNull(CalculationSettings().adhanSoundIdFor("dhuhr"))
    }

    /**
     * Alarms carry their recitation in the pending intent, so changing the Fajr choice
     * only takes effect if it forces a reschedule. Leaving it out of the fingerprint
     * would make the setting appear to work and change nothing until the next refresh.
     */
    @Test
    fun changingTheFajrAdhanIsAnAlarmRelevantChange() {
        val before = settings.alarmRelevantFingerprint()
        val after = settings.copy(fajrAdhanSoundId = "fajr_madinah").alarmRelevantFingerprint()

        assertNotEquals(before, after)
    }

    /** The name is only shown in Settings, so it must not cost every alarm a reschedule. */
    @Test
    fun renamingTheStoredLabelIsNotAnAlarmRelevantChange() {
        val before = settings.alarmRelevantFingerprint()
        val after = settings.copy(fajrAdhanSoundName = "Something else").alarmRelevantFingerprint()

        assertEquals(before, after)
    }

    /**
     * The two picker lists are built from this flag, and they must not overlap - that is
     * what stops a Fajr recording being chosen for the other four prayers.
     */
    @Test
    fun theCatalogueCarriesTheFajrFlagAndDefaultsToFalse() {
        val json = """
            {"version":1,"adhans":[
              {"id":"makkah","name":"Makkah","url":"https://salati.sulfuro.xyz/a/makkah.mp3"},
              {"id":"makkah_fajr","name":"Makkah - Fajr",
               "url":"https://salati.sulfuro.xyz/a/makkah_fajr.mp3","fajr":true}
            ]}
        """.trimIndent()

        val result = AdhanCatalog.parse(json)

        assertTrue(result is AdhanCatalogResult.Available)
        val options = (result as AdhanCatalogResult.Available).options
        assertFalse("an entry without the flag is a general adhan", options[0].isFajr)
        assertTrue(options[1].isFajr)
        assertEquals(1, options.count { it.isFajr })
        assertEquals(1, options.count { !it.isFajr })
    }
}
