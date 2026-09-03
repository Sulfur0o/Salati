package io.github.sulfuro25.salati.navigation

import io.github.sulfuro25.salati.Calendar
import io.github.sulfuro25.salati.Dashboard
import io.github.sulfuro25.salati.Settings
import io.github.sulfuro25.salati.Zakat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NavigationUiContractTest {

    @Test
    fun `navigation destinations exist`() {
        val d = Dashboard
        val c = Calendar
        val z = Zakat
        val s = Settings

        assertTrue(d.javaClass.simpleName == "Dashboard")
        assertTrue(c.javaClass.simpleName == "Calendar")
        assertTrue(z.javaClass.simpleName == "Zakat")
        assertTrue(s.javaClass.simpleName == "Settings")
    }

    @Test
    fun `no French terminology remains in strings or Navigation`() {
        val stringsFile = File("src/main/res/values/strings.xml")
        if (stringsFile.exists()) {
            val content = stringsFile.readText()
            assertFalse("strings.xml contains Chourouk", content.contains("Chourouk", ignoreCase = true))
            assertFalse("strings.xml contains Duhr", content.contains("Duhr", ignoreCase = true))
            assertTrue("strings.xml contains Dhuhr", content.contains("Dhuhr"))
            assertTrue("strings.xml contains Sunrise", content.contains("Sunrise"))
            assertTrue("strings.xml contains Maghrib", content.contains("Maghrib"))
            assertTrue("strings.xml contains Fajr", content.contains("Fajr"))
            assertTrue("strings.xml contains Asr", content.contains("Asr"))
            assertTrue("strings.xml contains Isha", content.contains("Isha"))
        }

        val navFile = File("src/main/java/io/github/sulfuro25/salati/Navigation.kt")
        if (navFile.exists()) {
            val content = navFile.readText()
            assertFalse("Navigation.kt contains hardcoded Daily", content.contains("\"Daily\""))
            assertFalse("Navigation.kt contains hardcoded Monthly", content.contains("\"Monthly\""))
            assertFalse("Navigation.kt contains hardcoded Zakat", content.contains("\"Zakat\""))
            assertFalse("Navigation.kt contains hardcoded Settings", content.contains("\"Settings\""))
            assertFalse("Navigation.kt uses Color.Transparent for selection", content.contains("indicatorColor = Color.Transparent"))
        }
    }
}
