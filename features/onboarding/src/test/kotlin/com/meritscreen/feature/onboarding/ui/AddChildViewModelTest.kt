package com.meritscreen.feature.onboarding.ui

import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.testing.MainDispatcherRule
import com.meritscreen.feature.onboarding.data.FakeOnboardingDraftRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AddChildViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun addChildWithBlankNameSetsFormError() = runTest {
        val viewModel = AddChildViewModel(FakeOnboardingDraftRepository())

        viewModel.onNameChanged("   ")
        viewModel.addChild()

        assertEquals("Enter your child's name.", viewModel.formError.value)
    }

    @Test
    fun addChildWithValidNameAddsAndResetsForm() = runTest {
        val viewModel = AddChildViewModel(FakeOnboardingDraftRepository())

        viewModel.onNameChanged("  Ada  ")
        viewModel.onAgeBandSelected(AgeBand.AGE_3_TO_6)
        viewModel.onAvatarSelected(AvatarPreset.FOX)
        viewModel.addChild()

        val state = viewModel.uiState.value
        check(state is UiState.Success)
        assertEquals(1, state.data.children.size)
        assertEquals("Ada", state.data.children.first().name)
        assertEquals(AgeBand.AGE_3_TO_6, state.data.children.first().ageBand)
        assertEquals(AvatarPreset.FOX, state.data.children.first().avatar)

        // form resets for adding another child
        assertEquals("", viewModel.name.value)
        assertEquals(AgeBand.AGE_7_TO_9, viewModel.ageBand.value)
        assertNull(viewModel.formError.value)
    }

    @Test
    fun cannotAddMoreThanConfiguredCap() = runTest {
        val viewModel = AddChildViewModel(FakeOnboardingDraftRepository())
        repeat(AppConfig.MAX_CHILDREN_PER_PARENT) { index ->
            viewModel.onNameChanged("Kid $index")
            viewModel.addChild()
        }

        val state = viewModel.uiState.value
        check(state is UiState.Success)
        assertEquals(AppConfig.MAX_CHILDREN_PER_PARENT, state.data.children.size)
        assertFalse(state.data.canAddMore)

        viewModel.onNameChanged("One too many")
        viewModel.addChild()

        assertNotNull(viewModel.formError.value)
        val afterOverflow = viewModel.uiState.value
        check(afterOverflow is UiState.Success)
        assertEquals(AppConfig.MAX_CHILDREN_PER_PARENT, afterOverflow.data.children.size)
    }

    @Test
    fun removeChildRemovesFromList() = runTest {
        val viewModel = AddChildViewModel(FakeOnboardingDraftRepository())
        viewModel.onNameChanged("Ada")
        viewModel.addChild()
        val added = (viewModel.uiState.value as UiState.Success).data.children.first()

        viewModel.removeChild(added.localId)

        val state = viewModel.uiState.value
        check(state is UiState.Success)
        assertEquals(0, state.data.children.size)
    }
}
