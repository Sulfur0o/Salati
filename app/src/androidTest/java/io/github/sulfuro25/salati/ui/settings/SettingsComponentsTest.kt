package io.github.sulfuro25.salati.ui.settings

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sulfuro25.salati.ui.components.PermissionStatusRow
import io.github.sulfuro25.salati.ui.components.SettingRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun verifyPermissionStatusRowDoesNotAutoLaunch() {
        var clicked = false
        composeTestRule.setContent {
            PermissionStatusRow(
                title = "Notifications",
                description = "Desc",
                statusText = "Not allowed",
                isAllowed = false,
                onActionClick = { clicked = true },
                actionText = "Allow"
            )
        }
        
        composeTestRule.onNodeWithText("Notifications").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not allowed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Allow").assertIsDisplayed()
        
        // Assert not clicked initially
        assert(!clicked)
        
        composeTestRule.onNodeWithText("Allow").performClick()
        assert(clicked)
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
        
        composeTestRule.onNodeWithText("Mute").onParent().onParent().performClick()
        assert(toggledValue)
    }
}