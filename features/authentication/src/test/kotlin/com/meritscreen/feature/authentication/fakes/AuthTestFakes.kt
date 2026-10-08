package com.meritscreen.feature.authentication.fakes

import com.meritscreen.core.analytics.AnalyticsEvent
import com.meritscreen.core.analytics.AnalyticsTracker
import com.meritscreen.core.common.session.ParentSession
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.firebase.auth.AuthClient
import com.meritscreen.core.firebase.auth.AuthUser
import com.meritscreen.core.firebase.family.CreatedFamily
import com.meritscreen.core.firebase.family.FamilyChildProfile
import com.meritscreen.core.firebase.family.FamilyDraft
import com.meritscreen.core.firebase.family.FamilyStore
import com.meritscreen.core.firebase.pairing.PairingClient
import com.meritscreen.core.firebase.pairing.PairingOffer
import com.meritscreen.core.firebase.pairing.PairingResult
import com.meritscreen.core.security.pairing.ChildPairingCredential
import com.meritscreen.core.security.pairing.ChildPairingStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) {
        events += event
    }
}

class FakeParentSessionRepository : ParentSessionRepository {
    private val state = MutableStateFlow<ParentSession?>(null)
    override val session: Flow<ParentSession?> = state
    override suspend fun current(): ParentSession? = state.value
    override suspend fun set(session: ParentSession) {
        state.value = session
    }
    override suspend fun clear() {
        state.value = null
    }
}

