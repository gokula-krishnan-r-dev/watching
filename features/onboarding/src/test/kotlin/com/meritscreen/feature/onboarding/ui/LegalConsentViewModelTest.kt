package com.meritscreen.feature.onboarding.ui

import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.testing.MainDispatcherRule
import com.meritscreen.feature.onboarding.data.FakeOnboardingDraftRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LegalConsentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun defaultsToConsentNotGiven() = runTest {
        val viewModel = LegalConsentViewModel(FakeOnboardingDraftRepository())
        val state = viewModel.uiState.value
        check(state is UiState.Success)
        assertFalse(state.data)
    }

    @Test
    fun settingConsentUpdatesState() = runTest {
        val repository = FakeOnboardingDraftRepository()
        val viewModel = LegalConsentViewModel(repository)

        viewModel.setConsentGiven(true)

        val state = viewModel.uiState.value
        check(state is UiState.Success)
        assertTrue(state.data)
    }
}
