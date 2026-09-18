package com.sulfuro.salati.core.computation

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The same promise as HijriMonthNamesTest, asked of the platform that actually answers it.
 *
 * This is not a duplicate. The unit tests run against Robolectric's copy of API 24's ICU;
 * this runs against a real phone's, which is both newer and reached through a different
 * path. The first version of [HijriMonthNames] asked `java.time` for these names and passed
 * every unit test, because the JDK answers them - while the desugared library that backs
 * java.time on a phone carries no Islamic month data at all and formats each month as its
 * number. A dashboard reading "7 4 1448" is what this test exists to refuse.
 *
 * Exact spellings are deliberately not asserted: how much ICU knows grows with the Android
 * version, so pinning them here would make the suite fail on an older phone for no reason.
 */
@RunWith(AndroidJUnit4::class)
class HijriMonthNamesDeviceTest {

    @Test
    fun theDeviceNamesTheMonthsRatherThanNumberingThem() {
        for (tag in SHIPPED_LANGUAGES) {
            val names = HijriMonthNames.forLocale(Locale.forLanguageTag(tag))
            assertEquals("$tag should name twelve months", 12, names.size)
            assertTrue("$tag has a blank month: $names", names.none { it.isBlank() })
            assertTrue(
                "$tag numbers a month instead of naming it - $names",
                names.none { name -> name.trim().all(Char::isDigit) }
            )
            assertTrue("$tag still counts a month: $names", names.none { NUMBERED.containsMatchIn(it) })
        }
    }

    @Test
    fun theDeviceNamesTheMonthsInTheReadersScript() {
        val arabic = HijriMonthNames.forLocale(Locale.forLanguageTag("ar"))
        assertTrue("expected Arabic script, got $arabic", arabic.all { ARABIC_SCRIPT.containsMatchIn(it) })
        assertEquals("رمضان", arabic[8])

        // Urdu and Persian write the same script and spell the months their own way.
        for (tag in listOf("ur", "fa")) {
            val names = HijriMonthNames.forLocale(Locale.forLanguageTag(tag))
            assertTrue("$tag should be in Arabic script: $names", names.all { ARABIC_SCRIPT.containsMatchIn(it) })
            assertNotEquals(tag, arabic, names)
        }
    }

    /** The app's own English wording, which ICU's "Rabiʻ I" must not replace. */
    @Test
    fun englishKeepsTheNameSpelledOut() {
        assertEquals("Rabi' al-Awwal", HijriMonthNames.of(3, Locale.ENGLISH))
        assertEquals("Dhu al-Hijjah", HijriMonthNames.of(12, Locale.ENGLISH))
    }

    private companion object {
        val SHIPPED_LANGUAGES = listOf(
            "en", "ar", "fr", "nl", "es", "de", "id", "ms", "tr",
            "ur", "fa", "hi", "bn", "ru", "so", "sw", "ha"
        )
        val ARABIC_SCRIPT = Regex("""[؀-ۿ]""")
        val NUMBERED = Regex("""\s[IVX]+$""")
    }
}
