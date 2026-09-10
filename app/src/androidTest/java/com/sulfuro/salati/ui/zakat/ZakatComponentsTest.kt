package com.sulfuro.salati.ui.zakat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    /**
     * A part-typed amount has to survive being typed. Pushing the field's text back down
     * from the parsed value would rewrite "12." to "12.00" under the cursor mid-entry.
     */
    @Test
    fun aPartlyTypedAmountStaysOnScreenAndTheParsedValueIsPushedUp() {
        val pushed = mutableListOf<Double>()

        composeTestRule.setContent {
            MoneyField(
                label = "Cash",
                initialValue = 0.0,
                currencySymbol = "€",
                onValueChange = { pushed += it }
            )
        }

        composeTestRule.onNodeWithText("Cash").performTextInput("12.")

        composeTestRule.onNodeWithText("12.").assertIsDisplayed()
        assertEquals(12.0, pushed.last(), 0.0001)
    }

    /** Clearing the field means zero, not "leave the last value in place". */
    @Test
    fun clearingAnAmountReportsZero() {
        val pushed = mutableListOf<Double>()

        composeTestRule.setContent {
            MoneyField(
                label = "Cash",
                initialValue = 40.0,
                currencySymbol = "€",
                onValueChange = { pushed += it }
            )
        }

        composeTestRule.onNodeWithText("Cash").performTextClearance()

        assertEquals(0.0, pushed.last(), 0.0001)
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

        composeTestRule.setContent {
            ZakatMetalsStep(
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

        // Matched by its label, which is there whether or not the field has a value.
        val weightField = composeTestRule.onNodeWithText(string(R.string.zakat_item_weight))
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

        composeTestRule.setContent {
            ZakatMetalsStep(
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

        composeTestRule.onAllNodesWithContentDescription(string(R.string.zakat_item_remove))[1]
            .performClick()

        assertEquals(listOf("second"), removed)
    }
}
