package com.meritscreen.core.security.pairing

import com.meritscreen.core.security.storage.SecureStorage
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureChildPairingStore @Inject constructor(
    private val secureStorage: SecureStorage,
) : ChildPairingStore {

    override suspend fun get(): ChildPairingCredential? {
        val raw = secureStorage.get(CREDENTIAL_KEY) ?: return null
        return try {
            Json.decodeFromString(ChildPairingCredential.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun set(credential: ChildPairingCredential) {
        secureStorage.put(CREDENTIAL_KEY, Json.encodeToString(ChildPairingCredential.serializer(), credential))
        secureStorage.put(DEVICE_ID_KEY, credential.deviceId)
    }

    override suspend fun getOrCreateDeviceId(): String {
        secureStorage.get(DEVICE_ID_KEY)?.let { return it }
        val id = UUID.randomUUID().toString().replace("-", "")
        secureStorage.put(DEVICE_ID_KEY, id)
        return id
    }

    override suspend fun clear() {
        secureStorage.remove(CREDENTIAL_KEY)
        // Keep DEVICE_ID_KEY so a re-pair on the same phone reuses the Firebase uid.
    }

    private companion object {
        const val CREDENTIAL_KEY = "child_pairing_credential"
        const val DEVICE_ID_KEY = "child_device_id"
    }
}
