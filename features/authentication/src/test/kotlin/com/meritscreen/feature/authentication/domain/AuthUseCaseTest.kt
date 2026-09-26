package com.meritscreen.feature.authentication.domain

import com.meritscreen.core.analytics.AnalyticsEvent
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.core.common.session.DeviceRole
import com.meritscreen.core.common.session.ParentSession
import com.meritscreen.core.firebase.auth.AuthUser
import com.meritscreen.core.testing.FakeNetworkMonitor
import com.meritscreen.core.testing.FakeSessionRoleRepository
import com.meritscreen.feature.authentication.fakes.FakeAnalyticsTracker
import com.meritscreen.feature.authentication.fakes.FakeAuthClient
import com.meritscreen.feature.authentication.fakes.FakeChildPairingStore
import com.meritscreen.feature.authentication.fakes.FakeFamilyStore
import com.meritscreen.feature.authentication.fakes.FakeOnboardingDraftRepository
import com.meritscreen.feature.authentication.fakes.FakePairingClient
import com.meritscreen.feature.authentication.fakes.FakeParentSessionRepository
import com.meritscreen.feature.onboarding.domain.ChildDraft
import com.meritscreen.feature.onboarding.domain.OnboardingDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteParentAuthUseCaseTest {

    private fun readyDraft() = OnboardingDraft(
        consentGiven = true,
        familyName = "Smith",
        children = listOf(
            ChildDraft("c1", "Ada", AgeBand.AGE_7_TO_9, AvatarPreset.FOX),
        ),
        parentPinHash = "pbkdf2\$hash",
    )

    @Test
    fun sendEmailOtpDelegatesToClient() = runTest {
        val otpClient = com.meritscreen.feature.authentication.fakes.FakeEmailOtpClient()
        val useCase = CompleteParentAuthUseCase(
            authClient = FakeAuthClient(),
            emailOtpClient = otpClient,
            familyStore = FakeFamilyStore(),
            draftRepository = FakeOnboardingDraftRepository(),
            parentSessionRepository = FakeParentSessionRepository(),
            networkMonitor = FakeNetworkMonitor(online = true),
            analyticsTracker = FakeAnalyticsTracker(),
        )

        val outcome = useCase.sendEmailOtp("parent@example.com")
        assertTrue(outcome is Outcome.Success)
        assertEquals("parent@example.com", otpClient.lastSentEmail)
    }

    @Test
    fun verifyOtpCreatesFamilyFromDraftAndStoresSession() = runTest {
        val draftRepo = FakeOnboardingDraftRepository(readyDraft())
        val familyStore = FakeFamilyStore()
        val parentSession = FakeParentSessionRepository()
        val analytics = FakeAnalyticsTracker()
        val otpClient = com.meritscreen.feature.authentication.fakes.FakeEmailOtpClient()
        val useCase = CompleteParentAuthUseCase(
            authClient = FakeAuthClient(),
            emailOtpClient = otpClient,
            familyStore = familyStore,
            draftRepository = draftRepo,
            parentSessionRepository = parentSession,
            networkMonitor = FakeNetworkMonitor(online = true),
            analyticsTracker = analytics,
        )

        val outcome = useCase.verifyEmailOtp("parent@example.com", "123456")
        check(outcome is Outcome.Success)
        assertTrue(outcome.value.isNewFamily)
        assertEquals("family-1", outcome.value.familyId)
        assertEquals(listOf("c1"), outcome.value.childIds)
        assertEquals("Smith", familyStore.createdDraft?.familyName)
        assertEquals("family-1", parentSession.current()?.familyId)
        assertFalse(draftRepo.current().isReadyToCommit)
        assertTrue(analytics.events.contains(AnalyticsEvent.ParentSignedIn))
    }

    @Test
    fun verifyOtpWithExistingFamilySkipsCreateAndMaySyncPin() = runTest {
        val draftRepo = FakeOnboardingDraftRepository(
            OnboardingDraft(consentGiven = true, parentPinHash = "new-pin-hash"),
        )
        val familyStore = FakeFamilyStore(familyIdForUser = "family-existing")
        val useCase = CompleteParentAuthUseCase(
            authClient = FakeAuthClient(),
            emailOtpClient = com.meritscreen.feature.authentication.fakes.FakeEmailOtpClient(),
            familyStore = familyStore,
            draftRepository = draftRepo,
            parentSessionRepository = FakeParentSessionRepository(),
            networkMonitor = FakeNetworkMonitor(online = true),
            analyticsTracker = FakeAnalyticsTracker(),
        )

        val outcome = useCase.verifyEmailOtp("parent@example.com", "123456")
        check(outcome is Outcome.Success)
        assertFalse(outcome.value.isNewFamily)
        assertEquals("family-existing", outcome.value.familyId)
        assertEquals("new-pin-hash", familyStore.updatedPinHash)
        assertNull(familyStore.createdDraft)
    }

    @Test
    fun verifyOtpWithoutFamilyOrDraftRequestsOnboarding() = runTest {
        val useCase = CompleteParentAuthUseCase(
            authClient = FakeAuthClient(),
            emailOtpClient = com.meritscreen.feature.authentication.fakes.FakeEmailOtpClient(),
            familyStore = FakeFamilyStore(),
            draftRepository = FakeOnboardingDraftRepository(),
            parentSessionRepository = FakeParentSessionRepository(),
            networkMonitor = FakeNetworkMonitor(online = true),
            analyticsTracker = FakeAnalyticsTracker(),
        )

        val outcome = useCase.verifyEmailOtp("parent@example.com", "123456")
        check(outcome is Outcome.Success)
        assertTrue(outcome.value.needsOnboarding)
        assertNull(outcome.value.familyId)
    }

    @Test
    fun offlineEmailOtpFailsWithNetworkError() = runTest {
        val useCase = CompleteParentAuthUseCase(
            authClient = FakeAuthClient(),
            emailOtpClient = com.meritscreen.feature.authentication.fakes.FakeEmailOtpClient(),
            familyStore = FakeFamilyStore(),
            draftRepository = FakeOnboardingDraftRepository(readyDraft()),
            parentSessionRepository = FakeParentSessionRepository(),
            networkMonitor = FakeNetworkMonitor(online = false),
            analyticsTracker = FakeAnalyticsTracker(),
        )

        val outcome = useCase.sendEmailOtp("parent@example.com")
        assertTrue(outcome is Outcome.Failure)
    }
}


