package com.meritscreen.feature.parent

import androidx.lifecycle.SavedStateHandle
import com.meritscreen.core.common.config.AppConfig
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
import com.meritscreen.core.firebase.family.FamilyChildProfile
import com.meritscreen.core.firebase.family.FamilyDraftChild
import com.meritscreen.core.firebase.family.FamilyMeta
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.feature.parent.ui.ChildDetailViewModel
import com.meritscreen.feature.parent.ui.DeviceConnectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class ChildDetailViewModelTest {

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
    fun `refresh loads child details and app breakdown correctly`() = runTest {
        val testChild = FamilyChildProfile(
            childId = "c1",
            displayName = "Leo Miller",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.LION,
            language = "en",
        )
        val store = ChildDetailTestStore(
            child = testChild,
            policy = ChildPolicy(dailyCeilingMinutes = 120, paused = false),
            devices = listOf(
                DeviceSummary(
                    deviceId = "d1",
                    model = "Pixel Tablet",
                    osVersion = "Android 14",
                    batteryPercent = 84,
                    lastSeenAtEpochMs = System.currentTimeMillis() - 90_000L,
                    launcherDefault = true,
                ),
            ),
            usageDays = listOf(
                UsageDaySummary(
                    day = "2026-09-18",
                    minutesUsed = 75,
                    minutesByApp = mapOf(
                        "com.khanacademy.android" to 45,
                        "com.google.android.youtube.kids" to 30,
                    ),
                ),
            ),
            quizAttempts = listOf(
                QuizAttemptSummary(
                    attemptId = "q1",
                    createdAtEpochMs = System.currentTimeMillis(),
                    topics = listOf("Math"),
                    score = 7,
                    total = 8,
                    passed = true,
                    extraMinutesGranted = 30,
                ),
            ),
            appRules = listOf(
                AppRule(
                    appId = "khan",
                    packageOrBundleId = "com.khanacademy.android",
                    displayName = "Khan Academy",
                    allowed = true,
                ),
                AppRule(
                    appId = "ytk",
                    packageOrBundleId = "com.google.android.youtube.kids",
                    displayName = "YouTube Kids",
                    allowed = true,
                ),
            ),
        )
        val sessions = ChildDetailTestSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = listOf("c1")),
        )
        val savedStateHandle = SavedStateHandle(mapOf("childId" to "c1"))

        val vm = ChildDetailViewModel(savedStateHandle, sessions, store)
        advanceTimeBy(AppConfig.CHILD_DETAIL_DEVICES_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected UiState.Success but was $state", state is UiState.Success)
        val data = (state as UiState.Success).data
        assertEquals("Leo Miller", data.profile.displayName)
        assertEquals(75, data.todayMinutes)
        assertEquals(120, data.dailyCeilingMinutes)
        assertEquals(45, data.remainingMinutes)
        assertFalse(data.isPaused)
        assertEquals(87, data.quizScorePercent)
        assertEquals("7 / 8 Correct", data.quizScoreFraction)
        assertEquals("Pixel Tablet • Android 14", data.deviceModelAndOs)
        assertEquals(84, data.batteryPercent)
        assertEquals(DeviceConnectionStatus.Connected, data.connectionStatus)
        assertEquals("Connected", data.connectionStatusLabel)
        assertEquals(2, data.appUsageBreakdown.size)
        assertEquals("Khan Academy", data.appUsageBreakdown[0].label)
        assertEquals(45, data.appUsageBreakdown[0].minutes)
        assertEquals("YouTube Kids", data.appUsageBreakdown[1].label)
        assertEquals(30, data.appUsageBreakdown[1].minutes)
        assertTrue(data.hasQuizData)
        assertTrue(data.hasUsageBreakdown)
    }

    @Test
    fun `togglePause flips isPaused and updates policy in store`() = runTest {
        val testChild = FamilyChildProfile(
            childId = "c1",
            displayName = "Leo Miller",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.LION,
            language = "en",
        )
        val store = ChildDetailTestStore(
            child = testChild,
            policy = ChildPolicy(dailyCeilingMinutes = 120, paused = false),
        )
        val sessions = ChildDetailTestSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = listOf("c1")),
        )
        val savedStateHandle = SavedStateHandle(mapOf("childId" to "c1"))

        val vm = ChildDetailViewModel(savedStateHandle, sessions, store)
        advanceTimeBy(AppConfig.CHILD_DETAIL_DEVICES_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        vm.togglePause()
        advanceUntilIdle()

        val dataAfterPause = (vm.uiState.value as UiState.Success).data
        assertTrue(dataAfterPause.isPaused)
        assertTrue(store.pausedStates["c1"] == true)

        vm.togglePause()
        advanceUntilIdle()

        val dataAfterResume = (vm.uiState.value as UiState.Success).data
        assertFalse(dataAfterResume.isPaused)
        assertFalse(store.pausedStates["c1"] == true)
    }

    @Test
    fun `grantBonus adds minutes to ceiling and shows toast banner`() = runTest {
        val testChild = FamilyChildProfile(
            childId = "c1",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.LION,
            language = "en",
        )
        val store = ChildDetailTestStore(
            child = testChild,
            policy = ChildPolicy(dailyCeilingMinutes = 120, paused = false),
            usageDays = listOf(UsageDaySummary(day = "2026-09-18", minutesUsed = 75)),
        )
        val sessions = ChildDetailTestSessionRepository(
            ParentSession(uid = "u1", familyId = "f1", childIds = listOf("c1")),
        )
        val savedStateHandle = SavedStateHandle(mapOf("childId" to "c1"))

        val vm = ChildDetailViewModel(savedStateHandle, sessions, store)
        advanceTimeBy(AppConfig.CHILD_DETAIL_DEVICES_DEBOUNCE_MS + 50)
        advanceUntilIdle()

        vm.grantBonus(15)
        advanceUntilIdle()

        val data = (vm.uiState.value as UiState.Success).data
        assertEquals(135, data.dailyCeilingMinutes)
        assertEquals(60, data.remainingMinutes)
        assertEquals(15, store.bonusAdded["c1"])
        assertNotNull(data.bonusToastMessage)
        assertTrue(data.bonusToastMessage!!.contains("15 minutes added to Leo's daily ceiling"))

        vm.dismissBonusToast()
        advanceUntilIdle()
        val dataDismissed = (vm.uiState.value as UiState.Success).data
        assertNull(dataDismissed.bonusToastMessage)
    }
}

