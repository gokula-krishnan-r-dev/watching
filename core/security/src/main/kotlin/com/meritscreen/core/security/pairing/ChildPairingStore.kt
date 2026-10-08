package com.meritscreen.core.security.pairing

import kotlinx.serialization.Serializable

/**
 * Local pairing credential kept in Keystore-backed storage on the child device.
 * The custom token itself is held by Firebase Auth; this record is what survives
 * offline PIN checks and identifies a bound child profile.
 *
 * One physical device may hold up to [com.meritscreen.core.common.config.AppConfig.MAX_CHILDREN_PER_PARENT]
 * profiles; [ChildPairingStore.get] always returns the **active** profile.
 */
@Serializable
data class ChildPairingCredential(
    val familyId: String,
    val childId: String,
    val deviceId: String,
    val parentPinHash: String,
    val displayName: String = "",
    val avatarId: String = "",
)

@Serializable
data class ChildPairingRegistry(
    val activeChildId: String,
    val profiles: List<ChildPairingCredential>,
)

interface ChildPairingStore {
    /** Active child profile, or null when the device is unpaired. */
    suspend fun get(): ChildPairingCredential?

    /**
     * Upserts [credential] into the on-device registry and makes it active.
     * Enforces the family child cap; throws [IllegalStateException] if adding a
     * *new* childId would exceed the cap.
     */
    suspend fun set(credential: ChildPairingCredential)

    suspend fun getOrCreateDeviceId(): String

    /** Removes every profile (device stays identifiable via [getOrCreateDeviceId]). */
    suspend fun clear()

    /** All profiles paired on this handset (active first). */
    suspend fun listProfiles(): List<ChildPairingCredential> = listOfNotNull(get())

    /** Switch active profile without reminting Auth (caller handles token). */
    suspend fun setActive(childId: String): Boolean {
        val match = listProfiles().firstOrNull { it.childId == childId } ?: return false
        set(match)
        return true
    }

    /** Drop one profile; if it was active, activates another or clears. */
    suspend fun removeProfile(childId: String): Boolean {
        val remaining = listProfiles().filterNot { it.childId == childId }
        if (remaining.size == listProfiles().size) return false
        clear()
        remaining.forEach { set(it) }
        return true
    }

    /** Keep PIN hash in sync across every local profile (family-wide PIN). */
    suspend fun updateParentPinHash(hash: String) {
        val profiles = listProfiles()
        if (profiles.isEmpty()) return
        clear()
        profiles.forEach { set(it.copy(parentPinHash = hash)) }
    }
}
