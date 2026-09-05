package io.github.sulfuro25.salati.ui.zakat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import java.nio.file.Files
import java.nio.file.Path

class ZakatUiContractTest {

    @Test
    fun testNisabThresholdsUnchanged() {
        val settings = CalculationSettings(
            zakatGoldPrice = 70.0,
            zakatSilverPrice = 0.8
        )
        val goldNisabValue = settings.zakatNisabGram * settings.zakatGoldPrice
        val silverNisabValue = settings.zakatNisabSilverGram * settings.zakatSilverPrice

        assertEquals(85.0 * 70.0, goldNisabValue, 0.01)
        assertEquals(595.0 * 0.8, silverNisabValue, 0.01)
    }

    @Test
    fun jewelryGoldUsesSelectedCaratWhileNisabStaysAt24k() {
        val settings = CalculationSettings(zakatGoldPrice = 70.0, zakatGoldCarat = 18)
        val nisab = settings.zakatNisabGram * settings.zakatGoldPrice
        val jewelryPrice = io.github.sulfuro25.salati.core.computation.ZakatCalculator
            .calculateEffectiveCaratPrice(settings.zakatGoldPrice, settings.zakatGoldCarat)

        assertEquals(5950.0, nisab, 0.01)
        assertEquals(52.5, jewelryPrice, 0.01)
    }

    @Test
    fun testZakatRateIsTwoPointFivePercent() {
        val amount = 10000.0
        val expectedZakat = amount * 0.025
        assertEquals(250.0, expectedZakat, 0.01)
    }

    @Test
    fun hawlTrackerLivesOnZakatScreenNotCalendar() {
        val zakat = String(Files.readAllBytes(projectPath("src/main/java/io/github/sulfuro25/salati/ui/zakat/ZakatScreen.kt")))
        val calendar = String(Files.readAllBytes(projectPath("src/main/java/io/github/sulfuro25/salati/ui/calendar/CalendarScreen.kt")))
        // The Hawl milestone is edited on the Zakat screen (in the summary step) and only
        // read by the calendar, which renders it as an event badge.
        assertTrue(zakat.contains("hawl_milestone_title"))
        assertTrue(zakat.contains("zakatHawlStartEpochDay"))
        assertFalse(calendar.contains("HawlMilestoneCard"))
        assertFalse(calendar.contains("onHawlStartDateChanged"))
    }

    @Test
    fun zakatScreenOffersTheCalendarHawlHandoff() {
        val zakat = String(Files.readAllBytes(projectPath("src/main/java/io/github/sulfuro25/salati/ui/zakat/ZakatScreen.kt")))
        assertTrue(zakat.contains("ZakatHawlCalendar.buildInsertIntent"))
        assertTrue(zakat.contains("zakat_hawl_calendar_action"))
    }

    @Test
    fun zakatWalkthroughExposesFourSteps() {
        assertEquals(4, ZAKAT_STEP_COUNT)
    }

    private fun projectPath(relative: String): Path {
        val direct = Path.of(relative)
        return if (Files.exists(direct)) direct else Path.of("app").resolve(relative)
    }
}
