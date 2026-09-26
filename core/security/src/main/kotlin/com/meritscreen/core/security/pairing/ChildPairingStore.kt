package com.meritscreen.core.security.pairing

import kotlinx.serialization.Serializable

/**
 * Local pairing credential kept in Keystore-backed storage on the child device.
 * The custom token itself is held by Firebase Auth; this record is what survives
 * offline PIN checks and identifies the bound child.
 */
@Serializable
data class ChildPairingCredential(
    val familyId: String,
    val childId: String,
    val deviceId: String,
    val parentPinHash: String,
    val displayName: String = "",
)

interface ChildPairingStore {
    suspend fun get(): ChildPairingCredential?
    suspend fun set(credential: ChildPairingCredential)
    suspend fun getOrCreateDeviceId(): String
    suspend fun clear()
}