private class ChildDetailTestSessionRepository(
    initial: ParentSession? = null,
) : ParentSessionRepository {
    private val state = MutableStateFlow(initial)
    override val session: Flow<ParentSession?> = state
    override suspend fun current(): ParentSession? = state.value
    override suspend fun set(session: ParentSession) { state.value = session }
    override suspend fun clear() { state.value = null }
}

private class ChildDetailTestStore(
    var child: FamilyChildProfile,
    var policy: ChildPolicy = ChildPolicy(),
    var devices: List<DeviceSummary> = emptyList(),
    var usageDays: List<UsageDaySummary> = emptyList(),
    var quizAttempts: List<QuizAttemptSummary> = emptyList(),
    var skillState: List<TopicSkillSummary> = emptyList(),
    var appRules: List<AppRule> = emptyList(),
) : ParentControlStore {
    val pausedStates = mutableMapOf<String, Boolean>()
    val bonusAdded = mutableMapOf<String, Int>()
    val deletedChildIds = mutableListOf<String>()

    override suspend fun getFamilyMeta(familyId: String): FamilyMeta? =
        FamilyMeta(familyId, "The Miller Family", "u1")

    override suspend fun listChildren(familyId: String): List<FamilyChildProfile> = listOf(child)
    override suspend fun addChild(familyId: String, child: FamilyDraftChild): FamilyChildProfile = this.child
    override suspend fun updateChild(
        familyId: String,
        childId: String,
        child: FamilyDraftChild,
    ): FamilyChildProfile {
        this.child = FamilyChildProfile(childId, child.name, child.ageBand, child.avatar, child.language)
        return this.child
    }
    override suspend fun deleteChild(familyId: String, childId: String) {
        deletedChildIds += childId
    }

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

    override suspend fun listAppRules(familyId: String, childId: String): List<AppRule> = appRules
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
    override fun observeChildren(familyId: String): Flow<List<FamilyChildProfile>> = flowOf(listOf(child))
    override suspend fun deleteFamily(familyId: String) = Unit
}
