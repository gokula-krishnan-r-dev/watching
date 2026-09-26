package com.meritscreen.feature.child.domain

import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.core.security.pin.PersistedPinGate
import com.meritscreen.core.security.pin.PinGateStore
import com.meritscreen.core.security.pin.PinHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PinGateState(
    val failedAttempts: Int = 0,
    val lockedUntilEpochMs: Long? = null,
)

@Singleton
class VerifyParentPinUseCase @Inject constructor(
    private val pairingStore: ChildPairingStore,
    private val pinHasher: PinHasher,
    private val pinGateStore: PinGateStore,
) {
    private val _state = MutableStateFlow(PinGateState())
    val state: StateFlow<PinGateState> = _state.asStateFlow()

    suspend operator fun invoke(pin: String): Outcome<Unit> {
        hydrateFromDisk()
        val now = System.currentTimeMillis()
        val lockedUntil = _state.value.lockedUntilEpochMs
        if (lockedUntil != null && now < lockedUntil) {
            val mins = ((lockedUntil - now) / 60_000L).coerceAtLeast(1)
            return Outcome.Failure(
                AppError.Validation("Too many tries. Wait about $mins min and try again."),
            )
        }
        if (lockedUntil != null && now >= lockedUntil) {
            persist(PinGateState())
        }
        val digits = pin.filter(Char::isDigit)
        if (digits.length !in AppConfig.PARENT_PIN_MIN_LENGTH..AppConfig.PARENT_PIN_MAX_LENGTH) {
            return Outcome.Failure(
                AppError.Validation(
                    "Enter your ${AppConfig.PARENT_PIN_MAX_LENGTH}-digit Parent PIN.",
                ),
            )
        }
        val credential = pairingStore.get()
            ?: return Outcome.Failure(AppError.Auth("This device is not paired."))
        if (pinHasher.verify(digits, credential.parentPinHash)) {
            persist(PinGateState())
            return Outcome.Success(Unit)
        }
        val attempts = _state.value.failedAttempts + 1
        val next = if (attempts >= AppConfig.PARENT_PIN_MAX_ATTEMPTS) {
            PinGateState(
                failedAttempts = attempts,
                lockedUntilEpochMs = now + AppConfig.PARENT_PIN_LOCKOUT_MINUTES * 60_000L,
            )
        } else {
            PinGateState(failedAttempts = attempts)
        }
        persist(next)
        val left = (AppConfig.PARENT_PIN_MAX_ATTEMPTS - attempts).coerceAtLeast(0)
        return Outcome.Failure(
            AppError.Validation(
                if (left == 0) {
                    "Too many tries. Wait ${AppConfig.PARENT_PIN_LOCKOUT_MINUTES} minutes."
                } else {
                    "That PIN didn’t work. $left tries left."
                },
            ),
        )
    }

    private suspend fun hydrateFromDisk() {
        val disk = pinGateStore.get()
        _state.value = PinGateState(
            failedAttempts = disk.failedAttempts,
            lockedUntilEpochMs = disk.lockedUntilEpochMs,
        )
    }

    private suspend fun persist(state: PinGateState) {
        _state.value = state
        pinGateStore.set(
            PersistedPinGate(
                failedAttempts = state.failedAttempts,
                lockedUntilEpochMs = state.lockedUntilEpochMs,
            ),
        )
    }
}