class FakeAuthClient(
    initial: AuthUser? = null,
) : AuthClient {
    var user: AuthUser? = initial
    var signedOut = false
    var lastEmail: String? = null
    var lastPassword: String? = null
    var lastCustomToken: String? = null
    var passwordResetEmail: String? = null

    override val currentUser: AuthUser? get() = user
    override val authState: Flow<AuthUser?> = flowOf(user)

    override suspend fun refreshCurrentUser(): AuthUser? = user

    override suspend fun ensureIdToken(forceRefresh: Boolean) = Unit

    override suspend fun signUpWithEmail(email: String, password: String): AuthUser {
        lastEmail = email
        lastPassword = password
        user = AuthUser(uid = "uid-new", email = email, displayName = null, isEmailVerified = false, isChildDevice = false)
        return user!!
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthUser {
        lastEmail = email
        lastPassword = password
        user = AuthUser(uid = "uid-existing", email = email, displayName = null, isEmailVerified = true, isChildDevice = false)
        return user!!
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser {
        user = AuthUser(uid = "uid-google", email = "g@example.com", displayName = "G", isEmailVerified = true, isChildDevice = false)
        return user!!
    }

    override suspend fun signInWithCustomToken(customToken: String): AuthUser {
        lastCustomToken = customToken
        val isChild = customToken.contains("child") || customToken.startsWith("dev_")
        user = AuthUser(
            uid = if (isChild) "dev_abc" else "uid-parent-otp",
            email = if (isChild) null else "parent@example.com",
            displayName = null,
            isEmailVerified = true,
            isChildDevice = isChild,
        )
        return user!!
    }


    override suspend fun sendPasswordReset(email: String) {
        passwordResetEmail = email
    }

    override suspend fun sendEmailVerification() = Unit

    override suspend fun signOut() {
        signedOut = true
        user = null
    }
}

class FakeFamilyStore(
    private var familyIdForUser: String? = null,
    private var children: List<FamilyChildProfile> = emptyList(),
) : FamilyStore {
    var createdDraft: FamilyDraft? = null
    var updatedPinHash: String? = null
    var createdFamilyId: String = "family-1"

    override suspend fun getUserFamilyId(uid: String): String? = familyIdForUser

    override suspend fun listChildren(familyId: String): List<FamilyChildProfile> = children

    override suspend fun createFamilyFromDraft(uid: String, email: String?, draft: FamilyDraft): CreatedFamily {
        createdDraft = draft
        familyIdForUser = createdFamilyId
        children = draft.children.map {
            FamilyChildProfile(
                childId = it.localId,
                displayName = it.name,
                ageBand = it.ageBand,
                avatar = it.avatar,
                language = it.language,
            )
        }
        return CreatedFamily(familyId = createdFamilyId, children = children)
    }

    override suspend fun updateParentPinHash(familyId: String, pinHash: String) {
        updatedPinHash = pinHash
    }

    override fun observeDeviceCount(familyId: String, childId: String): Flow<Int> = flowOf(0)
}

class FakePairingClient(
    private val result: PairingResult = PairingResult(
        customToken = "custom-token",
        familyId = "family-1",
        childId = "child-1",
        deviceId = "device-1",
        parentPinHash = "pbkdf2\$hash",
        displayName = "Ada",
        ageBand = "AGE_7_TO_9",
        avatarId = "LION",
    ),
) : PairingClient {
    var lastCode: String? = null
    var lastDeviceId: String? = null

    override suspend fun createToken(childId: String): PairingOffer =
        PairingOffer("123456", "secret", 0L, "meritscreen://pair", childId, "family-1")

    override suspend fun consumeToken(code: String, secret: String?, deviceId: String): PairingResult {
        lastCode = code
        lastDeviceId = deviceId
        return result.copy(deviceId = deviceId)
    }

    override suspend fun activateChildOnDevice(childId: String, deviceId: String): PairingResult =
        result.copy(childId = childId, deviceId = deviceId)
}

class FakeChildPairingStore(
    private var credential: ChildPairingCredential? = null,
    private val deviceId: String = "deviceabcdef12",
) : ChildPairingStore {
    override suspend fun get(): ChildPairingCredential? = credential
    override suspend fun set(credential: ChildPairingCredential) {
        this.credential = credential
    }
    override suspend fun getOrCreateDeviceId(): String = deviceId
    override suspend fun clear() {
        credential = null
    }
}

class FakeOnboardingDraftRepository(
    initial: com.meritscreen.feature.onboarding.domain.OnboardingDraft =
        com.meritscreen.feature.onboarding.domain.OnboardingDraft(),
) : com.meritscreen.feature.onboarding.data.OnboardingDraftRepository {
    private val state = MutableStateFlow(initial)
    override val draft: Flow<com.meritscreen.feature.onboarding.domain.OnboardingDraft> = state
    override suspend fun setConsentGiven(given: Boolean) {
        state.value = state.value.copy(consentGiven = given)
    }
    override suspend fun setFamilyName(name: String) {
        state.value = state.value.copy(familyName = name)
    }
    override suspend fun addChild(child: com.meritscreen.feature.onboarding.domain.ChildDraft) =
        com.meritscreen.core.common.result.Outcome.Success(Unit)
    override suspend fun removeChild(localId: String) = Unit
    override suspend fun setParentPinHash(hash: String) {
        state.value = state.value.copy(parentPinHash = hash)
    }
    override suspend fun current() = state.value
    override suspend fun clear() {
        state.value = com.meritscreen.feature.onboarding.domain.OnboardingDraft()
    }
}

class FakeEmailOtpClient : com.meritscreen.core.firebase.auth.EmailOtpClient {
    var lastSentEmail: String? = null
    var shouldFailSend = false
    var shouldFailVerify = false
    var customTokenToReturn = "custom-token-parent"

    override suspend fun sendOtp(email: String) {
        if (shouldFailSend) throw com.meritscreen.core.common.error.AppErrorException(
            com.meritscreen.core.common.error.AppError.Network("Failed to send OTP"),
        )
        lastSentEmail = email
    }

    override suspend fun verifyOtp(email: String, code: String): String {
        if (shouldFailVerify) throw com.meritscreen.core.common.error.AppErrorException(
            com.meritscreen.core.common.error.AppError.Auth("Invalid OTP code"),
        )
        return customTokenToReturn
    }
}

