package com.meritscreen.feature.parent

import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.DeviceSummary
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.core.common.domain.QuizAttemptSummary
import com.meritscreen.core.common.domain.TopicSkillSummary
import com.meritscreen.core.common.domain.UsageDaySummary
import com.meritscreen.core.common.session.ParentSession
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.firebase.auth.AuthClient
import com.meritscreen.core.firebase.auth.AuthUser
import com.meritscreen.core.firebase.family.FamilyChildProfile
import com.meritscreen.core.firebase.family.FamilyDraftChild
import com.meritscreen.core.firebase.family.FamilyMeta
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.feature.parent.ui.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

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
    fun `uiState emits success with correct child card and calculations`() = runTest {
        val testChild = FamilyChildProfile(
            childId = "c1",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.LION,
            language = "en",
        )
        val store = TestParentControlStore(
            children = listOf(testChild),
            policy = ChildPolicy(dailyCeilingMinutes = 120, paused = false),
            usageDays = listOf(
                UsageDaySummary(
                    day = "2026-09-18",
                    minutesUsed = 75,
                    minutesByApp = mapOf("com.khanacademy.android" to 45, "com.google.android.youtube.kids" to 30),
                ),
            ),
            devices = listOf(
                DeviceSummary(
                    deviceId = "d1",
                    model = "Pixel Tablet",
                    lastSeenAtEpochMs = System.currentTimeMillis() - 60_000L,
                ),
            ),
            quizAttempts = listOf(
                QuizAttemptSummary(
                    attemptId = "q1",
                    createdAtEpochMs = System.currentTimeMillis(),
                    topics = listOf("Math"),
                    score = 3,
                    total = 3,
                    passed = true,
                    extraMinutesGranted = 30,
                ),
            ),
        )
        val sessions = TestParentSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = listOf("c1")),
        )
        val authClient = TestAuthClient(email = "sarah@example.com")

        val vm = DashboardViewModel(sessions, store, authClient)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceTimeBy(AppConfig.DASHBOARD_CHILDREN_DEBOUNCE_MS + 100)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected UiState.Success but was $state", state is UiState.Success)
        val data = (state as UiState.Success).data
        assertEquals("Sarah", data.greetingName)
        assertEquals("The Miller Family", data.familyName)
        assertEquals(1, data.children.size)

        val leoCard = data.children.first()
        assertEquals("Leo", leoCard.profile.displayName)
        assertEquals(75, leoCard.todayMinutes)
        assertEquals(120, leoCard.dailyCeilingMinutes)
        assertEquals(45, leoCard.remainingMinutes)
        assertEquals(1, leoCard.pairedDevices)
        assertFalse(leoCard.isPaused)
        assertEquals("Pixel Tablet", leoCard.deviceModel)
        assertEquals(true, leoCard.lastQuizPassed)
        assertEquals("3/3", leoCard.lastQuizScore)
        assertEquals(30, leoCard.lastQuizRewardMinutes)
    }

    @Test
    fun `greeting strips digits from email when displayName is absent`() = runTest {
        val sessions = TestParentSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = emptyList()),
        )
        val store = TestParentControlStore(children = emptyList())
        val authClient = TestAuthClient(
            email = "gokulakrishnanr812@gmail.com",
            displayName = null,
        )

        val vm = DashboardViewModel(sessions, store, authClient)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceTimeBy(AppConfig.DASHBOARD_CHILDREN_DEBOUNCE_MS + 100)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals("Gokulakrishnanr", (state as UiState.Success).data.greetingName)
    }

    @Test
    fun `togglePause toggles paused state in store`() = runTest {
        val testChild = FamilyChildProfile(
            childId = "c1",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.LION,
            language = "en",
        )
        val store = TestParentControlStore(
            children = listOf(testChild),
            policy = ChildPolicy(dailyCeilingMinutes = 120, paused = false),
        )
        val sessions = TestParentSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = listOf("c1")),
        )
        val authClient = TestAuthClient(email = "sarah@example.com")

        val vm = DashboardViewModel(sessions, store, authClient)
        advanceUntilIdle()

        vm.togglePause("c1")
        advanceUntilIdle()

        assertTrue(store.pausedStates["c1"] == true)
    }

    @Test
    fun `grantBonus adds bonus time in store`() = runTest {
        val testChild = FamilyChildProfile(
            childId = "c1",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.LION,
            language = "en",
        )
        val store = TestParentControlStore(
            children = listOf(testChild),
            policy = ChildPolicy(dailyCeilingMinutes = 120, paused = false),
        )
        val sessions = TestParentSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = listOf("c1")),
        )
        val authClient = TestAuthClient(email = "sarah@example.com")

        val vm = DashboardViewModel(sessions, store, authClient)
        advanceUntilIdle()

        vm.grantBonus("c1", 15)
        advanceUntilIdle()

        assertEquals(15, store.bonusAdded["c1"])
    }

    @Test
    fun `uiState computes comprehensive learning analytics overview`() = runTest {
        val testChild = FamilyChildProfile(
            childId = "c1",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.LION,
            language = "en",
        )
        val store = TestParentControlStore(
            children = listOf(testChild),
            policy = ChildPolicy(dailyCeilingMinutes = 120, paused = false),
            usageDays = listOf(
                UsageDaySummary(
                    day = "2026-09-18",
                    minutesUsed = 75,
                    minutesByApp = mapOf("com.khanacademy.android" to 45, "com.google.android.youtube.kids" to 30),
                ),
            ),
            quizAttempts = listOf(
                QuizAttemptSummary(
                    attemptId = "q1",
                    createdAtEpochMs = System.currentTimeMillis(),
                    topics = listOf("Math"),
                    score = 3,
                    total = 3,
                    passed = true,
                    extraMinutesGranted = 30,
                ),
            ),
            skillState = listOf(
                TopicSkillSummary(
                    topic = "Math",
                    level = 3,
                    streakCorrect = 4,
                    weak = false,
                ),
            ),
        )
        val sessions = TestParentSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = listOf("c1")),
        )
        val authClient = TestAuthClient(email = "sarah@example.com")

        val vm = DashboardViewModel(sessions, store, authClient)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceTimeBy(AppConfig.DASHBOARD_CHILDREN_DEBOUNCE_MS + 100)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is UiState.Success)
        val data = (state as UiState.Success).data
        assertEquals(1, data.analytics.modulesCompleted)
        assertEquals(100, data.analytics.accuracyPercent)
        assertTrue(data.analytics.totalAiMinutes > 0)
        assertEquals(7, data.analytics.dailyTrend.size)
        assertTrue(data.analytics.subjectBreakdowns.isNotEmpty())
        assertTrue(data.analytics.aiRecommendation != null)
    }

    @Test
    fun `setTimeRange updates selected time filter reactively`() = runTest {
        val sessions = TestParentSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = emptyList()),
        )
        val store = TestParentControlStore(children = emptyList())
        val authClient = TestAuthClient(email = "sarah@example.com")

        val vm = DashboardViewModel(sessions, store, authClient)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceTimeBy(AppConfig.DASHBOARD_CHILDREN_DEBOUNCE_MS + 100)
        advanceUntilIdle()

        vm.setTimeRange(com.meritscreen.feature.parent.ui.DashboardTimeRange.MONTH)
        advanceTimeBy(AppConfig.DASHBOARD_CHILDREN_DEBOUNCE_MS + 100)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals(com.meritscreen.feature.parent.ui.DashboardTimeRange.MONTH, (state as UiState.Success).data.selectedTimeRange)
    }

    @Test
    fun `selectChild updates selected child filter reactively`() = runTest {
        val testChild = FamilyChildProfile(
            childId = "c1",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.LION,
            language = "en",
        )
        val sessions = TestParentSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = listOf("c1")),
        )
        val store = TestParentControlStore(children = listOf(testChild))
        val authClient = TestAuthClient(email = "sarah@example.com")

        val vm = DashboardViewModel(sessions, store, authClient)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        advanceTimeBy(AppConfig.DASHBOARD_CHILDREN_DEBOUNCE_MS + 100)
        advanceUntilIdle()

        vm.selectChild("c1")
        advanceTimeBy(AppConfig.DASHBOARD_CHILDREN_DEBOUNCE_MS + 100)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals("c1", (state as UiState.Success).data.selectedChildId)
    }
}

