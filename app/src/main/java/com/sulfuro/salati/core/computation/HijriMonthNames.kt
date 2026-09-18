package com.sulfuro.salati.core.computation

import android.icu.text.DateFormatSymbols
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * What to call each Hijri month in the language the app is being read in.
 *
 * The names used to be twelve English transliterations with no alternative, so an app
 * translated into sixteen languages still said "Rabi' al-Thani" on the dashboard, in the
 * widget and in every prayer notification - in the middle of an otherwise Arabic or Urdu
 * screen, in Latin script, read left to right.
 *
 * Android has known these names since API 24, so none of them are translated here and none
 * had to be: [namesFor] asks ICU for the Islamic calendar's months and the platform answers
 * in the reader's language.
 *
 * Deliberately ICU and not `java.time`. `DateTimeFormatter.ofPattern("MMMM")` over
 * [java.time.chrono.HijrahChronology] gives the same names on the JVM and so passes a unit
 * test, but the library that backs java.time on a phone carries no Islamic month data and
 * formats the month as its number - a dashboard reading "7 4 1448". The instrumented
 * HijriMonthNamesDeviceTest is what holds this to the platform that actually answers.
 */
object HijriMonthNames {

    /**
     * The app's own English names - its base-language copy, like values/strings.xml.
     *
     * ICU's English is not used for them. It gives the scholarly short forms, "Rabiʻ I" and
     * "Jumada II", where an English-reading Muslim expects the name written out; those same
     * forms are what a language ICU cannot help falls back to, month by month, in [namesFor].
     */
    private val english = listOf(
        "Muharram",
        "Safar",
        "Rabi' al-Awwal",
        "Rabi' al-Thani",
        "Jumada al-Awwal",
        "Jumada al-Thani",
        "Rajab",
        "Sha'ban",
        "Ramadan",
        "Shawwal",
        "Dhu al-Qi'dah",
        "Dhu al-Hijjah"
    )

    private val byLanguageTag = ConcurrentHashMap<String, List<String>>()

    /** The name of [monthNumber], counting Muharram as 1. Empty if it is not a month. */
    fun of(monthNumber: Int, locale: Locale = Locale.getDefault()): String =
        forLocale(locale).getOrNull(monthNumber - 1).orEmpty()

    /** The twelve months in order, in [locale]'s language. */
    fun forLocale(locale: Locale): List<String> =
        if (locale.language == Locale.ENGLISH.language) english
        else byLanguageTag.getOrPut(locale.toLanguageTag()) { namesFor(locale) }

    /**
     * A language's own names where it has them, and English's where it does not.
     *
     * ICU covers these months unevenly. Arabic, Turkish, Indonesian, Urdu, Persian, Hindi,
     * Bengali, Russian, Somali, French, Dutch and Malay name all twelve; German has
     * Radschab and Dhu l-Hiddscha of its own but inherits the root's "Rabiʻ I" for the
     * months that come in pairs; Spanish, Swahili and Hausa inherit nearly all of them.
     * Falling back month by month rather than language by language keeps every real
     * translation and still never shows a reader a numeral where a name should be.
     */
    private fun namesFor(locale: Locale): List<String> {
        // A month's name should never be the reason a prayer time fails to draw.
        val fromIcu = runCatching {
            DateFormatSymbols(IslamicCalendar(), ULocale.forLocale(locale)).months
        }.getOrNull()

        return List(MONTHS_IN_YEAR) { index ->
            fromIcu?.getOrNull(index)
                ?.takeUnless { it.isBlank() || isNumbered(it) }
                ?: english[index]
        }
    }

    /** `Rabiʻ I`, `Jumada II` - the root's way of counting a pair, not a name. */
    private fun isNumbered(name: String): Boolean = NUMBERED_MONTH.containsMatchIn(name)

    private val NUMBERED_MONTH = Regex("""\s[IVX]+$""")

    private const val MONTHS_IN_YEAR = 12
}
