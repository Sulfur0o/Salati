package com.sulfuro.salati.core.zakat

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

    // ---------------------------------------------------------------------
    // Multi-carat normalisation
    // ---------------------------------------------------------------------

    @Test
    fun normalizePureGoldWeight_scalesByKaratOutOf24() {
        // Pure Weight = Weight x (Carat / 24)
        assertEquals(10.0, ZakatCalculator.normalizePureGoldWeight(10.0, 24), 0.0001)
        assertEquals(9.1667, ZakatCalculator.normalizePureGoldWeight(10.0, 22), 0.0001)
        assertEquals(8.75, ZakatCalculator.normalizePureGoldWeight(10.0, 21), 0.0001)
        assertEquals(7.5, ZakatCalculator.normalizePureGoldWeight(10.0, 18), 0.0001)
        assertEquals(5.8333, ZakatCalculator.normalizePureGoldWeight(10.0, 14), 0.0001)
    }

    @Test
    fun normalizePureGoldWeight_rejectsUnusableInput() {
        assertEquals(0.0, ZakatCalculator.normalizePureGoldWeight(-5.0, 24), 0.0001)
        assertEquals(0.0, ZakatCalculator.normalizePureGoldWeight(0.0, 24), 0.0001)
        assertEquals(0.0, ZakatCalculator.normalizePureGoldWeight(Double.NaN, 24), 0.0001)
        // An unsupported karat must contribute nothing rather than be treated as pure.
        assertEquals(0.0, ZakatCalculator.normalizePureGoldWeight(10.0, 19), 0.0001)
    }

    @Test
    fun totalPureGoldWeight_sumsMixedCaratsOnA24kBasis() {
        val items = listOf(
            ZakatGoldItem(id = "a", karat = 24, weightGrams = 20.0),  // 20.0 pure
            ZakatGoldItem(id = "b", karat = 18, weightGrams = 40.0),  // 30.0 pure
            ZakatGoldItem(id = "c", karat = 21, weightGrams = 8.0)    //  7.0 pure
        )
        assertEquals(57.0, ZakatCalculator.totalPureGoldWeight(items), 0.0001)
    }

    @Test
    fun totalPureGoldWeight_ignoresIncompleteItemsInAnOtherwiseValidList() {
        val items = listOf(
            ZakatGoldItem(id = "a", karat = 24, weightGrams = 12.0),
            ZakatGoldItem(id = "blank", karat = 24, weightGrams = 0.0),
            ZakatGoldItem(id = "bad-karat", karat = 999, weightGrams = 50.0)
        )
        assertEquals(12.0, ZakatCalculator.totalPureGoldWeight(items), 0.0001)
    }

    @Test
    fun totalPureGoldWeight_ofNoItemsIsZero() {
        assertEquals(0.0, ZakatCalculator.totalPureGoldWeight(emptyList()), 0.0001)
    }

    @Test
    fun normalizeFineSilverWeight_scalesByMillesimalFineness() {
        // "Fine" silver is 999 parts per thousand, not 1000, so it is 99.9% by weight.
        assertEquals(99.9, ZakatCalculator.normalizeFineSilverWeight(100.0, 999), 0.0001)
        assertEquals(92.5, ZakatCalculator.normalizeFineSilverWeight(100.0, 925), 0.0001)
        // An unsupported hallmark contributes nothing rather than being assumed pure.
        assertEquals(0.0, ZakatCalculator.normalizeFineSilverWeight(100.0, 800), 0.0001)
    }

    @Test
    fun totalFineSilverWeight_sumsSterlingAndFineTogether() {
        val items = listOf(
            ZakatSilverItem(id = "a", millesimal = 999, weightGrams = 200.0), // 199.8 fine
            ZakatSilverItem(id = "b", millesimal = 925, weightGrams = 400.0)  // 370.0 fine
        )
        assertEquals(569.8, ZakatCalculator.totalFineSilverWeight(items), 0.0001)
    }

    @Test
    fun valueForPureWeight_multipliesNormalisedWeightByTheQuotedRate() {
        assertEquals(4000.0, ZakatCalculator.valueForPureWeight(50.0, 80.0), 0.0001)
        assertEquals(0.0, ZakatCalculator.valueForPureWeight(50.0, 0.0), 0.0001)
        assertEquals(0.0, ZakatCalculator.valueForPureWeight(0.0, 80.0), 0.0001)
    }

    @Test
    fun mixedCaratJewelleryIsValuedOnItsPureContentNotItsGrossWeight() {
        // 100 g of 18k is 75 g of pure gold, so at 70/g it is worth 5250, not 7000.
        val items = listOf(ZakatGoldItem(id = "a", karat = 18, weightGrams = 100.0))
        val pure = ZakatCalculator.totalPureGoldWeight(items)
        assertEquals(75.0, pure, 0.0001)
        assertEquals(5250.0, ZakatCalculator.valueForPureWeight(pure, 70.0), 0.0001)
    }
}
