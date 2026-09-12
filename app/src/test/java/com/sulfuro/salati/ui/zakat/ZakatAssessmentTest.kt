package com.sulfuro.salati.ui.zakat

import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.core.zakat.ZakatGoldItem
import com.sulfuro.salati.core.zakat.ZakatSilverItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the assessment counts, and what it deliberately does not.
 *
 * Two things were added here: trade goods, which all four schools agree are zakatable and
 * which the calculator previously had nowhere to put, and an explicit answer to whether
 * gold and silver are being declared at all - because the majority schools exempt jewellery
 * kept for personal wear, and the app used to assume everyone owed on it.
 */
class ZakatAssessmentTest {

    /** Comfortably over the gold nisab at the default 70/gram, so eligibility is not the variable. */
    private fun settings(
        cash: Double = 10_000.0,
        inventory: Double = 0.0,
        liabilities: Double = 0.0,
        declaresMetals: Boolean? = null,
        gold: List<ZakatGoldItem> = emptyList(),
        silver: List<ZakatSilverItem> = emptyList()
    ) = CalculationSettings().let {
        it.copy(
            zakat = it.zakat.copy(
                cashOnHand = cash,
                businessInventory = inventory,
                liabilities = liabilities,
                declaresMetals = declaresMetals,
                goldItems = gold,
                silverItems = silver
            )
        )
    }

    @Test
    fun tradeGoodsCountTowardsTheTotalAndSoTowardsWhatIsDue() {
        val without = computeAssessment(settings(cash = 10_000.0))
        val with = computeAssessment(settings(cash = 10_000.0, inventory = 4_000.0))

        assertEquals(14_000.0, with.netWealth, 0.001)
        assertEquals(with.netWealth - without.netWealth, 4_000.0, 0.001)
        assertEquals(350.0, with.zakatDue, 0.001)
    }

    /** Inventory is wealth like any other, so a debt still comes off it. */
    @Test
    fun tradeGoodsSitBehindLiabilitiesLikeEverythingElse() {
        val assessment = computeAssessment(
            settings(cash = 5_000.0, inventory = 4_000.0, liabilities = 1_000.0)
        )

        assertEquals(8_000.0, assessment.netWealth, 0.001)
    }

    @Test
    fun tradeGoodsShowUpInTheLiquidSubtotalTheCashStepPrints() {
        val assessment = computeAssessment(settings(cash = 1_000.0, inventory = 2_500.0))

        assertEquals(3_500.0, assessment.liquidAssets, 0.001)
    }

    /**
     * The pieces stay on file when the answer is no, so that changing one's mind costs
     * nothing - but nothing on file counts while the answer is no. Anything else would
     * charge a Maliki, Shafi'i or Hanbali user for jewellery their school exempts.
     */
    @Test
    fun goldOnFileIsNotCountedWhileTheAnswerIsNo() {
        val pieces = listOf(ZakatGoldItem(id = "a", weightGrams = 100.0, karat = 24))

        val declined = computeAssessment(settings(declaresMetals = false, gold = pieces))

        assertFalse(declined.declaresMetals)
        assertEquals(0.0, declined.pureGoldGrams, 0.001)
        assertEquals(0.0, declined.goldValue, 0.001)
        assertEquals(10_000.0, declined.netWealth, 0.001)
    }

    /**
     * Unanswered with nothing on file counts nothing: no one is charged for a question
     * they have not been asked.
     */
    @Test
    fun anUnansweredQuestionWithNoPiecesCountsNothing() {
        val unanswered = computeAssessment(settings(declaresMetals = null))

        assertFalse(unanswered.declaresMetals)
        assertEquals(0.0, unanswered.goldValue, 0.001)
    }

    /**
     * The question is new, so every existing install arrives with no answer on file.
     * Someone who had already listed pieces meant to declare them, and dropping them out
     * of a figure that user has already seen would be a silent change to what they owe.
     */
    @Test
    fun piecesAlreadyOnFileAnswerTheQuestionForAnUpgradingUser() {
        val pieces = listOf(ZakatGoldItem(id = "a", weightGrams = 100.0, karat = 24))

        val upgrading = computeAssessment(settings(declaresMetals = null, gold = pieces))

        assertTrue(upgrading.declaresMetals)
        assertEquals(7_000.0, upgrading.goldValue, 0.001)
    }

    /** And an explicit no still wins over pieces on file. */
    @Test
    fun anExplicitNoOverridesPiecesOnFile() {
        val pieces = listOf(ZakatGoldItem(id = "a", weightGrams = 100.0, karat = 24))

        val declined = computeAssessment(settings(declaresMetals = false, gold = pieces))

        assertFalse(declined.declaresMetals)
        assertEquals(0.0, declined.goldValue, 0.001)
    }

    @Test
    fun sayingYesBringsTheSamePiecesBackIntoTheTotal() {
        val pieces = listOf(ZakatGoldItem(id = "a", weightGrams = 100.0, karat = 24))

        val declared = computeAssessment(settings(declaresMetals = true, gold = pieces))

        assertTrue(declared.declaresMetals)
        assertEquals(100.0, declared.pureGoldGrams, 0.001)
        // 100g of pure gold at the default 70/gram.
        assertEquals(7_000.0, declared.goldValue, 0.001)
        assertEquals(17_000.0, declared.netWealth, 0.001)
    }

    @Test
    fun silverFollowsTheSameAnswerAsGold() {
        val pieces = listOf(ZakatSilverItem(id = "s", weightGrams = 200.0, millesimal = 999))

        val declined = computeAssessment(settings(declaresMetals = false, silver = pieces))
        val declared = computeAssessment(settings(declaresMetals = true, silver = pieces))

        assertEquals(0.0, declined.fineSilverGrams, 0.001)
        assertTrue(declared.fineSilverGrams > 0.0)
        assertTrue(declared.silverValue > declined.silverValue)
    }

    /**
     * Step 1 prices both standards side by side, so both have to be derived whichever one
     * is in force - naming them without their thresholds left the choice unanswerable.
     */
    @Test
    fun bothThresholdsAreDerivedAndTheChosenOneIsTheThresholdInForce() {
        val onGold = computeAssessment(settings())
        val onSilver = computeAssessment(
            CalculationSettings().let {
                it.copy(zakat = it.zakat.copy(standard = STANDARD_SILVER, cashOnHand = 10_000.0))
            }
        )

        // Defaults: 85 g at 70/gram, 595 g at 0.8/gram.
        assertEquals(5_950.0, onGold.goldThreshold, 0.001)
        assertEquals(476.0, onGold.silverThreshold, 0.001)
        assertEquals(onGold.goldThreshold, onGold.nisabThreshold, 0.001)

        // Switching the standard moves the threshold in force, not the pair.
        assertEquals(onGold.goldThreshold, onSilver.goldThreshold, 0.001)
        assertEquals(onGold.silverThreshold, onSilver.silverThreshold, 0.001)
        assertEquals(onSilver.silverThreshold, onSilver.nisabThreshold, 0.001)
    }

    /**
     * The nisab is a threshold priced from the metal markets, not from what the user holds,
     * so skipping the metals step must not move it.
     */
    @Test
    fun decliningMetalsLeavesTheThresholdWhereItWas() {
        val declined = computeAssessment(settings(declaresMetals = false))
        val declared = computeAssessment(settings(declaresMetals = true))

        assertEquals(declared.nisabThreshold, declined.nisabThreshold, 0.001)
        assertTrue(declined.nisabThreshold > 0.0)
    }
}
