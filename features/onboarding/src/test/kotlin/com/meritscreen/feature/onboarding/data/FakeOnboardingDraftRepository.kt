package com.meritscreen.feature.onboarding.data

import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.feature.onboarding.domain.ChildDraft
import com.meritscreen.feature.onboarding.domain.OnboardingDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory test double so ViewModel tests do not need a real DataStore/file system. */
class FakeOnboardingDraftRepository(
    initial: OnboardingDraft = OnboardingDraft(),
) : OnboardingDraftRepository {

    private val state = MutableStateFlow(initial)
    override val draft: Flow<OnboardingDraft> = state

    override suspend fun setConsentGiven(given: Boolean) {
        state.value = state.value.copy(consentGiven = given)
    }

    override suspend fun setFamilyName(name: String) {
        state.value = state.value.copy(familyName = name.trim())
    }

    override suspend fun addChild(child: ChildDraft): Outcome<Unit> {
        val current = state.value
        if (current.children.size >= AppConfig.MAX_CHILDREN_PER_PARENT) {
            return Outcome.Failure(
                AppError.Validation("You can add up to ${AppConfig.MAX_CHILDREN_PER_PARENT} children for now."),
            )
        }
        state.value = current.copy(children = current.children + child)
        return Outcome.Success(Unit)
    }

    override suspend fun removeChild(localId: String) {
        state.value = state.value.copy(children = state.value.children.filterNot { it.localId == localId })
    }

    override suspend fun setParentPinHash(hash: String) {
        state.value = state.value.copy(parentPinHash = hash)
    }

    override suspend fun current(): OnboardingDraft = state.value

    override suspend fun clear() {
        state.value = OnboardingDraft()
    }
}
