package com.sulfuro.salati.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Below API 33 the chosen language is baked into the Activity's resources by
 * [wrapContextForLanguage], and [applyAppLanguage] decides whether a recreate is
 * needed by comparing against the tag that was actually attached. If those two ever
 * disagree for the same language code, every recreate would request another one and
 * the app would loop, so the agreement is pinned here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30], manifest = Config.NONE)
class AppLanguageContextTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun attachingALanguageRecordsTheTagApplyAppLanguageComparesAgainst() {
        for (code in listOf(null, "", "en", "ar", "fr", "nl")) {
            wrapContextForLanguage(context, code)
            assertEquals(
                "attached tag must match the guard for code=$code",
                localeTagsForLanguageCode(code),
                attachedLanguageTag
            )
        }
    }

    @Test
    fun reattachingTheSameLanguageConvergesInsteadOfLooping() {
        wrapContextForLanguage(context, "fr")
        val afterFirstAttach = attachedLanguageTag

        // A recreate re-runs attachBaseContext with the same stored choice; the guard
        // must then report "nothing to do" rather than asking for another recreate.
        wrapContextForLanguage(context, "fr")

        assertEquals(afterFirstAttach, attachedLanguageTag)
        assertEquals(localeTagsForLanguageCode("fr"), attachedLanguageTag)
    }

    @Test
    fun regionQualifiedSystemLocaleStillMatchesTheSystemDefaultChoice() {
        // "System default" is stored as null. The guard compares language *codes*, not
        // resolved locale tags, so a system locale such as fr-FR cannot be mistaken for
        // a pending change and trigger a recreate on every pass.
        wrapContextForLanguage(context, null)
        assertEquals("", attachedLanguageTag)
        assertEquals(localeTagsForLanguageCode(null), attachedLanguageTag)
    }

    @Test
    fun wrappedContextCarriesTheRequestedLocale() {
        val wrapped = wrapContextForLanguage(context, "ar")
        assertEquals("ar", wrapped.resources.configuration.locales[0].language)
    }

    @Test
    fun systemDefaultReturnsTheUnwrappedContext() {
        val wrapped = wrapContextForLanguage(context, null)
        assertTrue("system default must not wrap the context", wrapped === context)
    }
}
