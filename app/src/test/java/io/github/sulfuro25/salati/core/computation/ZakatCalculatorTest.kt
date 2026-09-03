package io.github.sulfuro25.salati.core.computation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZakatCalculatorTest {

    @Test
    fun parseAmount_parsesWesternDigits() {
        assertEquals(1234.56, ZakatCalculator.parseAmount("1234.56")!!, 0.001)
        assertEquals(1000.0, ZakatCalculator.parseAmount("1,000.00")!!, 0.001)
    }

    @Test
    fun parseAmount_parsesCommaDecimalSeparator() {
        assertEquals(1234.56, ZakatCalculator.parseAmount("1234,56")!!, 0.001)
        assertEquals(50.5, ZakatCalculator.parseAmount("50,5")!!, 0.001)
    }

    @Test
    fun parseAmount_parsesArabicIndicDigits() {
        // ١٠٠٠ = 1000
        val parsed = ZakatCalculator.parseAmount("١٠٠٠")
        assertNotNull(parsed)
        assertEquals(1000.0, parsed!!, 0.001)

        // ١٢٣٤.٥ = 1234.5
        val parsedDecimal = ZakatCalculator.parseAmount("١٢٣٤.٥")
        assertNotNull(parsedDecimal)
        assertEquals(1234.5, parsedDecimal!!, 0.001)
    }

    @Test
    fun parseAmount_rejectsInvalidInputs() {
        assertNull(ZakatCalculator.parseAmount(""))
        assertNull(ZakatCalculator.parseAmount("   "))
        assertNull(ZakatCalculator.parseAmount("abc"))
        assertNull(ZakatCalculator.parseAmount("-500"))
        assertNull(ZakatCalculator.parseAmount("12.34.56"))
    }

    @Test
    fun doPricesMatchCurrency_validation() {
        assertTrue(ZakatCalculator.doPricesMatchCurrency("", "EUR"))
        assertTrue(ZakatCalculator.doPricesMatchCurrency("EUR", "EUR"))
        assertTrue(ZakatCalculator.doPricesMatchCurrency("eur", "EUR"))
        assertFalse(ZakatCalculator.doPricesMatchCurrency("USD", "EUR"))
    }

    @Test
    fun computeZakat_belowNisab_returnsZero() {
        val result = ZakatCalculator.computeZakat(
            cash = 1000.0,
            goldValue = 500.0,
            silverValue = 0.0,
            otherAssets = 0.0,
            shortTermLiabilities = 200.0,
            nisabThreshold = 5000.0
        )
        assertEquals(1500.0, result.totalAssets, 0.001)
        assertEquals(1300.0, result.netWealth, 0.001)
        assertFalse(result.isEligible)
        assertEquals(0.0, result.zakatDue, 0.001)
    }

    @Test
    fun computeZakat_aboveNisab_calculatesTwoPointFivePercent() {
        val result = ZakatCalculator.computeZakat(
            cash = 10000.0,
            goldValue = 0.0,
            silverValue = 0.0,
            otherAssets = 0.0,
            shortTermLiabilities = 0.0,
            nisabThreshold = 5000.0
        )
        assertTrue(result.isEligible)
        assertEquals(250.0, result.zakatDue, 0.001)
    }
}
