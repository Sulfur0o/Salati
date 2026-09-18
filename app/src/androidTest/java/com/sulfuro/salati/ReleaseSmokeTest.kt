package com.sulfuro.salati

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ReleaseSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * The labels are looked for in the unmerged tree because a navigation item merges its
     * icon and its label into one node, and from Compose BOM 2026.09 the merged node no
     * longer answers to the label's text. The bar still draws "Daily" and "Settings"; only
     * where the finder has to look for them changed.
     */
    @Test
    fun realMainActivityRendersCoreNavigationWithoutLiveData() {
        composeRule.onNodeWithText("Daily", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Settings", useUnmergedTree = true).assertExists()
    }
}
