package com.meritscreen.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.meritscreen.core.ui.theme.MeritScreenTheme
import org.junit.Rule
import org.junit.Test

class StateComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateIsVisible() {
        composeRule.setContent {
            MeritScreenTheme { LoadingState(message = "Loading children") }
        }
        composeRule.onNodeWithTag("loading_state").assertIsDisplayed()
        composeRule.onNodeWithText("Loading children").assertIsDisplayed()
    }

    @Test
    fun errorStateShowsRetry() {
        composeRule.setContent {
            MeritScreenTheme {
                ErrorState(message = "Check your connection and try again.", onRetry = {})
            }
        }
        composeRule.onNodeWithTag("error_state").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsAction() {
        composeRule.setContent {
            MeritScreenTheme {
                EmptyState(
                    title = "No children yet",
                    message = "Add a child profile to get started.",
                    actionLabel = "Add child",
                    onAction = {},
                )
            }
        }
        composeRule.onNodeWithTag("empty_state").assertIsDisplayed()
        composeRule.onNodeWithText("Add child").assertIsDisplayed()
    }
}
