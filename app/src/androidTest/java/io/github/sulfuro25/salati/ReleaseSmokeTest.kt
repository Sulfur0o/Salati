package io.github.sulfuro25.salati

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ReleaseSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun realMainActivityRendersCoreNavigationWithoutLiveData() {
        composeRule.onNodeWithText("Daily").assertExists()
        composeRule.onNodeWithText("Settings").assertExists()
    }
}
