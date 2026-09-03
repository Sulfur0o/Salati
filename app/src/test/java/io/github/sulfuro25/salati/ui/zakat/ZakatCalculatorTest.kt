package io.github.sulfuro25.salati.ui.zakat

import org.junit.Assert.assertEquals
import org.junit.Test

class ZakatCalculatorTest {

    @Test
    fun testZakatCalculation() {
        val baseGoldPrice = 70.0 // EUR per gram (24k)
        val selectedCarat = 21   // 21 carats gold
        val caratMultiplier = selectedCarat / 24.0 // 0.875
        val effectiveGoldPrice = baseGoldPrice * caratMultiplier // 61.25 €/g
        
        val goldNisabGrams = 85.0
        val goldNisabThreshold = effectiveGoldPrice * goldNisabGrams // 5206.25 €

        // Test Cash assets below gold Nisab
        val cashWealthBelow = 5000.0
        val cashZakatBelow = if (cashWealthBelow >= goldNisabThreshold) cashWealthBelow * 0.025 else 0.0
        assertEquals(0.0, cashZakatBelow, 0.001)

        // Test Cash assets above gold Nisab
        val cashWealthAbove = 10000.0
        val cashZakatAbove = if (cashWealthAbove >= goldNisabThreshold) cashWealthAbove * 0.025 else 0.0
        assertEquals(250.0, cashZakatAbove, 0.001)

        // Test Gold Jewelry weight below 85g threshold
        val goldWeightBelow = 50.0 // grams
        val isGoldJewelryNisabReached1 = goldWeightBelow >= goldNisabGrams
        val goldJewelryZakat1 = if (isGoldJewelryNisabReached1) (goldWeightBelow * effectiveGoldPrice) * 0.025 else 0.0
        assertEquals(0.0, goldJewelryZakat1, 0.001)

        // Test Gold Jewelry weight equal or above 85g threshold
        val goldWeightAbove = 100.0 // grams
        val isGoldJewelryNisabReached2 = goldWeightAbove >= goldNisabGrams
        val goldJewelryZakat2 = if (isGoldJewelryNisabReached2) (goldWeightAbove * effectiveGoldPrice) * 0.025 else 0.0
        // Expected value: 100g * 61.25 €/g = 6125.0 €. Zakat = 6125.0 * 2.5% = 153.125 €
        assertEquals(153.125, goldJewelryZakat2, 0.001)

        // Test Silver Jewelry weight above 595g threshold
        val silverPrice = 0.8 // EUR per gram
        val silverWeightAbove = 1000.0 // grams
        val silverNisabGrams = 595.0
        val isSilverJewelryNisabReached = silverWeightAbove >= silverNisabGrams
        val silverJewelryZakat = if (isSilverJewelryNisabReached) (silverWeightAbove * silverPrice) * 0.025 else 0.0
        // Expected value: 1000g * 0.8 €/g = 800.0 €. Zakat = 800.0 * 2.5% = 20.0 €
        assertEquals(20.0, silverJewelryZakat, 0.001)
    }

    @Test
    fun testAggregateWealthReachingNisab() {
        val pureGoldPrice = 70.0
        val nisabValue = 85.0 * pureGoldPrice // 5950.0 €
        val cash = 4000.0
        val goldWeight = 50.0 // 50g * 70 = 3500.0 €
        val goldValue = goldWeight * pureGoldPrice
        val totalWealth = cash + goldValue // 7500.0 € > 5950.0 € (Nisab reached)

        val isNisabReached = totalWealth >= nisabValue
        val totalZakat = if (isNisabReached) totalWealth * 0.025 else 0.0

        assertEquals(true, isNisabReached)
        assertEquals(187.5, totalZakat, 0.001)
    }
}