private class TestParentSessionRepository(
    initial: ParentSession? = null,
) : ParentSessionRepository {
    private val state = MutableStateFlow(initial)
    override val session: Flow<ParentSession?> = state
    override suspend fun current(): ParentSession? = state.value
    override suspend fun set(session: ParentSession) { state.value = session }
    override suspend fun clear() { state.value = null }
}

private class TestAuthClient(
    val email: String? = "test@example.com",
    val displayName: String? = "Sarah",
) : AuthClient {
    override val currentUser: AuthUser? = email?.let {
        AuthUser(
            uid = "u1",
            email = it,
            displayName = displayName,
            isEmailVerified = true,
            isChildDevice = false,
        )
    }
    override val authState: Flow<AuthUser?> = flowOf(currentUser)
    override suspend fun refreshCurrentUser(): AuthUser? = currentUser
    override suspend fun ensureIdToken(forceRefresh: Boolean) = Unit
    override suspend fun signUpWithEmail(email: String, password: String): AuthUser = currentUser!!
    override suspend fun signInWithEmail(email: String, password: String): AuthUser = currentUser!!
    override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser = currentUser!!
    override suspend fun signInWithCustomToken(customToken: String): AuthUser = currentUser!!
    override suspend fun sendPasswordReset(email: String) = Unit
    override suspend fun sendEmailVerification() = Unit
    override suspend fun signOut() = Unit
}

