package com.meritscreen.core.security.pairing

import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.security.storage.SecureStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SecureChildPairingStoreTest {

    private val memory = object : SecureStorage {
        private val map = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = map[key]
        override suspend fun put(key: String, value: String) {
            map[key] = value
        }
        override suspend fun remove(key: String) {
            map.remove(key)
        }
        override suspend fun clear() {
            map.clear()
        }
    }

    private fun store() = SecureChildPairingStore(memory)

    private fun cred(id: String, name: String) = ChildPairingCredential(
        familyId = "fam",
        childId = id,
        deviceId = "deviceabc123",
        parentPinHash = "hash",
        displayName = name,
    )

    @Test
    fun upsertsMultipleProfilesAndSwitchesActive() = runBlocking {
        val store = store()
        store.set(cred("c1", "Ada"))
        store.set(cred("c2", "Leo"))
        assertEquals("c2", store.get()?.childId)
        assertEquals(2, store.listProfiles().size)
        assertTrue(store.setActive("c1"))
        assertEquals("c1", store.get()?.childId)
        assertEquals("Ada", store.get()?.displayName)
    }

    @Test
    fun enforcesMaxChildrenCap() = runBlocking {
        val store = store()
        repeat(AppConfig.MAX_CHILDREN_PER_PARENT) { index ->
            store.set(cred("c$index", "Child $index"))
        }
        try {
            store.set(cred("extra", "Overflow"))
            fail("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
        assertEquals(AppConfig.MAX_CHILDREN_PER_PARENT, store.listProfiles().size)
    }

    @Test
    fun updateParentPinHashPropagatesToAll() = runBlocking {
        val store = store()
        store.set(cred("c1", "Ada"))
        store.set(cred("c2", "Leo"))
        store.updateParentPinHash("new-hash")
        assertTrue(store.listProfiles().all { it.parentPinHash == "new-hash" })
    }

    @Test
    fun removeLastProfileClearsStore() = runBlocking {
        val store = store()
        store.set(cred("c1", "Ada"))
        assertTrue(store.removeProfile("c1"))
        assertNull(store.get())
        assertTrue(store.listProfiles().isEmpty())
    }
}
