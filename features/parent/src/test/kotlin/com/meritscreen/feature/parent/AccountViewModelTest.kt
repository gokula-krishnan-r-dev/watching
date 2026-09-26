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
import com.meritscreen.feature.parent.ui.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

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
    fun uiState_emitsSuccessWithCorrectUserDetailsAndChildren() = runTest {
        val authClient = AccountTestAuthClient(
            email = "gokulakrishnanr812@gmail.com",
            displayName = "Gokulakrishnanr",
        )
        val sessions = AccountTestParentSessionRepository(
            initialSession = ParentSession(uid = "parent_123", familyId = "fam_1", childIds = listOf("c1")),
        )
        val child = FamilyChildProfile(
            childId = "c1",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.LION,
            language = "en",
        )
        val store = AccountTestParentControlStore(
            familyMeta = FamilyMeta("fam_1", "The Miller Family", "parent_123"),
            children = listOf(child),
        )

        val vm = AccountViewModel(authClient, sessions, store)

        val state = vm.uiState.value
        assertTrue("Expected UiState.Success but got $state", state is UiState.Success)
        val data = (state as UiState.Success).data
        assertEquals("gokulakrishnanr812@gmail.com", data.email)
        assertEquals("Gokulakrishnanr", data.displayName)
        assertEquals("The Miller Family", data.familyName)
        assertEquals(1, data.childCount)
        assertEquals(1, data.children.size)
        assertEquals("Leo", data.children.first().displayName)
    }

    @Test
    fun uiState_emitsErrorWhenSessionMissing() = runTest {
        val authClient = AccountTestAuthClient(email = null, displayName = null)
        val sessions = AccountTestParentSessionRepository(initialSession = null)
        val store = AccountTestParentControlStore()

        val vm = AccountViewModel(authClient, sessions, store)

        val state = vm.uiState.value
        assertTrue("Expected UiState.Error when session is missing", state is UiState.Error)
    }
}

private class AccountTestAuthClient(
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

private class AccountTestParentSessionRepository(
    var initialSession: ParentSession? = null,
) : ParentSessionRepository {
    override val session = MutableStateFlow(initialSession)
    override suspend fun current(): ParentSession? = session.value
    override suspend fun set(session: ParentSession) { this.session.value = session }
    override suspend fun clear() { this.session.value = null }
}

private class AccountTestParentControlStore(
    var familyMeta: FamilyMeta? = FamilyMeta("fam_1", "The Miller Family", "u1"),
    var children: List<FamilyChildProfile> = emptyList(),
) : ParentControlStore {
    override suspend fun getFamilyMeta(familyId: String): FamilyMeta? = familyMeta
    override suspend fun listChildren(familyId: String): List<FamilyChildProfile> = children
    override suspend fun addChild(familyId: String, child: FamilyDraftChild): FamilyChildProfile =
        FamilyChildProfile(child.localId, child.name, child.ageBand, child.avatar, child.language)
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
    override suspend fun listUsageDays(familyId: String, childId: String, limit: Int): List<UsageDaySummary> = emptyList()
    override suspend fun listQuizAttempts(familyId: String, childId: String, limit: Int): List<QuizAttemptSummary> = emptyList()
    override suspend fun getSkillState(familyId: String, childId: String): List<TopicSkillSummary> = emptyList()
    override suspend fun listDevices(familyId: String, childId: String): List<DeviceSummary> = emptyList()
    override suspend fun setDeviceRevoked(familyId: String, childId: String, deviceId: String, revoked: Boolean) = Unit
    override suspend fun listInstalledApps(familyId: String, childId: String): List<InstalledAppSummary> = emptyList()
    override fun observeInstalledApps(familyId: String, childId: String): Flow<List<InstalledAppSummary>> = flowOf(emptyList())
    override fun observeDevices(familyId: String, childId: String): Flow<List<DeviceSummary>> = flowOf(emptyList())
    override fun observeChildren(familyId: String): Flow<List<FamilyChildProfile>> = flowOf(children)
    override suspend fun deleteFamily(familyId: String) = Unit
}