private class TestParentControlStore(
    var children: List<FamilyChildProfile> = emptyList(),
    var policy: ChildPolicy = ChildPolicy(),
    var usageDays: List<UsageDaySummary> = emptyList(),
    var devices: List<DeviceSummary> = emptyList(),
    var quizAttempts: List<QuizAttemptSummary> = emptyList(),
    var skillState: List<TopicSkillSummary> = emptyList(),
) : ParentControlStore {
    val pausedStates = mutableMapOf<String, Boolean>()
    val bonusAdded = mutableMapOf<String, Int>()

    override suspend fun getFamilyMeta(familyId: String): FamilyMeta? =
        FamilyMeta(familyId, "The Miller Family", "u1")

    override suspend fun listChildren(familyId: String): List<FamilyChildProfile> = children
    override suspend fun addChild(familyId: String, child: FamilyDraftChild): FamilyChildProfile =
        FamilyChildProfile(child.localId, child.name, child.ageBand, child.avatar, child.language)

    override suspend fun updateChild(
        familyId: String,
        childId: String,
        child: FamilyDraftChild,
    ): FamilyChildProfile = FamilyChildProfile(childId, child.name, child.ageBand, child.avatar, child.language)

    override suspend fun deleteChild(familyId: String, childId: String) = Unit

    override suspend fun getPolicy(familyId: String, childId: String): ChildPolicy =
        policy.copy(paused = pausedStates[childId] ?: policy.paused)

    override suspend fun updatePolicy(familyId: String, childId: String, policy: ChildPolicy) {
        this.policy = policy
    }

    override suspend fun setChildPaused(familyId: String, childId: String, paused: Boolean) {
        pausedStates[childId] = paused
    }

    override suspend fun addBonusTime(familyId: String, childId: String, bonusMinutes: Int) {
        bonusAdded[childId] = (bonusAdded[childId] ?: 0) + bonusMinutes
    }

    override suspend fun listAppRules(familyId: String, childId: String): List<AppRule> = emptyList()
    override suspend fun upsertAppRule(familyId: String, childId: String, rule: AppRule) = Unit
    override suspend fun deleteAppRule(familyId: String, childId: String, appId: String) = Unit
    override suspend fun listUsageDays(familyId: String, childId: String, limit: Int): List<UsageDaySummary> = usageDays
    override suspend fun listQuizAttempts(familyId: String, childId: String, limit: Int): List<QuizAttemptSummary> = quizAttempts
    override suspend fun getSkillState(familyId: String, childId: String): List<TopicSkillSummary> = skillState
    override suspend fun listDevices(familyId: String, childId: String): List<DeviceSummary> = devices
    override suspend fun setDeviceRevoked(familyId: String, childId: String, deviceId: String, revoked: Boolean) = Unit
    override suspend fun listInstalledApps(familyId: String, childId: String): List<InstalledAppSummary> = emptyList()
    override fun observeInstalledApps(familyId: String, childId: String): Flow<List<InstalledAppSummary>> = flowOf(emptyList())
    override fun observeDevices(familyId: String, childId: String): Flow<List<DeviceSummary>> = flowOf(devices)
    override fun observeChildren(familyId: String): Flow<List<FamilyChildProfile>> = flowOf(children)
    override suspend fun deleteFamily(familyId: String) = Unit
}
