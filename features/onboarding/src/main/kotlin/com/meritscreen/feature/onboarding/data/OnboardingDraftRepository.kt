package com.meritscreen.feature.onboarding.data

import com.meritscreen.core.common.result.Outcome
import com.meritscreen.feature.onboarding.domain.ChildDraft
import com.meritscreen.feature.onboarding.domain.OnboardingDraft
import kotlinx.coroutines.flow.Flow

/** Local, pre-authentication storage for the parent first-run wizard. See [OnboardingDraft]. */
interface OnboardingDraftRepository {
    val draft: Flow<OnboardingDraft>

    suspend fun setConsentGiven(given: Boolean)
    suspend fun setFamilyName(name: String)

    /** Fails with [com.meritscreen.core.common.error.AppError.Validation] past the child cap. */
    suspend fun addChild(child: ChildDraft): Outcome<Unit>
    suspend fun removeChild(localId: String)

    suspend fun setParentPinHash(hash: String)

    suspend fun current(): OnboardingDraft

    /** Called once the draft has been committed to Firestore after sign-up (Phase 3). */
    suspend fun clear()
}
