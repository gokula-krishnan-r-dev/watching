package com.meritscreen.feature.parent

import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.DeviceSummary
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.core.common.domain.UsageDaySummary
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.common.session.ParentSession
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.firebase.family.FamilyChildProfile
import com.meritscreen.core.firebase.family.FamilyDraftChild
import com.meritscreen.core.firebase.family.FamilyMeta
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.feature.parent.ui.AddChildAccountViewModel
import com.meritscreen.feature.parent.ui.formatMinutes
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
class AddChildAccountViewModelTest {

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
    fun save_rejectsBlankName() = runTest {
        val store = FakeParentControlStore()
        val sessions = FakeParentSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = emptyList()),
        )
        val vm = AddChildAccountViewModel(sessions, store)
        vm.save()
        advanceUntilIdle()
        assertEquals("Enter your child's name.", vm.error.value)
        assertNull(vm.createdChildId.value)
        assertTrue(store.added.isEmpty())
    }

    @Test
    fun save_createsChildAndUpdatesSession() = runTest {
        val store = FakeParentControlStore()
        val sessions = FakeParentSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = listOf("c0")),
        )
        val vm = AddChildAccountViewModel(sessions, store)
        vm.onNameChanged("Sam")
        vm.onAgeBand(AgeBand.AGE_10_TO_12)
        vm.save()
        advanceUntilIdle()
        assertNull(vm.error.value)
        assertEquals(1, store.added.size)
        assertEquals("Sam", store.added.single().name)
        assertEquals(AgeBand.AGE_10_TO_12, store.added.single().ageBand)
        val createdId = vm.createdChildId.value
        assertTrue(!createdId.isNullOrBlank())
        assertEquals(listOf("c0", createdId), sessions.current()?.childIds)
        assertEquals("Sam", vm.createdChildName.value)
        assertEquals(AgeBand.AGE_10_TO_12, vm.createdAgeBand.value)
    }

    @Test
    fun save_surfacesCapError() = runTest {
        val store = FakeParentControlStore(failAddWith = AppError.Validation("cap"))
        val sessions = FakeParentSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = emptyList()),
        )
        val vm = AddChildAccountViewModel(sessions, store)
        vm.onNameChanged("Sam")
        vm.save()
        advanceUntilIdle()
        assertEquals("cap", vm.error.value)
        assertNull(vm.createdChildId.value)
    }
}

class FormatMinutesTest {
    @Test
    fun formatsRanges() {
        assertEquals("0 min", formatMinutes(0))
        assertEquals("45 min", formatMinutes(45))
        assertEquals("2h", formatMinutes(120))
        assertEquals("1h 5m", formatMinutes(65))
    }
}

private class FakeParentSessionRepository(
    initial: ParentSession? = null,
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

private class FakeParentControlStore(
    private val failAddWith: AppError? = null,
) : ParentControlStore {
    val added = mutableListOf<FamilyDraftChild>()

    override suspend fun getFamilyMeta(familyId: String): FamilyMeta? =
        FamilyMeta(familyId, "Test", "u1")

    override suspend fun listChildren(familyId: String): List<FamilyChildProfile> = emptyList()

    override suspend fun addChild(familyId: String, child: FamilyDraftChild): FamilyChildProfile {
        failAddWith?.let { throw AppErrorException(it) }
        if (added.size >= AppConfig.MAX_CHILDREN_PER_PARENT) {
            throw AppErrorException(AppError.Validation("cap"))
        }
        added += child
        return FamilyChildProfile(
            childId = child.localId,
            displayName = child.name,
            ageBand = child.ageBand,
            avatar = child.avatar,
            language = child.language,
        )
    }

    override suspend fun updateChild(
        familyId: String,
        childId: String,
        child: FamilyDraftChild,
    ): FamilyChildProfile = FamilyChildProfile(childId, child.name, child.ageBand, child.avatar, child.language)

    override suspend fun deleteChild(familyId: String, childId: String) = Unit

    override suspend fun getPolicy(familyId: String, childId: String): ChildPolicy = ChildPolicy()
    override suspend fun updatePolicy(familyId: String, childId: String, policy: ChildPolicy) = Unit
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
