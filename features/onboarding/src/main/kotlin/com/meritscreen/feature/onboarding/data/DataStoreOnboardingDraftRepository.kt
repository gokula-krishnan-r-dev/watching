package com.meritscreen.feature.onboarding.data

import androidx.datastore.core.DataStore
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.feature.onboarding.domain.ChildDraft
import com.meritscreen.feature.onboarding.domain.OnboardingDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [store] is injected rather than built from a `Context` inside this class, so the repository
 * can be unit tested on the JVM against a temp-file [DataStore] with no Android dependency.
 */
@Singleton
class DataStoreOnboardingDraftRepository @Inject constructor(
    private val store: DataStore<OnboardingDraft>,
) : OnboardingDraftRepository {

    override val draft: Flow<OnboardingDraft> = store.data

    override suspend fun setConsentGiven(given: Boolean) {
        store.updateData { it.copy(consentGiven = given) }
    }

    override suspend fun setFamilyName(name: String) {
        store.updateData { it.copy(familyName = name.trim().take(MAX_FAMILY_NAME_LENGTH)) }
    }

    override suspend fun addChild(child: ChildDraft): Outcome<Unit> {
        var outcome: Outcome<Unit> = Outcome.Success(Unit)
        store.updateData { current ->
            if (current.children.size >= AppConfig.MAX_CHILDREN_PER_PARENT) {
                outcome = Outcome.Failure(
                    AppError.Validation(
                        "You can add up to ${AppConfig.MAX_CHILDREN_PER_PARENT} children for now.",
                    ),
                )
                current
            } else {
                current.copy(children = current.children + child)
            }
        }
        return outcome
    }

    override suspend fun removeChild(localId: String) {
        store.updateData { current ->
            current.copy(children = current.children.filterNot { it.localId == localId })
        }
    }

    override suspend fun setParentPinHash(hash: String) {
        store.updateData { it.copy(parentPinHash = hash) }
    }

    override suspend fun current(): OnboardingDraft = store.data.first()

    override suspend fun clear() {
        store.updateData { OnboardingDraft() }
    }

    private companion object {
        const val MAX_FAMILY_NAME_LENGTH = 60
    }
}