class PairChildDeviceUseCaseTest {

    @Test
    fun rejectsShortCode() = runTest {
        val useCase = PairChildDeviceUseCase(
            pairingClient = FakePairingClient(),
            authClient = FakeAuthClient(),
            pairingStore = FakeChildPairingStore(),
            networkMonitor = FakeNetworkMonitor(online = true),
            analyticsTracker = FakeAnalyticsTracker(),
        )
        assertTrue(useCase("12", null) is Outcome.Failure)
    }

    @Test
    fun consumeStoresCredentialAndSignsInWithCustomToken() = runTest {
        val pairingStore = FakeChildPairingStore()
        val auth = FakeAuthClient()
        val analytics = FakeAnalyticsTracker()
        val pairingClient = FakePairingClient()
        val useCase = PairChildDeviceUseCase(
            pairingClient = pairingClient,
            authClient = auth,
            pairingStore = pairingStore,
            networkMonitor = FakeNetworkMonitor(online = true),
            analyticsTracker = analytics,
        )

        assertTrue(useCase("654321", null) is Outcome.Success)
        assertEquals("654321", pairingClient.lastCode)
        assertEquals("custom-token", auth.lastCustomToken)
        assertEquals("child-1", pairingStore.get()?.childId)
        assertEquals("pbkdf2\$hash", pairingStore.get()?.parentPinHash)
        assertTrue(analytics.events.contains(AnalyticsEvent.ChildDevicePaired))
    }
}

class SignOutParentUseCaseTest {

    @Test
    fun clearsAuthSessionDraftAndRole() = runTest {
        val auth = FakeAuthClient(
            initial = AuthUser(
                uid = "uid",
                email = "a@b.com",
                displayName = null,
                isEmailVerified = true,
                isChildDevice = false,
            ),
        )
        val parentSession = FakeParentSessionRepository()
        parentSession.set(ParentSession(uid = "uid", familyId = "family-1"))
        val roles = FakeSessionRoleRepository(initial = DeviceRole.Parent)
        val draft = FakeOnboardingDraftRepository(OnboardingDraft(consentGiven = true, familyName = "X"))
        val analytics = FakeAnalyticsTracker()

        SignOutParentUseCase(auth, parentSession, roles, draft, analytics).invoke()

        assertTrue(auth.signedOut)
        assertNull(parentSession.current())
        assertEquals(DeviceRole.Unassigned, roles.role.first())
        assertEquals("", draft.current().familyName)
        assertTrue(analytics.events.contains(AnalyticsEvent.ParentSignedOut))
    }
}
