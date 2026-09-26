package com.meritscreen.core.firebase.auth

/**
 * App-facing Firebase Auth snapshot. Feature modules should never receive a raw
 * `FirebaseUser` — that keeps UI free of SDK types and of email/token leakage into logs.
 *
 * Child-device custom tokens carry [familyId]/[childId]/[deviceId] claims (see
 * `consumePairingToken`). Parents leave those null.
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean,
    val isChildDevice: Boolean,
    val familyId: String? = null,
    val childId: String? = null,
    val deviceId: String? = null,
)
