package com.meritscreen.feature.onboarding.ui

import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.testing.MainDispatcherRule
import com.meritscreen.feature.onboarding.data.FakeOnboardingDraftRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CreateFamilyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun defaultsToEmptyFamilyName() = runTest {
        val viewModel = CreateFamilyViewModel(FakeOnboardingDraftRepository())
        val state = viewModel.uiState.value
        check(state is UiState.Success)
        assertEquals("", state.data)
    }

    @Test
    fun settingFamilyNameUpdatesState() = runTest {
        val viewModel = CreateFamilyViewModel(FakeOnboardingDraftRepository())

        viewModel.setFamilyName("The Smiths")

        val state = viewModel.uiState.value
        check(state is UiState.Success)
        assertEquals("The Smiths", state.data)
    }
}
