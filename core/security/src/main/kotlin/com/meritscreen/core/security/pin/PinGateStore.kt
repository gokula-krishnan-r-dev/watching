package com.meritscreen.core.security.pin

import com.meritscreen.core.security.storage.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

data class PersistedPinGate(
    val failedAttempts: Int = 0,
    val lockedUntilEpochMs: Long? = null,
)

/**
 * Keystore-backed PIN lockout so process death cannot reset the attempt counter
 * (Phase 9 — matches AppConfig.PARENT_PIN_MAX_ATTEMPTS / LOCKOUT_MINUTES).
 */
@Singleton
class PinGateStore @Inject constructor(
    private val secureStorage: SecureStorage,
) {
    suspend fun get(): PersistedPinGate {
        val attempts = secureStorage.get(ATTEMPTS_KEY)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val lockedUntil = secureStorage.get(LOCKED_UNTIL_KEY)?.toLongOrNull()
        return PersistedPinGate(failedAttempts = attempts, lockedUntilEpochMs = lockedUntil)
    }

    suspend fun set(state: PersistedPinGate) {
        secureStorage.put(ATTEMPTS_KEY, state.failedAttempts.toString())
        val locked = state.lockedUntilEpochMs
        if (locked == null) {
            secureStorage.remove(LOCKED_UNTIL_KEY)
        } else {
            secureStorage.put(LOCKED_UNTIL_KEY, locked.toString())
        }
    }

    suspend fun clear() {
        secureStorage.remove(ATTEMPTS_KEY)
        secureStorage.remove(LOCKED_UNTIL_KEY)
    }

    private companion object {
        const val ATTEMPTS_KEY = "parent_pin_failed_attempts"
        const val LOCKED_UNTIL_KEY = "parent_pin_locked_until_ms"
    }
}
