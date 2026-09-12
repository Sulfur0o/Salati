package com.sulfuro.salati.ui.settings

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sulfuro.salati.ui.components.PermissionStatusRow
import com.sulfuro.salati.ui.components.SettingRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * The state is a pill and the action is a button beside it. Both halves of the pill
     * are one string, so the row reads as a single statement rather than a label and a
     * value that happen to sit near each other.
     */
    @Test
    fun aMissingPermissionStatesItselfAndOffersTheWayOut() {
        var clicked = false
        composeTestRule.setContent {
            PermissionStatusRow(
                label = "Notifications",
                statusText = "Denied",
                isAllowed = false,
                actionText = "Allow",
                actionDescription = "Allow notifications for Salati",
                onActionClick = { clicked = true },
                description = "Needed to alert you at prayer time"
            )
        }

        composeTestRule.onNodeWithText("Notifications · Denied").assertIsDisplayed()
        composeTestRule.onNodeWithText("Needed to alert you at prayer time").assertIsDisplayed()
        // Named for a screen reader: "Allow" alone says nothing about which permission.
        composeTestRule.onNodeWithContentDescription("Allow notifications for Salati")
            .assertIsDisplayed()

        assert(!clicked) { "the row must not act until it is asked to" }
        composeTestRule.onNodeWithContentDescription("Allow notifications for Salati").performClick()
        assert(clicked)
    }

    /**
     * Granted means the button is *absent*, not disabled. A control that cannot do
     * anything is a question the user has to answer; nothing to tap is nothing to wonder
     * about. The explanation goes too - it only exists to justify the ask.
     */
    @Test
    fun aGrantedPermissionKeepsItsLabelAndLosesItsButton() {
        composeTestRule.setContent {
            PermissionStatusRow(
                label = "Notifications",
                statusText = "Granted",
                isAllowed = true,
                actionText = "Allow",
                actionDescription = "Allow notifications for Salati",
                onActionClick = {},
                description = "Needed to alert you at prayer time"
            )
        }

        composeTestRule.onNodeWithText("Notifications · Granted").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Allow notifications for Salati")
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("Needed to alert you at prayer time").assertDoesNotExist()
    }

    @Test
    fun verifySettingRowMinimumTouchTarget() {
        composeTestRule.setContent {
            SettingRow(title = "Test Row", supportingText = "Subtitle") {}
        }
        
        composeTestRule.onNodeWithText("Test Row").onParent()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun verifySettingRowTogglesCorrectly() {
        var toggledValue = false
        composeTestRule.setContent {
            Surface(
                modifier = Modifier.toggleable(
                    value = toggledValue,
                    onValueChange = { toggledValue = it },
                    role = Role.Switch
                )
            ) {
                SettingRow(title = "Mute", supportingText = "Disable") {
                    Switch(
                        checked = toggledValue,
                        onCheckedChange = null,
                        modifier = Modifier.clearAndSetSemantics {}
                    )
                }
            }
        }
        
        // Walking up two parents assumed a particular tree shape and broke on the merged
        // tree. The thing under test is the toggleable wrapper, so ask for that directly.
        composeTestRule.onNode(isToggleable()).performClick()
        assert(toggledValue)
    }
}