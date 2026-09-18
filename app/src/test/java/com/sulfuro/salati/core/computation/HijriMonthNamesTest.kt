package com.sulfuro.salati.core.computation

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Hijri months in the reader's language.
 *
 * Deliberately light on exact spellings: CLDR is allowed to revise them, and a test that
 * pins all twelve in fourteen languages would fail on a toolchain bump without anything
 * being wrong. What is asserted is what the app actually promises - that the names arrive
 * in the reader's script, that the two languages CLDR cannot help keep a readable name,
 * and that no language shows a blank.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [24], manifest = Config.NONE)
class HijriMonthNamesTest {

    @Test
    fun arabicNamesTheMonthsInArabic() {
        val names = HijriMonthNames.forLocale(Locale.forLanguageTag("ar"))
        assertTrue("expected Arabic script, got $names", names.all { ARABIC_SCRIPT.containsMatchIn(it) })
        assertEquals("رمضان", names[8])
    }

    @Test
    fun urduAndPersianAreNotServedArabicsSpelling() {
        val arabic = HijriMonthNames.forLocale(Locale.forLanguageTag("ar"))
        val urdu = HijriMonthNames.forLocale(Locale.forLanguageTag("ur"))
        val persian = HijriMonthNames.forLocale(Locale.forLanguageTag("fa"))
        // All three write Arabic script, and all three spell the months differently.
        assertTrue(urdu.all { ARABIC_SCRIPT.containsMatchIn(it) })
        assertTrue(persian.all { ARABIC_SCRIPT.containsMatchIn(it) })
        assertNotEquals(arabic, urdu)
        assertNotEquals(arabic, persian)
    }

    /**
     * A language ICU has never heard of is given English for whatever it lacks.
     *
     * The fallback is asserted through an unknown language rather than a real one because
     * how much ICU knows about any given language is a property of the Android version:
     * this runs against API 24's data, where German is still called Rajab and Indonesian
     * still Rabi' al-Awwal, while a phone on 14 has richer names for both.
     *
     * What such a language falls back to is ICU's root, which names eight of the twelve
     * and counts the four that come in pairs. Those four are the ones replaced.
     */
    @Test
    fun aLanguageIcuHasNeverHeardOfIsGivenEnglishForWhateverItLacks() {
        val unknown = HijriMonthNames.forLocale(Locale.forLanguageTag("zz"))
        val english = HijriMonthNames.forLocale(Locale.ENGLISH)
        for (paired in listOf(2, 3, 4, 5)) {
            assertEquals(english[paired], unknown[paired])
        }
        assertTrue("root's numerals reached a reader: $unknown", unknown.none { NUMBERED.containsMatchIn(it) })
        // The eight it does name are its own, not English's.
        assertNotEquals(english, unknown)
    }

    /**
     * English CLDR answers "Rabiʻ I", which is not what an English-reading Muslim calls it.
     * The app's own transliterations are kept for exactly this reason.
     */
    @Test
    fun englishKeepsTheNameSpelledOut() {
        assertEquals("Rabi' al-Awwal", HijriMonthNames.of(3, Locale.ENGLISH))
        assertEquals("Dhu al-Hijjah", HijriMonthNames.of(12, Locale.ENGLISH))
    }

    /**
     * CLDR covers the months unevenly, and the gap is always the four that come in pairs -
     * the root spells those "Rabiʻ I" and "Jumada II", which is a way of counting rather
     * than a name. No reader should be shown one, in any language.
     */
    @Test
    fun noLanguageIsEverShownARomanNumeralInsteadOfAName() {
        for (tag in shippedLanguages()) {
            val names = HijriMonthNames.forLocale(Locale.forLanguageTag(tag))
            assertTrue("$tag still counts a month: $names", names.none { NUMBERED.containsMatchIn(it) })
        }
    }

    /**
     * Whatever else is true of a language, the date on the dashboard reads as a date.
     *
     * The bare number is the interesting one. It is what `java.time` answers on a phone,
     * where the desugared library carries no Islamic month names - "7 4 1448" - and it is
     * the reason these names come from ICU instead.
     */
    @Test
    fun everyShippedLanguageNamesAllTwelveMonths() {
        for (tag in shippedLanguages()) {
            val names = HijriMonthNames.forLocale(Locale.forLanguageTag(tag))
            assertEquals("$tag should name twelve months", 12, names.size)
            assertTrue("$tag has a blank month: $names", names.none { it.isBlank() })
            assertTrue("$tag numbers a month instead of naming it: $names", names.none(::isBareNumber))
        }
    }

    @Test
    fun aNumberThatIsNotAMonthHasNoName() {
        assertEquals("", HijriMonthNames.of(0))
        assertEquals("", HijriMonthNames.of(13))
    }

    private fun isBareNumber(name: String): Boolean = name.trim().all { it.isDigit() }

    /** Read from the manifest the app ships, so a new language is covered the day it lands. */
    private fun shippedLanguages(): List<String> {
        val config = projectPath("src/main/res/xml/locales_config.xml")
        return Regex("""android:name="([^"]+)"""")
            .findAll(Files.readString(config))
            .map { it.groupValues[1] }
            .toList()
    }

    private fun projectPath(relative: String): Path {
        val direct = Path.of(relative)
        return if (Files.exists(direct)) direct else Path.of("app").resolve(relative)
    }

    private companion object {
        val ARABIC_SCRIPT = Regex("""[؀-ۿ]""")
        val NUMBERED = Regex("""\s[IVX]+$""")
    }
}
