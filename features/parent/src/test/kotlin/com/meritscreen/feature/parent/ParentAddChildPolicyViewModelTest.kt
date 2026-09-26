package com.meritscreen.feature.parent

import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.DeviceSummary
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.core.common.domain.UsageDaySummary
import com.meritscreen.core.common.session.ParentSession
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.firebase.family.FamilyChildProfile
import com.meritscreen.core.firebase.family.FamilyDraftChild
import com.meritscreen.core.firebase.family.FamilyMeta
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.feature.parent.ui.ParentAddChildPolicyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ParentAddChildPolicyViewModelTest {

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
    fun saveTimeline_updatesPolicyFields() = runTest {
        val store = FakePolicyStore()
        val sessions = FakePolicySessions(
            ParentSession(uid = "u1", familyId = "f1", childIds = listOf("c1")),
        )
        val vm = ParentAddChildPolicyViewModel(sessions, store)
        var succeeded = false

        vm.saveTimeline(
            childId = "c1",
            ageBand = AgeBand.AGE_7_TO_9,
            budgetMinutes = 120,
            quizFreqMinutes = 25,
            cooldownMinutes = 12,
            onSuccess = { succeeded = true },
        )
        advanceUntilIdle()

        assertTrue(succeeded)
        assertNull(vm.error.value)
        val policy = store.policies["c1"]
        assertEquals(120, policy?.dailyCeilingMinutes)
        assertEquals(25, policy?.defaultBlockMinutes)
        assertEquals(12, policy?.defaultCooldownMinutes)
        assertEquals("Grade 3", policy?.gradeStandard)
    }

    @Test
    fun saveAiPrompt_sanitizesAndPersists() = runTest {
        val store = FakePolicyStore()
        store.policies["c1"] = ChildPolicy(dailyCeilingMinutes = 90)
        val sessions = FakePolicySessions(
            ParentSession(uid = "u1", familyId = "f1", childIds = listOf("c1")),
        )
        val vm = ParentAddChildPolicyViewModel(sessions, store)
        var succeeded = false

        vm.saveAiPrompt(
            childId = "c1",
            ageBand = AgeBand.AGE_10_TO_12,
            rawPrompt = "Focus on fractions and reading",
            onSuccess = { succeeded = true },
        )
        advanceUntilIdle()

        assertTrue(succeeded)
        assertEquals("Focus on fractions and reading", store.policies["c1"]?.customPromptGuidelines)
        assertEquals(90, store.policies["c1"]?.dailyCeilingMinutes)
    }

    @Test
    fun saveTimeline_surfacesMissingSession() = runTest {
        val store = FakePolicyStore()
        val sessions = FakePolicySessions(null)
        val vm = ParentAddChildPolicyViewModel(sessions, store)

        vm.saveTimeline(
            childId = "c1",
            ageBand = AgeBand.AGE_7_TO_9,
            budgetMinutes = 90,
            quizFreqMinutes = 30,
            cooldownMinutes = 10,
            onSuccess = {},
        )
        advanceUntilIdle()

        assertTrue(!vm.error.value.isNullOrBlank())
    }
}

private class FakePolicySessions(
    initial: ParentSession?,
) : ParentSessionRepository {
    private val state = MutableStateFlow(initial)
    override val session: Flow<ParentSession?> = state
    override suspend fun current(): ParentSession? = state.value
    override suspend fun set(session: ParentSession) {
        state.value = session
    }
    override suspend fun clear() {
        state.value = null
    }
}

private class FakePolicyStore : ParentControlStore {
    val policies = mutableMapOf<String, ChildPolicy>()

    override suspend fun getFamilyMeta(familyId: String): FamilyMeta? =
        FamilyMeta(familyId, "Test", "u1")

    override suspend fun listChildren(familyId: String): List<FamilyChildProfile> = emptyList()

    override suspend fun addChild(familyId: String, child: FamilyDraftChild): FamilyChildProfile =
        FamilyChildProfile(child.localId, child.name, child.ageBand, child.avatar, child.language)

    override suspend fun updateChild(
        familyId: String,
        childId: String,
        child: FamilyDraftChild,
    ): FamilyChildProfile = FamilyChildProfile(childId, child.name, child.ageBand, child.avatar, child.language)

    override suspend fun deleteChild(familyId: String, childId: String) = Unit

    override suspend fun getPolicy(familyId: String, childId: String): ChildPolicy =
        policies[childId] ?: ChildPolicy()

    override suspend fun updatePolicy(familyId: String, childId: String, policy: ChildPolicy) {
        policies[childId] = policy
    }

    override suspend fun setChildPaused(familyId: String, childId: String, paused: Boolean) = Unit
    override suspend fun addBonusTime(familyId: String, childId: String, bonusMinutes: Int) = Unit
    override suspend fun listAppRules(familyId: String, childId: String): List<AppRule> = emptyList()
    override suspend fun upsertAppRule(familyId: String, childId: String, rule: AppRule) = Unit
    override suspend fun deleteAppRule(familyId: String, childId: String, appId: String) = Unit
    override suspend fun listUsageDays(familyId: String, childId: String, limit: Int): List<UsageDaySummary> =
        emptyList()
    override suspend fun listQuizAttempts(
        familyId: String,
        childId: String,
        limit: Int,
    ): List<com.meritscreen.core.common.domain.QuizAttemptSummary> = emptyList()
    override suspend fun getSkillState(
        familyId: String,
        childId: String,
    ): List<com.meritscreen.core.common.domain.TopicSkillSummary> = emptyList()
    override suspend fun listDevices(familyId: String, childId: String): List<DeviceSummary> = emptyList()
    override suspend fun setDeviceRevoked(
        familyId: String,
        childId: String,
        deviceId: String,
        revoked: Boolean,
    ) = Unit
    override suspend fun listInstalledApps(familyId: String, childId: String): List<InstalledAppSummary> =
        emptyList()
    override fun observeInstalledApps(
        familyId: String,
        childId: String,
    ): Flow<List<InstalledAppSummary>> = flowOf(emptyList())
    override fun observeDevices(familyId: String, childId: String): Flow<List<DeviceSummary>> = flowOf(emptyList())
    override fun observeChildren(familyId: String): Flow<List<FamilyChildProfile>> = flowOf(emptyList())
    override suspend fun deleteFamily(familyId: String) = Unit
}
