package com.sulfuro.salati.ui.zakat

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sulfuro.salati.R
import com.sulfuro.salati.core.zakat.ZakatGoldItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Zakat inputs, where a mistake is expensive: these fields decide how much someone
 * believes they owe, and the assessment is only revisited a lunar year later.
 */
@RunWith(AndroidJUnit4::class)
class ZakatComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun string(id: Int, arg: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, arg)

    /** Every amount row carries its label as the field's own description. */
    private fun scrolled(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                content()
            }
        }
    }

    /**
     * A part-typed amount has to survive being typed. Pushing the field's text back down
     * from the parsed value would rewrite "12." to "12.00" under the cursor mid-entry.
     */
    @Test
    fun aPartlyTypedAmountStaysOnScreenAndTheParsedValueIsPushedUp() {
        val pushed = mutableListOf<Double>()

        composeTestRule.setContent {
            MoneyRow(
                label = "Cash",
                initialValue = 0.0,
                currencySymbol = "€",
                onValueChange = { pushed += it }
            )
        }

        composeTestRule.onNodeWithContentDescription("Cash").performTextInput("12.")

        composeTestRule.onNodeWithText("12.").assertIsDisplayed()
        assertEquals(12.0, pushed.last(), 0.0001)
    }

    /** Clearing the row means zero, not "leave the last value in place". */
    @Test
    fun clearingAnAmountReportsZero() {
        val pushed = mutableListOf<Double>()

        composeTestRule.setContent {
            MoneyRow(
                label = "Cash",
                initialValue = 40.0,
                currencySymbol = "€",
                onValueChange = { pushed += it }
            )
        }

        composeTestRule.onNodeWithContentDescription("Cash").performTextClearance()

        assertEquals(0.0, pushed.last(), 0.0001)
    }

    /**
     * A piece is one line until it is opened.
     *
     * Every piece used to show a title, a description field repeating that title, a
     * "Purity" label, a chip row and a weight field at once, so two pieces filled the
     * screen and the totals they added up to were never in sight.
     */
    @Test
    fun aPieceIsOneLineUntilItIsOpened() {
        scrolled {
            ZakatMetalsStep(
                declaresMetals = true,
                onDeclaresMetalsChange = {},
                goldItems = listOf(
                    ZakatGoldItem(id = "g1", label = "Ring", karat = 24, weightGrams = 12.5)
                ),
                silverItems = emptyList(),
                totalPureGoldText = "12.50 g",
                totalFineSilverText = "0.00 g",
                onAddGoldItem = {},
                onUpdateGoldItem = {},
                onEditGoldItemText = {},
                onRemoveGoldItem = {},
                onAddSilverItem = {},
                onUpdateSilverItem = {},
                onEditSilverItemText = {},
                onRemoveSilverItem = {}
            )
        }

        composeTestRule.onNodeWithText("Ring").assertIsDisplayed()
        // Closed: no editing controls, so the list stays scannable.
        composeTestRule.onNodeWithContentDescription(string(R.string.zakat_item_weight))
            .assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(string(R.string.zakat_item_remove))
            .assertDoesNotExist()

        composeTestRule.onNodeWithText("Ring").performClick()

        composeTestRule.onNodeWithContentDescription(string(R.string.zakat_item_weight))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.zakat_purity_18k)).assertIsDisplayed()
    }

    /** A piece you have just added is empty, so it opens itself rather than waiting for a tap. */
    @Test
    fun aPieceJustAddedOpensItself() {
        composeTestRule.setContent {
            var items by remember {
                mutableStateOf(listOf(ZakatGoldItem(id = "g1", label = "Ring", weightGrams = 12.5)))
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                ZakatMetalsStep(
                    declaresMetals = true,
                    onDeclaresMetalsChange = {},
                    goldItems = items,
                    silverItems = emptyList(),
                    totalPureGoldText = "12.50 g",
                    totalFineSilverText = "0.00 g",
                    onAddGoldItem = { items = items + ZakatGoldItem(id = "g2") },
                    onUpdateGoldItem = {},
                    onEditGoldItemText = {},
                    onRemoveGoldItem = {},
                    onAddSilverItem = {},
                    onUpdateSilverItem = {},
                    onEditSilverItemText = {},
                    onRemoveSilverItem = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.zakat_item_weight))
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(string(R.string.zakat_add_gold_item))
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithContentDescription(string(R.string.zakat_item_weight))
            .assertIsDisplayed()
    }

    /**
     * A name has to survive being typed.
     *
     * Label edits take the coalesced write path, which holds the stored settings unchanged
     * until typing stops - so a field bound straight to the stored value was handed its own
     * character back as the empty string on the very next frame, and nothing could be typed
     * into it at all. The list below never changes, which is exactly the condition that
     * exposed it.
     */
    @Test
    fun aPieceCanBeNamed() {
        val whileTyping = mutableListOf<ZakatGoldItem>()

        scrolled {
            ZakatMetalsStep(
                declaresMetals = true,
                onDeclaresMetalsChange = {},
                goldItems = listOf(ZakatGoldItem(id = "g1", weightGrams = 12.5)),
                silverItems = emptyList(),
                totalPureGoldText = "12.50 g",
                totalFineSilverText = "0.00 g",
                onAddGoldItem = {},
                onUpdateGoldItem = {},
                onEditGoldItemText = { whileTyping += it },
                onRemoveGoldItem = {},
                onAddSilverItem = {},
                onUpdateSilverItem = {},
                onEditSilverItemText = {},
                onRemoveSilverItem = {}
            )
        }

        composeTestRule.onNodeWithText(string(R.string.zakat_item_default_label, 1)).performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.zakat_item_label))
            .performTextInput("Necklace")

        // Every character, not just the last one to arrive.
        composeTestRule.onNodeWithText("Necklace").assertIsDisplayed()
        assertEquals("Necklace", whileTyping.last().label)
    }

    /**
     * The two write paths must not be swapped.
     *
     * Typing goes to onEditGoldItemText, which the screen coalesces and flushes once
     * typing stops - routing it to the immediate callback would serialise and fsync the
     * whole settings document on every character. A purity chip is a tap and takes the
     * immediate path, because the pure-weight line under it has to update at once; if it
     * were coalesced the chip would appear to do nothing for a moment.
     */
    @Test
    fun typedFieldsAreCoalescedAndPurityTapsAreImmediate() {
        val immediate = mutableListOf<ZakatGoldItem>()
        val whileTyping = mutableListOf<ZakatGoldItem>()

        scrolled {
            ZakatMetalsStep(
                declaresMetals = true,
                onDeclaresMetalsChange = {},
                goldItems = listOf(
                    ZakatGoldItem(id = "g1", label = "Ring", karat = 24, weightGrams = 12.5)
                ),
                silverItems = emptyList(),
                totalPureGoldText = "12.50 g",
                totalFineSilverText = "0.00 g",
                onAddGoldItem = {},
                onUpdateGoldItem = { immediate += it },
                onEditGoldItemText = { whileTyping += it },
                onRemoveGoldItem = {},
                onAddSilverItem = {},
                onUpdateSilverItem = {},
                onEditSilverItemText = {},
                onRemoveSilverItem = {}
            )
        }

        composeTestRule.onNodeWithText("Ring").performClick()

        val weightField =
            composeTestRule.onNodeWithContentDescription(string(R.string.zakat_item_weight))
        weightField.performTextClearance()
        weightField.performTextInput("30")

        assertTrue("typing must not write immediately", immediate.isEmpty())
        assertEquals(30.0, whileTyping.last().weightGrams, 0.0001)

        val keystrokesSoFar = whileTyping.size
        composeTestRule.onNodeWithText(string(R.string.zakat_purity_18k)).performClick()

        assertEquals(18, immediate.single().karat)
        assertEquals("a purity tap is not a keystroke", keystrokesSoFar, whileTyping.size)
    }

    /** Deleting a piece has to name the piece; the screen removes by id, not by position. */
    @Test
    fun removingAnItemReportsThatItemsId() {
        val removed = mutableListOf<String>()

        scrolled {
            ZakatMetalsStep(
                declaresMetals = true,
                onDeclaresMetalsChange = {},
                goldItems = listOf(
                    ZakatGoldItem(id = "first", label = "Ring"),
                    ZakatGoldItem(id = "second", label = "Bangle")
                ),
                silverItems = emptyList(),
                totalPureGoldText = "0.00 g",
                totalFineSilverText = "0.00 g",
                onAddGoldItem = {},
                onUpdateGoldItem = {},
                onEditGoldItemText = {},
                onRemoveGoldItem = { removed += it },
                onAddSilverItem = {},
                onUpdateSilverItem = {},
                onEditSilverItemText = {},
                onRemoveSilverItem = {}
            )
        }

        // Only the open piece carries a delete button, so the second one has to be opened
        // before it can be removed - which is also what stops a mis-tap deleting a piece.
        composeTestRule.onNodeWithText("Bangle").performScrollTo().performClick()
        composeTestRule.onAllNodesWithContentDescription(string(R.string.zakat_item_remove))[0]
            .performScrollTo()
            .performClick()

        assertEquals(listOf("second"), removed)
    }

    /**
     * The question is worth its full size while it is being asked - the answer turns on
     * which school the user follows, and that needs explaining. Once answered it has to
     * get out of the way: leaving the heading, the paragraph and two explained cards on
     * screen put two headings and two explainers above the first item.
     */
    @Test
    fun theMetalsQuestionIsAskedInFullAndThenShrinksToTwoChips() {
        composeTestRule.setContent {
            ZakatMetalsStep(
                declaresMetals = null,
                onDeclaresMetalsChange = {},
                goldItems = emptyList(),
                silverItems = emptyList(),
                totalPureGoldText = "0.00 g",
                totalFineSilverText = "0.00 g",
                onAddGoldItem = {},
                onUpdateGoldItem = {},
                onEditGoldItemText = {},
                onRemoveGoldItem = {},
                onAddSilverItem = {},
                onUpdateSilverItem = {},
                onEditSilverItemText = {},
                onRemoveSilverItem = {}
            )
        }

        composeTestRule.onNodeWithText(string(R.string.zakat_metals_choice_heading)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.zakat_metals_skip_detail)).assertIsDisplayed()
        // Nothing below the question until it has an answer.
        composeTestRule.onNodeWithText(string(R.string.zakat_add_gold_item)).assertDoesNotExist()
    }

    @Test
    fun answeringYesReplacesTheQuestionWithChipsAndShowsTheItems() {
        scrolled {
            ZakatMetalsStep(
                declaresMetals = true,
                onDeclaresMetalsChange = {},
                goldItems = emptyList(),
                silverItems = emptyList(),
                totalPureGoldText = "0.00 g",
                totalFineSilverText = "0.00 g",
                onAddGoldItem = {},
                onUpdateGoldItem = {},
                onEditGoldItemText = {},
                onRemoveGoldItem = {},
                onAddSilverItem = {},
                onUpdateSilverItem = {},
                onEditSilverItemText = {},
                onRemoveSilverItem = {}
            )
        }

        composeTestRule.onNodeWithText(string(R.string.zakat_metals_chip_declare)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.zakat_add_gold_item)).assertIsDisplayed()
        // The paragraph and the explained cards are the cost this change exists to remove.
        composeTestRule.onNodeWithText(string(R.string.zakat_metals_choice_explainer)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.zakat_metals_skip_detail)).assertDoesNotExist()
    }

    /**
     * The total belongs to the assets it totals.
     *
     * It used to be the last thing in the step, below the deductions and their helper
     * text, which is how six outlined fields at 58dp each pushed it off the bottom of the
     * screen. It is the closing row of the assets card now, and what is owed is a group of
     * its own underneath.
     */
    @Test
    fun theCashStepShowsWhatTheAmountsAddUpTo() {
        scrolled {
            ZakatCashStep(
                currencySymbol = "€",
                currencyCode = "EUR",
                cashOnHand = 1_200.0,
                bankBalance = 6_400.0,
                investments = 9_000.0,
                receivables = 0.0,
                businessInventory = 0.0,
                liabilities = 400.0,
                subtotalText = "€ 16,600.00",
                onCashOnHandChange = {},
                onBankBalanceChange = {},
                onInvestmentsChange = {},
                onReceivablesChange = {},
                onBusinessInventoryChange = {},
                onLiabilitiesChange = {}
            )
        }

        composeTestRule.onNodeWithText(string(R.string.zakat_cash_total))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("€ 16,600.00").assertIsDisplayed()
        // What is owed is a separate group, not a field lost among the assets.
        composeTestRule.onNodeWithText(string(R.string.zakat_section_owe))
            .performScrollTo()
            .assertIsDisplayed()
    }

    /**
     * Step 1 prices both standards, not just the chosen one. Naming them without saying
     * what picking one does to the threshold left the choice unanswerable.
     */
    @Test
    fun bothNisabStandardsShowTheThresholdTheyProduce() {
        scrolled {
            ZakatStandardStep(
                selectedStandard = STANDARD_GOLD,
                onSelectStandard = {},
                currencyLabel = "EUR (€)",
                onOpenCurrency = {},
                goldThresholdText = "€ 10,136.28",
                silverThresholdText = "€ 1,039.73",
                nisabThresholdText = "€ 10,136.28",
                ratesSummary = {}
            )
        }

        // The standard not in force is priced too - that is the whole point of showing both.
        composeTestRule.onNodeWithText("€ 1,039.73").assertIsDisplayed()
        // The chosen one appears on its card and again as the threshold in force.
        composeTestRule.onAllNodesWithText("€ 10,136.28").assertCountEquals(2)
        composeTestRule.onNodeWithText(string(R.string.zakat_section_standard)).assertIsDisplayed()
    }
}
