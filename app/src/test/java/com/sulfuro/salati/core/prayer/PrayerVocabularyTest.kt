package com.sulfuro.salati.core.prayer

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's list of prayers, and the promise that it is written down once.
 */
class PrayerVocabularyTest {

    /**
     * These keys and ids are on people's phones.
     *
     * An alarm scheduled by an older version carries its prayer's key in the intent extra
     * and its id inside the PendingIntent request code, so changing either here would leave
     * those alarms unmatched - the registry would fail to recognise or cancel them. The
     * test exists to make that consequence visible to whoever tries.
     */
    @Test
    fun theStoredIdentityOfEachPrayerIsFixed() {
        assertEquals(
            listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"),
            Prayer.entries.map { it.key }
        )
        assertEquals(
            listOf(1, null, 2, 3, 4, 5),
            Prayer.entries.map { it.alarmId }
        )
    }

    /** Sunrise is the end of Fajr's window, not a prayer: it is never alerted. */
    @Test
    fun sunriseIsCountedInTheDayButNotAmongThePrayers() {
        assertTrue(Prayer.SUNRISE in Prayer.entries)
        assertTrue(Prayer.SUNRISE !in Prayer.prayed)
        assertNull(Prayer.SUNRISE.alarmId)
        assertEquals(5, Prayer.prayed.size)
    }

    /** Declaration order is the order of the day, and the window chain leans on it. */
    @Test
    fun theOrderOfTheEnumIsTheOrderOfTheDay() {
        assertEquals(
            listOf(
                Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR,
                Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA
            ),
            Prayer.entries.toList()
        )
    }

    @Test
    fun aPrayerCanBeFoundByTheKeyAnOlderAlarmCarries() {
        assertEquals(Prayer.FAJR, Prayer.byKey("fajr"))
        // Alarms written before the keys were lowercased spell it this way.
        assertEquals(Prayer.MAGHRIB, Prayer.byKey("Maghrib"))
        assertNull(Prayer.byKey("white_days"))
    }

    /**
     * The list of prayers is spelled out in one place, and stays that way.
     *
     * It used to be typed out in six - the dashboard, the window chain, the calendar's six
     * rows, the scheduler's five addAlarm calls, the widget snapshot, and the receiver's
     * key-to-label lookup - with nothing tying them together, so a disagreement between
     * them compiled cleanly and only showed on the phone.
     *
     * The files below are the ones where naming every prayer *is* the job: the enum itself,
     * the two that translate someone else's field names into ours, the solar calculator
     * (each prayer has its own formula), and the widget layouts (whose view ids belong to
     * the XML, not to us). Anything else naming three or more is a seventh copy in the
     * making - reach for [Prayer] instead of adding a line here.
     */
    @Test
    fun onlyTheEdgesOfTheAppSpellOutEveryPrayer() {
        val allowed = setOf(
            "core/prayer/Prayer.kt",
            "core/computation/LocalPrayerTimeCalculator.kt",
            "core/computation/PrayerTimeMapper.kt",
            "widget/SalatiWidgetData.kt",
            "widget/SalatiAppWidgetProvider.kt",
            "widget/SalatiMinimalBarWidgetProvider.kt"
        )
        // Matches a prayer named as a property, a string literal or a view id, in any
        // of the spellings the app meets - `.fajr`, `"Fajr"`, `widget_cell_fajr` - but not
        // `Prayer.FAJR`, which is the whole point of having the enum.
        val named = Regex("""(?<!Prayer)[."_](fajr|sunrise|dhuhr|asr|maghrib|isha)\b""", RegexOption.IGNORE_CASE)

        val sources = projectPath("src/main/java/com/sulfuro/salati").toFile()
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }

        val offenders = sources.filter { file ->
            named.findAll(file.readText()).map { it.groupValues[1].lowercase() }.toSet().size >= 3
        }.map { file ->
            file.invariantSeparatorsPath.substringAfter("com/sulfuro/salati/")
        }.toList()

        assertEquals(
            "these files spell out the prayers themselves instead of using Prayer",
            allowed,
            offenders.toSet()
        )
    }

    private fun projectPath(relative: String): Path {
        val direct = Path.of(relative)
        return if (Files.exists(direct)) direct else Path.of("app").resolve(relative)
    }
}
