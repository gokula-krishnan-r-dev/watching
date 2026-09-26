package com.meritscreen.feature.onboarding.ui

import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.feature.onboarding.data.FakeOnboardingDraftRepository
import com.meritscreen.feature.onboarding.domain.ChildDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingChildContextViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun exposesPrimaryChildNameFromDraft() = runTest {
        val repo = FakeOnboardingDraftRepository()
        repo.addChild(
            ChildDraft(
                localId = "c1",
                name = "Maya",
                ageBand = AgeBand.AGE_3_TO_6,
                avatar = AvatarPreset.OWL,
            ),
        )
        val vm = OnboardingChildContextViewModel(repo)
        advanceUntilIdle()

        assertEquals("Maya", vm.childContext.value.name)
        assertEquals(AgeBand.AGE_3_TO_6, vm.childContext.value.ageBand)
        assertEquals("Kindergarten", vm.childContext.value.gradeLabel)
        assertEquals(AvatarPreset.OWL, vm.childContext.value.avatar)
    }

    @Test
    fun fallsBackWhenDraftHasNoChildren() = runTest {
        val vm = OnboardingChildContextViewModel(FakeOnboardingDraftRepository())
        advanceUntilIdle()

        assertEquals("your child", vm.childContext.value.name)
        assertEquals("3rd Grade", vm.childContext.value.gradeLabel)
    }

    @Test
    fun usesLatestChildWhenMultipleExist() = runTest {
        val repo = FakeOnboardingDraftRepository()
        repo.addChild(ChildDraft("c1", "Leo", AgeBand.AGE_7_TO_9, AvatarPreset.FOX))
        repo.addChild(ChildDraft("c2", "Sam", AgeBand.AGE_10_TO_12, AvatarPreset.BEAR))
        val vm = OnboardingChildContextViewModel(repo)
        advanceUntilIdle()

        assertEquals("Sam", vm.childContext.value.name)
        assertEquals("6th Grade", vm.childContext.value.gradeLabel)
    }
}
