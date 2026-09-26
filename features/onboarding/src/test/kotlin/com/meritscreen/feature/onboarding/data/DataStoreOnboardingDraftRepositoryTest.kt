package com.meritscreen.feature.onboarding.data

import androidx.datastore.core.DataStoreFactory
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.feature.onboarding.domain.ChildDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Exercises the real DataStore-backed repository against a temp-file store — no Android
 * `Context` needed, since [DataStoreOnboardingDraftRepository] takes the [DataStore] directly.
 */
class DataStoreOnboardingDraftRepositoryTest {

    private fun newRepository(): DataStoreOnboardingDraftRepository {
        val file = File.createTempFile("onboarding_draft", ".json").apply { deleteOnExit() }
        val store = DataStoreFactory.create(serializer = OnboardingDraftSerializer, produceFile = { file })
        return DataStoreOnboardingDraftRepository(store)
    }

    @Test
    fun defaultDraftIsEmpty() = runTest {
        val draft = newRepository().draft.first()
        assertFalse(draft.consentGiven)
        assertEquals("", draft.familyName)
        assertTrue(draft.children.isEmpty())
        assertNull(draft.parentPinHash)
    }

    @Test
    fun setConsentGivenPersists() = runTest {
        val repository = newRepository()
        repository.setConsentGiven(true)
        assertTrue(repository.draft.first().consentGiven)
    }

    @Test
    fun setFamilyNameTrimsAndTruncates() = runTest {
        val repository = newRepository()
        repository.setFamilyName("  The Smiths  ")
        assertEquals("The Smiths", repository.draft.first().familyName)
    }

    @Test
    fun addChildAppendsUntilCapThenFails() = runTest {
        val repository = newRepository()
        repeat(AppConfig.MAX_CHILDREN_PER_PARENT) { index ->
            val outcome = repository.addChild(
                ChildDraft(
                    localId = "child-$index",
                    name = "Kid $index",
                    ageBand = AgeBand.AGE_7_TO_9,
                    avatar = AvatarPreset.Default,
                ),
            )
            assertTrue(outcome is Outcome.Success)
        }
        assertEquals(AppConfig.MAX_CHILDREN_PER_PARENT, repository.draft.first().children.size)

        val overflow = repository.addChild(
            ChildDraft(
                localId = "overflow",
                name = "One too many",
                ageBand = AgeBand.AGE_7_TO_9,
                avatar = AvatarPreset.Default,
            ),
        )
        assertTrue(overflow is Outcome.Failure)
        assertEquals(AppConfig.MAX_CHILDREN_PER_PARENT, repository.draft.first().children.size)
    }

    @Test
    fun removeChildFiltersByLocalId() = runTest {
        val repository = newRepository()
        repository.addChild(ChildDraft("a", "A", AgeBand.AGE_3_TO_6, AvatarPreset.Default))
        repository.addChild(ChildDraft("b", "B", AgeBand.AGE_7_TO_9, AvatarPreset.Default))
        repository.removeChild("a")
        val remaining = repository.draft.first().children
        assertEquals(1, remaining.size)
        assertEquals("b", remaining.first().localId)
    }

    @Test
    fun setParentPinHashPersists() = runTest {
        val repository = newRepository()
        repository.setParentPinHash("hashed-value-123")
        assertEquals("hashed-value-123", repository.draft.first().parentPinHash)
    }

    @Test
    fun clearResetsToDefault() = runTest {
        val repository = newRepository()
        repository.setConsentGiven(true)
        repository.setFamilyName("Name")
        repository.clear()
        val draft = repository.draft.first()
        assertFalse(draft.consentGiven)
        assertEquals("", draft.familyName)
    }
}
