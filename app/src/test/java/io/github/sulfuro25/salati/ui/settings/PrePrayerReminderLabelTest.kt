package io.github.sulfuro25.salati.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sulfuro25.salati.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path

/**
 * The pre-prayer slider runs 0 to 30 in steps of five, and zero is not a five-minute
 * reminder rounded down - `AlarmScheduler` schedules nothing at all for it. The label has
 * to say so, and the non-zero labels have to agree with each language's grammar.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class PrePrayerReminderLabelTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Every stop the slider can actually land on, zero excluded. */
    private val reachableMinutes = listOf(5, 10, 15, 20, 25, 30)

    /**
     * Arabic formats numbers with Arabic-Indic digits, so the label for five minutes
     * reads "٥" and not "5". That is correct rendering, not a missing substitution, so
     * the digits are folded back to ASCII before any assertion looks for them.
     */
    private fun withAsciiDigits(text: String): String {
        val builder = StringBuilder(text.length)
        for (character in text) {
            val arabicIndic = character.code - 0x0660
            val extended = character.code - 0x06F0
            when {
                arabicIndic in 0..9 -> builder.append('0' + arabicIndic)
                extended in 0..9 -> builder.append('0' + extended)
                else -> builder.append(character)
            }
        }
        return builder.toString()
    }

    private fun localized(language: String): Context {
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(java.util.Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration)
    }

    @Test
    fun everyReachableValueHasALabelInEveryLanguage() {
        for (language in listOf("en", "ar", "fr", "nl")) {
            val localized = localized(language)
            for (minutes in reachableMinutes) {
                val label = localized.resources.getQuantityString(
                    R.plurals.settings_reminders_pre_prayer_value, minutes, minutes
                )
                assertTrue(
                    "$language has no label for $minutes minutes, got '$label'",
                    label.isNotBlank() && withAsciiDigits(label).contains(minutes.toString())
                )
            }
        }
    }

    /**
     * Arabic changes the noun with the count: 3 to 10 take the plural form, 11 and above
     * return to the singular. A flat string cannot express that, which is why these are
     * plurals - and this fails if someone ever flattens them back.
     */
    @Test
    fun arabicUsesDifferentWordingForFewAndManyMinutes() {
        val arabic = localized("ar")

        val five = arabic.resources.getQuantityString(
            R.plurals.settings_reminders_pre_prayer_value, 5, 5
        )
        val twenty = arabic.resources.getQuantityString(
            R.plurals.settings_reminders_pre_prayer_value, 20, 20
        )

        assertNotEquals(
            "Arabic must not use one noun form for both 5 and 20 minutes",
            withAsciiDigits(five).replace("5", "#"),
            withAsciiDigits(twenty).replace("20", "#")
        )
        assertTrue("5 minutes should take the plural noun", five.contains("دقائق"))
        assertTrue("20 minutes should take the singular noun", twenty.contains("دقيقة"))
    }

    @Test
    fun zeroIsLabelledAsOffRatherThanAsZeroMinutes() {
        for (language in listOf("en", "ar", "fr", "nl")) {
            val localized = localized(language)
            val off = localized.getString(R.string.settings_reminders_pre_prayer_off)
            val spoken = localized.getString(R.string.settings_reminders_pre_prayer_off_accessibility)

            assertTrue("$language has no off label", off.isNotBlank())
            assertTrue("$language has no spoken off label", spoken.isNotBlank())
            assertFalse(
                "$language announces a zero-minute reminder",
                withAsciiDigits(off).contains("0")
            )
        }
    }

    /**
     * The label and the scheduler have to agree about what zero means. The scheduler
     * guards with `prePrayerMinutes > 0`; the screen must branch on the same value.
     */
    @Test
    fun theScreenAndTheSchedulerAgreeThatZeroMeansNoReminder() {
        val scheduler = source("core/notifications/AlarmScheduler.kt")
        val alarmsCard = source("ui/settings/SettingsAlarmsCard.kt")

        assertTrue(scheduler.contains("settings.prePrayerMinutes > 0"))
        assertTrue(alarmsCard.contains("prePrayerMinutes == 0"))
        assertTrue(alarmsCard.contains("settings_reminders_pre_prayer_off"))
    }

    /**
     * Closing the adhan picker mid-preview used to leave a recitation playing over
     * whatever the user did next, because nothing stopped playback when the sheet left
     * composition.
     */
    @Test
    fun theAdhanPickerSilencesItsOwnPreviewWhenItCloses() {
        val sheet = source("ui/settings/AdhanSoundSheet.kt")

        assertTrue("no teardown for the preview", sheet.contains("DisposableEffect"))
        assertTrue(sheet.contains("AdhanPlaybackService.stop(context)"))
        // Playback state is the service's, not the sheet's: a recording ends by itself.
        assertTrue(sheet.contains("AdhanPlaybackService.nowPlayingId"))
    }

    private fun source(relative: String): String {
        val base = "src/main/java/io/github/sulfuro25/salati/"
        val direct = Path.of(base + relative)
        val path = if (Files.exists(direct)) direct else Path.of("app").resolve(base + relative)
        return String(Files.readAllBytes(path))
    }

    @Test
    fun theSliderStopsAreTheOnesTheseLabelsCover() {
        // 0..30 in five steps is seven stops; the labels above cover the six non-zero
        // ones, and the seventh is "Off".
        val alarmsCard = source("ui/settings/SettingsAlarmsCard.kt")
        assertTrue(alarmsCard.contains("valueRange = 0f..30f"))
        assertEquals(6, reachableMinutes.size)
    }
}
