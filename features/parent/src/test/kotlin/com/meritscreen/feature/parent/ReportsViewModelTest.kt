package com.meritscreen.feature.parent

import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import com.meritscreen.core.analytics.AnalyticsEvent
import com.meritscreen.core.analytics.AnalyticsTracker
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.DeviceSummary
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.core.common.domain.QuizAttemptSummary
import com.meritscreen.core.common.domain.TopicSkillSummary
import com.meritscreen.core.common.domain.UsageDaySummary
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.session.ParentSession
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.firebase.family.FamilyChildProfile
import com.meritscreen.core.firebase.family.FamilyDraftChild
import com.meritscreen.core.firebase.family.FamilyMeta
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.feature.parent.ui.ReportsViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val analyticsTracker = object : AnalyticsTracker {
        val events = mutableListOf<AnalyticsEvent>()
        override fun track(event: AnalyticsEvent) {
            events.add(event)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads reports and calculates metrics for selected child`() = runTest {
        val leo = FamilyChildProfile(
            childId = "c_leo",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.RABBIT,
            language = "en",
        )
        val maya = FamilyChildProfile(
            childId = "c_maya",
            displayName = "Maya",
            ageBand = AgeBand.AGE_3_TO_6,
            avatar = AvatarPreset.OWL,
            language = "en",
        )

        val store = ReportsTestStore(
            children = listOf(leo, maya),
            usageDays = mapOf(
                "c_leo" to listOf(
                    UsageDaySummary(LocalDate.now().toString(), 102, mapOf("org.khankids.android" to 60, "com.google.android.apps.youtube.kids" to 42)),
                ),
            ),
            quizAttempts = mapOf(
                "c_leo" to listOf(
                    QuizAttemptSummary("q1", System.currentTimeMillis(), listOf("Math"), 8, 8, true, 30),
                ),
            ),
        )
        val sessions = ReportsTestSessionRepository(ParentSession("u1", "f1", listOf("c_leo", "c_maya")))
        val savedStateHandle = SavedStateHandle(mapOf("childId" to "c_leo"))

        val vm = ReportsViewModel(savedStateHandle, sessions, store, analyticsTracker)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected UiState.Success but was $state", state is UiState.Success)
        val data = (state as UiState.Success).data
        assertEquals("c_leo", data.selectedChildId)
        assertEquals("Leo", data.selectedChildName)
        assertEquals(2, data.children.size)
        assertEquals(7, data.selectedDays)
        assertTrue(data.dailyAverageFormatted.isNotBlank())
        assertTrue(data.totalScreenTimeFormatted.isNotBlank())
        assertNotNull(data.balancedScore)
        assertTrue(data.hasUsageData)
        assertTrue(data.appAllocations.isNotEmpty())
        assertFalse(data.appAllocations.any { it.displayName.contains("Khan") && data.report.totalMinutes == 0 })
        assertEquals(1, analyticsTracker.events.filter { it == AnalyticsEvent.ReportsViewed }.size)
    }

    @Test
    fun `empty usage does not invent mock app allocations`() = runTest {
        val leo = FamilyChildProfile(
            childId = "c_leo",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.RABBIT,
            language = "en",
        )
        val store = ReportsTestStore(children = listOf(leo))
        val sessions = ReportsTestSessionRepository(ParentSession("u1", "f1", listOf("c_leo")))
        val savedStateHandle = SavedStateHandle(mapOf("childId" to "c_leo"))

        val vm = ReportsViewModel(savedStateHandle, sessions, store, analyticsTracker)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is UiState.Success)
        val data = (state as UiState.Success).data
        assertTrue(data.appAllocations.isEmpty())
        assertFalse(data.hasUsageData)
        assertNull(data.trendPercentage)
        assertNull(data.educationalPercent)
    }

    @Test
    fun `selectDays supports Today 7 and 30 day windows`() = runTest {
        val leo = FamilyChildProfile(
            childId = "c_leo",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.RABBIT,
            language = "en",
        )
        val store = ReportsTestStore(children = listOf(leo))
        val sessions = ReportsTestSessionRepository(ParentSession("u1", "f1", listOf("c_leo")))
        val savedStateHandle = SavedStateHandle(mapOf("childId" to "c_leo"))

        val vm = ReportsViewModel(savedStateHandle, sessions, store, analyticsTracker)
        advanceUntilIdle()

        vm.selectDays(1)
        advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as UiState.Success).data.selectedDays)

        vm.selectDays(30)
        advanceUntilIdle()
        assertEquals(30, (vm.uiState.value as UiState.Success).data.selectedDays)

        vm.selectDays(7)
        advanceUntilIdle()
        assertEquals(7, (vm.uiState.value as UiState.Success).data.selectedDays)
    }

    @Test
    fun `selectChild switches active child and reloads their reports`() = runTest {
        val leo = FamilyChildProfile(
            childId = "c_leo",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.RABBIT,
            language = "en",
        )
        val maya = FamilyChildProfile(
            childId = "c_maya",
            displayName = "Maya",
            ageBand = AgeBand.AGE_3_TO_6,
            avatar = AvatarPreset.OWL,
            language = "en",
        )

        val store = ReportsTestStore(
            children = listOf(leo, maya),
            usageDays = mapOf(
                "c_leo" to listOf(UsageDaySummary(LocalDate.now().toString(), 90, mapOf("app1" to 90))),
                "c_maya" to listOf(UsageDaySummary(LocalDate.now().toString(), 45, mapOf("app2" to 45))),
            ),
        )
        val sessions = ReportsTestSessionRepository(ParentSession("u1", "f1", listOf("c_leo", "c_maya")))
        val savedStateHandle = SavedStateHandle(mapOf("childId" to "c_leo"))

        val vm = ReportsViewModel(savedStateHandle, sessions, store, analyticsTracker)
        advanceUntilIdle()

        // Switch to Maya
        vm.selectChild("c_maya")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is UiState.Success)
        val data = (state as UiState.Success).data
        assertEquals("c_maya", data.selectedChildId)
        assertEquals("Maya", data.selectedChildName)
    }

    @Test
    fun `handles missing session with auth error`() = runTest {
        val store = ReportsTestStore()
        val sessions = ReportsTestSessionRepository(null) // No session
        val savedStateHandle = SavedStateHandle(mapOf("childId" to "c_leo"))

        val vm = ReportsViewModel(savedStateHandle, sessions, store, analyticsTracker)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected UiState.Error but was $state", state is UiState.Error)
        assertTrue((state as UiState.Error).error is AppError.Auth)
    }

    @Test
    fun `real-time stream emission dynamically updates reports state without reloading`() = runTest {
        val leo = FamilyChildProfile(
            childId = "c_leo",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.RABBIT,
            language = "en",
        )
        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()
        val usageFlow = MutableStateFlow(listOf(UsageDaySummary(today, 20, mapOf("org.khankids.android" to 20))))
        val store = ReportsTestStore(
            children = listOf(leo),
            usageDaysFlow = mapOf("c_leo" to usageFlow),
        )
        val sessions = ReportsTestSessionRepository(ParentSession("u1", "f1", listOf("c_leo")))
        val savedStateHandle = SavedStateHandle(mapOf("childId" to "c_leo"))

        val vm = ReportsViewModel(savedStateHandle, sessions, store, analyticsTracker)
        advanceUntilIdle()

        val initialData = (vm.uiState.value as UiState.Success).data
        assertEquals("20m", initialData.totalScreenTimeFormatted)

        // Real-time Firestore snapshot arrives with updated usage:
        usageFlow.value = listOf(
            UsageDaySummary(today, 20, mapOf("org.khankids.android" to 20)),
            UsageDaySummary(yesterday, 40, mapOf("org.khankids.android" to 40)),
        )
        advanceUntilIdle()

        val updatedData = (vm.uiState.value as UiState.Success).data
        assertEquals("1h", updatedData.totalScreenTimeFormatted)
    }
}

private class ReportsTestSessionRepository(
    initial: ParentSession? = null,
) : ParentSessionRepository {
    private val state = MutableStateFlow(initial)
    override val session: Flow<ParentSession?> = state
    override suspend fun current(): ParentSession? = state.value
    override suspend fun set(session: ParentSession) { state.value = session }
    override suspend fun clear() { state.value = null }
}

private class ReportsTestStore(
    val children: List<FamilyChildProfile> = emptyList(),
    val usageDays: Map<String, List<UsageDaySummary>> = emptyMap(),
    val quizAttempts: Map<String, List<QuizAttemptSummary>> = emptyMap(),
    val skillState: Map<String, List<TopicSkillSummary>> = emptyMap(),
    val usageDaysFlow: Map<String, Flow<List<UsageDaySummary>>> = emptyMap(),
) : ParentControlStore {
    override suspend fun getFamilyMeta(familyId: String): FamilyMeta? =
        FamilyMeta(familyId, "The Miller Family", "u1")

    override suspend fun listChildren(familyId: String): List<FamilyChildProfile> = children
    override suspend fun addChild(familyId: String, child: FamilyDraftChild): FamilyChildProfile =
        children.firstOrNull() ?: FamilyChildProfile("c1", "Leo", AgeBand.AGE_7_TO_9, AvatarPreset.LION, "en")

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
        usageDays[childId] ?: emptyList()

    override suspend fun listQuizAttempts(familyId: String, childId: String, limit: Int): List<QuizAttemptSummary> =
        quizAttempts[childId] ?: emptyList()

    override suspend fun getSkillState(familyId: String, childId: String): List<TopicSkillSummary> =
        skillState[childId] ?: emptyList()

    override suspend fun listDevices(familyId: String, childId: String): List<DeviceSummary> = emptyList()
    override suspend fun setDeviceRevoked(familyId: String, childId: String, deviceId: String, revoked: Boolean) = Unit
    override suspend fun listInstalledApps(familyId: String, childId: String): List<InstalledAppSummary> = emptyList()
    override fun observeInstalledApps(familyId: String, childId: String): Flow<List<InstalledAppSummary>> = flowOf(emptyList())
    override fun observeDevices(familyId: String, childId: String): Flow<List<DeviceSummary>> = flowOf(emptyList())
    override fun observeChildren(familyId: String): Flow<List<FamilyChildProfile>> = flowOf(children)
    override fun observeUsageDays(familyId: String, childId: String, limit: Int): Flow<List<UsageDaySummary>> =
        usageDaysFlow[childId] ?: flowOf(usageDays[childId] ?: emptyList())
    override fun observeQuizAttempts(familyId: String, childId: String, limit: Int): Flow<List<QuizAttemptSummary>> =
        flowOf(quizAttempts[childId] ?: emptyList())
    override fun observeSkillState(familyId: String, childId: String): Flow<List<TopicSkillSummary>> =
        flowOf(skillState[childId] ?: emptyList())
    override fun observePolicy(familyId: String, childId: String): Flow<ChildPolicy?> = flowOf(ChildPolicy())
    override fun observeAppRules(familyId: String, childId: String): Flow<List<AppRule>> = flowOf(emptyList())
    override suspend fun deleteFamily(familyId: String) = Unit
}
