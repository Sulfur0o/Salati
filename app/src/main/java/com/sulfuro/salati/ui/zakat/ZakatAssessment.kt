package com.sulfuro.salati.ui.zakat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sulfuro.salati.core.zakat.ZakatCalculator
import com.sulfuro.salati.data.settings.CalculationSettings

internal data class ZakatAssessment(
    val standard: Int,
    val liquidAssets: Double,
    val declaresMetals: Boolean,
    val pureGoldGrams: Double,
    val fineSilverGrams: Double,
    val goldValue: Double,
    val silverValue: Double,
    val grossAssets: Double,
    val liabilities: Double,
    val netWealth: Double,
    val goldThreshold: Double,
    val silverThreshold: Double,
    val nisabThreshold: Double,
    val isEligible: Boolean,
    val zakatDue: Double,
    val pricesMatchCurrency: Boolean
)

@Composable
internal fun rememberZakatAssessment(settings: CalculationSettings): ZakatAssessment =
    remember(settings) { computeAssessment(settings) }

/**
 * Whether gold and silver count, for someone who has not been asked yet.
 *
 * The question is new, so every existing install arrives with no answer on file. Anyone
 * who had already listed pieces plainly meant to declare them, and it would be wrong to
 * quietly drop them from a figure the user has already seen - so having pieces is taken
 * as the answer until they say otherwise. An empty list means the question is genuinely
 * open, and nothing is owed on metals while it stays that way.
 */
internal fun declaresMetalsOrDefault(settings: CalculationSettings): Boolean =
    settings.zakat.declaresMetals
        ?: (settings.zakat.goldItems.isNotEmpty() || settings.zakat.silverItems.isNotEmpty())

/**
 * Pure derivation of the whole assessment, kept out of the composables so the arithmetic
 * can be exercised directly in tests.
 */
internal fun computeAssessment(settings: CalculationSettings): ZakatAssessment {
    val liquid = settings.zakat.cashOnHand +
        settings.zakat.bankBalance +
        settings.zakat.investments +
        settings.zakat.receivables +
        settings.zakat.businessInventory

    // Pieces stay on file when the user chooses to skip, so that changing their mind
    // costs nothing - but nothing they entered counts while the answer is no.
    val declaringMetals = declaresMetalsOrDefault(settings)
    val pureGold = if (declaringMetals) {
        ZakatCalculator.totalPureGoldWeight(settings.zakat.goldItems)
    } else {
        0.0
    }
    val fineSilver = if (declaringMetals) {
        ZakatCalculator.totalFineSilverWeight(settings.zakat.silverItems)
    } else {
        0.0
    }
    val goldValue = ZakatCalculator.valueForPureWeight(pureGold, settings.zakat.goldPrice)
    val silverValue = ZakatCalculator.valueForPureWeight(fineSilver, settings.zakat.silverPrice)

    // Both are derived, not just the chosen one: step 1 shows each standard beside the
    // threshold it produces, so the choice is between two amounts rather than two names.
    val goldThreshold =
        ZakatCalculator.calculateNisabValue(settings.zakat.nisabGram, settings.zakat.goldPrice)
    val silverThreshold =
        ZakatCalculator.calculateNisabValue(settings.zakat.nisabSilverGram, settings.zakat.silverPrice)
    val nisab = if (settings.zakat.standard == STANDARD_SILVER) silverThreshold else goldThreshold

    val result = ZakatCalculator.computeZakat(
        cash = settings.zakat.cashOnHand + settings.zakat.bankBalance,
        goldValue = goldValue,
        silverValue = silverValue,
        otherAssets = settings.zakat.investments +
            settings.zakat.receivables +
            settings.zakat.businessInventory,
        shortTermLiabilities = settings.zakat.liabilities,
        nisabThreshold = nisab
    )

    return ZakatAssessment(
        standard = settings.zakat.standard,
        liquidAssets = liquid,
        declaresMetals = declaringMetals,
        pureGoldGrams = pureGold,
        fineSilverGrams = fineSilver,
        goldValue = goldValue,
        silverValue = silverValue,
        grossAssets = result.totalAssets,
        liabilities = settings.zakat.liabilities,
        netWealth = result.netWealth,
        goldThreshold = goldThreshold,
        silverThreshold = silverThreshold,
        nisabThreshold = result.nisabThreshold,
        isEligible = result.isEligible,
        zakatDue = result.zakatDue,
        pricesMatchCurrency = ZakatCalculator.doPricesMatchCurrency(
            settings.zakat.pricesCurrencyCode,
            settings.zakat.currencyCode
        )
    )
}

// ---------------------------------------------------------------------------
// Step 4 - summary, eligibility and the Hawl record
// ---------------------------------------------------------------------------
