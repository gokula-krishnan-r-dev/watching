package com.meritscreen.feature.onboarding.ui

import com.meritscreen.core.common.session.ParentSession
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.firebase.family.FamilyStore
import com.meritscreen.core.security.pin.PinHasher
import com.meritscreen.core.testing.MainDispatcherRule
import com.meritscreen.feature.onboarding.data.FakeOnboardingDraftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Trivial fake — never invoke a real hasher from a unit test; behavior is covered separately. */
private class FakePinHasher : PinHasher {
    var lastHashedPin: String? = null
    override fun hash(pin: String): String {
        lastHashedPin = pin
        return "hashed:$pin"
    }
    override fun verify(pin: String, storedHash: String): Boolean = storedHash == "hashed:$pin"
}

private class FakeParentSessionRepository(
    initial: ParentSession? = null,
) : ParentSessionRepository {
    private val state = MutableStateFlow(initial)
    override val session: Flow<ParentSession?> = state
    override suspend fun current(): ParentSession? = state.value
    override suspend fun set(session: ParentSession) { state.value = session }
    override suspend fun clear() { state.value = null }
}

private class RecordingFamilyStore : FamilyStore by UnsupportedFamilyStore {
    var lastPinUpdate: Pair<String, String>? = null
    override suspend fun updateParentPinHash(familyId: String, pinHash: String) {
        lastPinUpdate = familyId to pinHash
    }
}

/** Minimal stub — only [updateParentPinHash] is exercised in these tests. */
private object UnsupportedFamilyStore : FamilyStore {
    override suspend fun getUserFamilyId(uid: String) = error("unused")
    override suspend fun listChildren(familyId: String) = error("unused")
    override suspend fun createFamilyFromDraft(
        uid: String,
        email: String?,
        draft: com.meritscreen.core.firebase.family.FamilyDraft,
    ) = error("unused")
    override suspend fun updateParentPinHash(familyId: String, pinHash: String) = error("unused")
    override fun observeDeviceCount(familyId: String, childId: String) = error("unused")
}

class SetParentPinViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun rejectsPinShorterThanMinimum() = runTest {
        val viewModel = SetParentPinViewModel(
            FakeOnboardingDraftRepository(),
            FakePinHasher(),
            FakeParentSessionRepository(),
            RecordingFamilyStore(),
        )

        viewModel.onPinChanged("12")
        viewModel.onConfirmPinChanged("12")
        viewModel.savePin()

        assertNotNull(viewModel.formError.value)
        assertFalse(viewModel.pinSaved.value)
    }

    @Test
    fun rejectsMismatchedConfirmation() = runTest {
        val viewModel = SetParentPinViewModel(
            FakeOnboardingDraftRepository(),
            FakePinHasher(),
            FakeParentSessionRepository(),
            RecordingFamilyStore(),
        )

        viewModel.onPinChanged("1234")
        viewModel.onConfirmPinChanged("4321")
        viewModel.savePin()

        assertEquals("PINs don't match. Try again.", viewModel.formError.value)
        assertFalse(viewModel.pinSaved.value)
    }

    @Test
    fun validPinIsHashedAndPersistedNeverInPlainText() = runTest {
        val repository = FakeOnboardingDraftRepository()
        val hasher = FakePinHasher()
        val familyStore = RecordingFamilyStore()
        val viewModel = SetParentPinViewModel(
            repository,
            hasher,
            FakeParentSessionRepository(
                ParentSession(uid = "u1", familyId = "f1", childIds = listOf("c1")),
            ),
            familyStore,
        )

        viewModel.onPinChanged("4829")
        viewModel.onConfirmPinChanged("4829")
        viewModel.savePin()

        assertTrue(viewModel.pinSaved.value)
        assertEquals("4829", hasher.lastHashedPin)
        assertEquals("hashed:4829", repository.draft.first().parentPinHash)
        assertEquals("f1" to "hashed:4829", familyStore.lastPinUpdate)
        assertEquals("", viewModel.pin.value)
        assertEquals("", viewModel.confirmPin.value)
    }
}
