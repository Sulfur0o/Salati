package com.sulfuro.salati.ui.zakat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sulfuro.salati.core.zakat.zakatHawlDueDate
import com.sulfuro.salati.core.zakat.zakatHawlStartDate
import com.sulfuro.salati.data.settings.CalculationSettings
import java.time.LocalDate
import java.nio.file.Files
import java.nio.file.Path
import com.sulfuro.salati.data.settings.ZakatPreferences

class ZakatUiContractTest {

    @Test
    fun testNisabThresholdsUnchanged() {
        val settings = CalculationSettings(zakat = ZakatPreferences(goldPrice = 70.0, silverPrice = 0.8))
        val goldNisabValue = settings.zakat.nisabGram * settings.zakat.goldPrice
        val silverNisabValue = settings.zakat.nisabSilverGram * settings.zakat.silverPrice

        assertEquals(85.0 * 70.0, goldNisabValue, 0.01)
        assertEquals(595.0 * 0.8, silverNisabValue, 0.01)
    }

    @Test
    fun jewelryGoldIsValuedByPureWeightWhileNisabStaysAt24k() {
        // Nisab is 85 g of *pure* gold, so it is priced at the 24k rate however impure
        // the user's own jewellery happens to be.
        val settings = CalculationSettings(zakat = ZakatPreferences(goldPrice = 70.0))
        val nisab = settings.zakat.nisabGram * settings.zakat.goldPrice
        val calculator = com.sulfuro.salati.core.zakat.ZakatCalculator
        val pureWeight = calculator.normalizePureGoldWeight(weightGrams = 100.0, karat = 18)
        val jewelryValue = calculator.valueForPureWeight(pureWeight, settings.zakat.goldPrice)

        assertEquals(5950.0, nisab, 0.01)
        assertEquals(75.0, pureWeight, 0.01)
        assertEquals(5250.0, jewelryValue, 0.01)
    }

    @Test
    fun testZakatRateIsTwoPointFivePercent() {
        val amount = 10000.0
        val expectedZakat = amount * 0.025
        assertEquals(250.0, expectedZakat, 0.01)
    }

    @Test
    fun hawlTrackerLivesOnZakatScreenNotCalendar() {
        val zakat = String(Files.readAllBytes(projectPath("src/main/java/com/sulfuro/salati/ui/zakat/ZakatScreen.kt")))
        val calendar = String(Files.readAllBytes(projectPath("src/main/java/com/sulfuro/salati/ui/calendar/CalendarScreen.kt")))
        // The Hawl milestone is edited on the Zakat screen (in the summary step) and only
        // read by the calendar, which renders it as an event badge.
        assertTrue(zakat.contains("zakat_hawl_next_label"))
        assertTrue(zakat.contains("hawlStartEpochDay"))
        assertFalse(calendar.contains("HawlMilestoneCard"))
        assertFalse(calendar.contains("onHawlStartDateChanged"))
    }

    @Test
    fun zakatScreenOffersTheCalendarHawlHandoff() {
        val zakat = String(Files.readAllBytes(projectPath("src/main/java/com/sulfuro/salati/ui/zakat/ZakatScreen.kt")))
        assertTrue(zakat.contains("ZakatHawlCalendar.buildInsertIntent"))
        // The hand-off is the last step's primary action now, in the bar at the foot of
        // the screen - which is where a disabled Next used to sit with nothing to do.
        assertTrue(zakat.contains("zakat_hawl_remind"))
    }

    @Test
    fun zakatWalkthroughExposesFourSteps() {
        assertEquals(4, ZAKAT_STEP_COUNT)
    }

