package io.github.sulfuro25.salati.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {
    @Test
    fun emptyAndNullLanguageCodesMapToTheSameLocaleTags() {
        assertEquals("", localeTagsForLanguageCode(null))
        assertEquals("", localeTagsForLanguageCode(""))
        assertEquals("ar", localeTagsForLanguageCode("ar"))
    }

    @Test
    fun identicalApplicationLocalesAreNotReapplied() {
        assertFalse(shouldUpdateApplicationLocales("ar", "ar"))
        assertFalse(shouldUpdateApplicationLocales("", null))
        assertFalse(shouldUpdateApplicationLocales("", ""))
    }

    @Test
    fun languageChangesAreApplied() {
        assertTrue(shouldUpdateApplicationLocales("", "ar"))
        assertTrue(shouldUpdateApplicationLocales("en", "fr"))
        assertTrue(shouldUpdateApplicationLocales("ar", null))
    }
}
