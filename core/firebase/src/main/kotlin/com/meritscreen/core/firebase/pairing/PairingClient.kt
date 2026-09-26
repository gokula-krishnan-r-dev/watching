package com.meritscreen.core.firebase.pairing

data class PairingOffer(
    val code: String,
    val secret: String,
    val expiresAtEpochMs: Long,
    val qrPayload: String,
    val childId: String,
    val familyId: String,
)

data class PairingResult(
    val customToken: String,
    val familyId: String,
    val childId: String,
    val deviceId: String,
    val parentPinHash: String,
    val displayName: String,
    val ageBand: String,
    val avatarId: String,
)

interface PairingClient {
    suspend fun createToken(childId: String): PairingOffer
    suspend fun consumeToken(code: String, secret: String?, deviceId: String): PairingResult
}
