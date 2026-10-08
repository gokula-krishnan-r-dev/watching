package com.meritscreen.core.security.pairing

import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.security.storage.SecureStorage
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureChildPairingStore @Inject constructor(
    private val secureStorage: SecureStorage,
) : ChildPairingStore {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun get(): ChildPairingCredential? {
        val registry = readRegistry() ?: return null
        return registry.profiles.firstOrNull { it.childId == registry.activeChildId }
            ?: registry.profiles.firstOrNull()
    }

    override suspend fun set(credential: ChildPairingCredential) {
        val current = readRegistry()
        val others = current?.profiles.orEmpty()
            .filterNot { it.childId.equals(credential.childId, ignoreCase = true) }
        val isNew = current?.profiles.orEmpty()
            .none { it.childId.equals(credential.childId, ignoreCase = true) }
        if (isNew && others.size >= AppConfig.MAX_CHILDREN_PER_PARENT) {
            throw IllegalStateException(
                "This device already has ${AppConfig.MAX_CHILDREN_PER_PARENT} child profiles. " +
                    "Remove one before pairing another.",
            )
        }
        // Keep a shared deviceId across profiles on this handset.
        val deviceId = credential.deviceId.ifBlank {
            current?.profiles?.firstOrNull()?.deviceId ?: getOrCreateDeviceId()
        }
        val normalized = credential.copy(deviceId = deviceId)
        val mergedPin = normalized.parentPinHash.ifBlank {
            others.firstOrNull()?.parentPinHash.orEmpty()
        }
        val withPin = normalized.copy(parentPinHash = mergedPin)
        // Propagate latest PIN hash to siblings (family-wide).
        val siblings = others.map { it.copy(parentPinHash = withPin.parentPinHash, deviceId = deviceId) }
        val profiles = (siblings + withPin).distinctBy { it.childId.lowercase() }
        writeRegistry(
            ChildPairingRegistry(
                activeChildId = withPin.childId,
                profiles = profiles,
            ),
        )
        secureStorage.put(DEVICE_ID_KEY, deviceId)
    }

    override suspend fun getOrCreateDeviceId(): String {
        secureStorage.get(DEVICE_ID_KEY)?.let { return it }
        readRegistry()?.profiles?.firstOrNull()?.deviceId?.let { existing ->
            secureStorage.put(DEVICE_ID_KEY, existing)
            return existing
        }
        val id = UUID.randomUUID().toString().replace("-", "")
        secureStorage.put(DEVICE_ID_KEY, id)
        return id
    }

    override suspend fun clear() {
        secureStorage.remove(REGISTRY_KEY)
        secureStorage.remove(CREDENTIAL_KEY)
        // Keep DEVICE_ID_KEY so a re-pair on the same phone reuses the Firebase uid.
    }

    override suspend fun listProfiles(): List<ChildPairingCredential> {
        val registry = readRegistry() ?: return emptyList()
        val active = registry.activeChildId
        return registry.profiles.sortedByDescending { it.childId == active }
    }

    override suspend fun setActive(childId: String): Boolean {
        val registry = readRegistry() ?: return false
        if (registry.profiles.none { it.childId == childId }) return false
        writeRegistry(registry.copy(activeChildId = childId))
        return true
    }

    override suspend fun removeProfile(childId: String): Boolean {
        val registry = readRegistry() ?: return false
        val remaining = registry.profiles.filterNot { it.childId == childId }
        if (remaining.size == registry.profiles.size) return false
        if (remaining.isEmpty()) {
            clear()
            return true
        }
        val nextActive = if (registry.activeChildId == childId) {
            remaining.first().childId
        } else {
            registry.activeChildId
        }
        writeRegistry(ChildPairingRegistry(activeChildId = nextActive, profiles = remaining))
        return true
    }

    override suspend fun updateParentPinHash(hash: String) {
        val registry = readRegistry() ?: return
        writeRegistry(
            registry.copy(
                profiles = registry.profiles.map { it.copy(parentPinHash = hash) },
            ),
        )
    }

    private suspend fun readRegistry(): ChildPairingRegistry? {
        secureStorage.get(REGISTRY_KEY)?.let { raw ->
            return try {
                json.decodeFromString(ChildPairingRegistry.serializer(), raw)
            } catch (_: Exception) {
                null
            }
        }
        // Migrate legacy single-credential blob → registry.
        val legacy = secureStorage.get(CREDENTIAL_KEY) ?: return null
        return try {
            val cred = json.decodeFromString(ChildPairingCredential.serializer(), legacy)
            val registry = ChildPairingRegistry(activeChildId = cred.childId, profiles = listOf(cred))
            writeRegistry(registry)
            secureStorage.remove(CREDENTIAL_KEY)
            registry
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun writeRegistry(registry: ChildPairingRegistry) {
        secureStorage.put(
            REGISTRY_KEY,
            json.encodeToString(ChildPairingRegistry.serializer(), registry),
        )
    }

    private companion object {
        const val REGISTRY_KEY = "child_pairing_registry"
        const val CREDENTIAL_KEY = "child_pairing_credential"
        const val DEVICE_ID_KEY = "child_device_id"
    }
}
