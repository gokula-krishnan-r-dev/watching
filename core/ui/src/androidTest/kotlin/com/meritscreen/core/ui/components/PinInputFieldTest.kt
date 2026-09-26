package com.meritscreen.core.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import com.meritscreen.core.ui.theme.MeritScreenTheme
import org.junit.Rule
import org.junit.Test

class PinInputFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersOneSlotPerDigitOfMaxLength() {
        composeRule.setContent {
            MeritScreenTheme {
                PinInputField(value = "", onValueChange = {}, maxLength = 6)
            }
        }
        composeRule.onAllNodesWithTag("pin_digit_slot").assertCountEquals(6)
    }

    @Test
    fun exposesAccessibleLabelForScreenReaders() {
        composeRule.setContent {
            MeritScreenTheme {
                PinInputField(
                    value = "",
                    onValueChange = {},
                    maxLength = 4,
                    contentDescriptionLabel = "Parent PIN",
                )
            }
        }
        composeRule.onNodeWithContentDescription("Parent PIN").assertIsDisplayed()
    }
}
