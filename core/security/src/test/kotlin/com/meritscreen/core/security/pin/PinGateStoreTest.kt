package com.meritscreen.core.security.pin

import com.meritscreen.core.security.storage.SecureStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinGateStoreTest {

    @Test
    fun `persists failed attempts and lockout across reloads`() = runTest {
        val storage = FakeSecureStorage()
        val store = PinGateStore(storage)
        store.set(PersistedPinGate(failedAttempts = 5, lockedUntilEpochMs = 99L))
        val reloaded = PinGateStore(storage).get()
        assertEquals(5, reloaded.failedAttempts)
        assertEquals(99L, reloaded.lockedUntilEpochMs)
    }

    @Test
    fun `clear removes lockout state`() = runTest {
        val storage = FakeSecureStorage()
        val store = PinGateStore(storage)
        store.set(PersistedPinGate(failedAttempts = 3, lockedUntilEpochMs = 1L))
        store.clear()
        val cleared = store.get()
        assertEquals(0, cleared.failedAttempts)
        assertNull(cleared.lockedUntilEpochMs)
    }
}

private class FakeSecureStorage : SecureStorage {
    private val values = mutableMapOf<String, String>()
    override suspend fun put(key: String, value: String) {
        values[key] = value
    }
    override suspend fun get(key: String): String? = values[key]
    override suspend fun remove(key: String) {
        values.remove(key)
    }
    override suspend fun clear() {
        values.clear()
    }
}
