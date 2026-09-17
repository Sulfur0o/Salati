package com.sulfuro.salati.ui.settings

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The languages the app ships, and the three places that have to agree about them.
 *
 * A language needs a `values-<code>` folder for the resource loader, a line in
 * locales_config.xml for the system's per-app language screen, and a row in the in-app
 * picker. Nothing links the three, and each way of getting it wrong fails quietly: without
 * the folder the screen stays English, without the config the system never offers the
 * language, without the picker only someone who knows about the system screen can reach it.
 */
class LanguageCatalogueTest {

    /**
     * Java rewrites three language codes, and the resource loader rewrites with it.
     *
     * `Locale("id").getLanguage()` is `"in"`, so the loader asks for values-in; a values-id
     * folder is never read and every string falls back to English. 1.5.0 shipped Indonesian
     * that way - the picker offered it, the date line localised, and the rest of the screen
     * did not move. Hebrew (he -> iw) and Yiddish (yi -> ji) carry the same trap.
     *
     * Only the folder takes the old code. locales_config and the picker keep the BCP-47 one,
     * which is what [applyAppLanguage] hands to the platform.
     */
    @Test
    fun theRewrittenLanguageCodesUseTheFolderNameAndroidReads() {
        val rewritten = mapOf("he" to "iw", "id" to "in", "yi" to "ji")
        val unread = rewritten.keys.filter { Files.exists(res().resolve("values-$it")) }
        assertEquals(
            "Android reads values-${unread.map { rewritten[it] }}, so these folders are dead",
            emptyList<String>(),
            unread
        )
    }

    /** A translation nobody declared cannot be chosen; a declaration with no translation is English. */
    @Test
    fun everyTranslationIsDeclaredAndEveryDeclarationIsTranslated() {
        val rewritten = mapOf("he" to "iw", "id" to "in", "yi" to "ji")
        val declared = declaredLocales()
            .filterNot { it == BASE_LANGUAGE }
            .map { rewritten[it] ?: it }
            .toSortedSet()
        assertEquals(declared, translationFolders())
    }

    /**
     * The in-app picker is the one most people use - the system screen is buried.
     *
     * It lists the same languages as locales_config, plus the two that need no folder of
     * their own: "" for the system default and the base language itself.
     */
    @Test
    fun theInAppPickerOffersExactlyTheDeclaredLanguages() {
        assertEquals(
            (declaredLocales() + BASE_LANGUAGE + "").toSortedSet(),
            pickerLanguageCodes()
        )
    }

    private fun declaredLocales(): Set<String> =
        Regex("""android:name="([^"]+)"""")
            .findAll(Files.readString(res().resolve("xml/locales_config.xml")))
            .map { it.groupValues[1] }
            .toSet()

    private fun translationFolders(): Set<String> =
        Files.list(res()).use { entries ->
            entries.map { it.fileName.toString() }
                .filter { it.startsWith("values-") && Files.exists(res().resolve("$it/strings.xml")) }
                .map { it.removePrefix("values-") }
                .toList()
        }.toSortedSet()

    private fun pickerLanguageCodes(): Set<String> {
        val source = projectPath("src/main/java/com/sulfuro/salati/ui/settings/SettingsAppearanceCard.kt")
        return Regex(""""([a-z]{0,3})" to stringResource\(R\.string\.settings_language_""")
            .findAll(Files.readString(source))
            .map { it.groupValues[1] }
            .toSet()
            .toSortedSet()
    }

    private fun res(): Path = projectPath("src/main/res")

    private fun projectPath(relative: String): Path {
        val direct = Path.of(relative)
        return if (Files.exists(direct)) direct else Path.of("app").resolve(relative)
    }

    private companion object {
        /** The language in values/strings.xml, which needs no folder of its own. */
        const val BASE_LANGUAGE = "en"
    }
}