    /**
     * The header is the one place a step is named. It used to be named three times on
     * every screen - once by the progress line, once by a heading directly under it, and
     * once more by a paragraph explaining the heading - above a "Zakat" title the tab bar
     * was already carrying.
     */
    @Test
    fun aStepIsNamedOnceAndTheAmountDueRidesAlongWithIt() {
        val zakat = String(Files.readAllBytes(projectPath("src/main/java/com/sulfuro/salati/ui/zakat/ZakatScreen.kt")))
        val steps = String(Files.readAllBytes(projectPath("src/main/java/com/sulfuro/salati/ui/zakat/ZakatSteps.kt")))

        assertTrue("the header carries the running total", zakat.contains("zakat_due_label"))
        assertTrue("and says so when nothing is owed", zakat.contains("zakat_due_below_nisab"))
        assertFalse("the tab bar already says Zakat", zakat.contains("R.string.zakat_title"))
        assertFalse("no per-step heading", steps.contains("zakat_cash_heading"))
        assertFalse("no per-step explainer", steps.contains("zakat_standard_explainer"))

        // The progress segments are the way back to a step you have already passed - the
        // summary in particular, which was otherwise three taps of Next away.
        assertTrue("segments are jump targets", zakat.contains("zakat_step_jump"))
        assertTrue("and announce themselves as tabs", zakat.contains("Role.Tab"))
    }

    /**
     * Nothing owed is not a deduction. The breakdown reads as arithmetic, so a red
     * "- EUR 0.00" on the debts line announced a subtraction that never happened.
     */
    @Test
    fun theDebtsLineOnlySubtractsWhenThereIsSomethingToSubtract() {
        val noDebts = computeAssessment(
            CalculationSettings().let { it.copy(zakat = it.zakat.copy(cashOnHand = 10_000.0)) }
        )
        val withDebts = computeAssessment(
            CalculationSettings().let {
                it.copy(zakat = it.zakat.copy(cashOnHand = 10_000.0, liabilities = 400.0))
            }
        )

        assertEquals(0.0, noDebts.liabilities, 0.001)
        assertEquals(noDebts.grossAssets, noDebts.netWealth, 0.001)
        assertEquals(400.0, withDebts.liabilities, 0.001)
        assertEquals(withDebts.grossAssets - 400.0, withDebts.netWealth, 0.001)

        val zakat = String(Files.readAllBytes(projectPath("src/main/java/com/sulfuro/salati/ui/zakat/ZakatScreen.kt")))
        assertTrue("the minus sign is conditional", zakat.contains("if (owesSomething)"))
    }

    /**
     * The Hawl picker asks for the day Zakat next falls due, and opens a lunar year out.
     *
     * It used to ask for the start - the day wealth reached Nisab - while the row above it
     * showed the due date in bold and the button beside it said "Change". Worse, an unset
     * Hawl opened the picker on today, which is the one date that cannot be the answer, so
     * setting a reminder meant paging forward twelve months by hand every time.
     */
    @Test
    fun theHawlPickerAsksForTheDueDateAndOpensALunarYearOut() {
        val today = LocalDate.of(2026, 9, 12)

        // 354 days, not a calendar year.
        assertEquals(LocalDate.of(2027, 9, 1), zakatHawlDueDate(today))

        // Whatever the user picks has to survive being stored as its start and read back.
        val picked = LocalDate.of(2027, 9, 6)
        assertEquals(LocalDate.of(2026, 9, 17), zakatHawlStartDate(picked))
        assertEquals(picked, zakatHawlDueDate(zakatHawlStartDate(picked)))

        val zakat = String(Files.readAllBytes(projectPath("src/main/java/com/sulfuro/salati/ui/zakat/ZakatScreen.kt")))
        assertTrue(
            "an unset Hawl opens a lunar year out",
            zakat.contains("initialDate = hawlDue ?: ZakatHawlCalendar.dueDateFrom(LocalDate.now())")
        )
        assertTrue("and what is picked is stored as its start", zakat.contains("zakatHawlStartDate"))
    }

    private fun projectPath(relative: String): Path {
        val direct = Path.of(relative)
        return if (Files.exists(direct)) direct else Path.of("app").resolve(relative)
    }
}
