package com.meritscreen.feature.authentication.domain

import com.meritscreen.core.analytics.AnalyticsEvent
import com.meritscreen.core.analytics.AnalyticsTracker
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.common.session.SessionRoleRepository
import com.meritscreen.core.firebase.auth.AuthClient
import com.meritscreen.feature.onboarding.data.OnboardingDraftRepository
import javax.inject.Inject

/**
 * Signs the parent out of Firebase Auth and clears local parent/report cache and device role.
 * Child device pairing is not touched — those credentials live on the child's phone.
 */
class SignOutParentUseCase @Inject constructor(
    private val authClient: AuthClient,
    private val parentSessionRepository: ParentSessionRepository,
    private val sessionRoleRepository: SessionRoleRepository,
    private val draftRepository: OnboardingDraftRepository,
    private val analyticsTracker: AnalyticsTracker,
) {
    suspend operator fun invoke() {
        authClient.signOut()
        parentSessionRepository.clear()
        draftRepository.clear()
        sessionRoleRepository.clear()
        analyticsTracker.track(AnalyticsEvent.ParentSignedOut)
    }
}
