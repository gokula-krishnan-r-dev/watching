package com.meritscreen.feature.onboarding.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.firebase.error.FirebaseErrorMapper
import com.meritscreen.core.firebase.family.FamilyStore
import com.meritscreen.core.firebase.pairing.PairingClient
import com.meritscreen.core.firebase.pairing.PairingOffer
import com.meritscreen.core.network.NetworkMonitor
import com.meritscreen.feature.onboarding.domain.EnsureFamilyReadyForPairingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HandshakePairPhase {
    Loading,
    Searching,
    Succeeded,
    Bypassed,
}

data class HandshakeUiState(
    val phase: HandshakePairPhase = HandshakePairPhase.Loading,
    val childName: String = "your child",
    val childId: String = "",
    val familyId: String = "",
    val offer: PairingOffer? = null,
    val secondsLeft: Int = 0,
    val error: AppError? = null,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class OnboardingDeviceHandshakeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ensureFamilyReady: EnsureFamilyReadyForPairingUseCase,
    private val pairingClient: PairingClient,
    private val familyStore: FamilyStore,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    /** Nav arg from onboarding or post-signup Add Child pairing step. */
    private val preferredChildId: String =
        savedStateHandle.get<String>("childId").orEmpty()

    private val _uiState = MutableStateFlow(HandshakeUiState())
    val uiState: StateFlow<HandshakeUiState> = _uiState.asStateFlow()

    private var watchJob: Job? = null
    private var countdownJob: Job? = null
    private var hasNavigated = false

    init {
        refreshCode()
    }

    fun refreshCode() {
        viewModelScope.launch { mint() }
    }

    private suspend fun mint() {
        if (_uiState.value.phase == HandshakePairPhase.Succeeded ||
            _uiState.value.phase == HandshakePairPhase.Bypassed
        ) {
            return
        }
        if (!networkMonitor.isCurrentlyOnline()) {
            _uiState.update {
                it.copy(phase = HandshakePairPhase.Searching, error = AppError.Network(), isRefreshing = false)
            }
            return
        }
        _uiState.update {
            it.copy(
                phase = if (it.offer == null) HandshakePairPhase.Loading else it.phase,
                isRefreshing = it.offer != null,
                error = null,
            )
        }
        try {
            val context = ensureFamilyReady(preferredChildId.takeIf { it.isNotBlank() })
            val offer = pairingClient.createToken(context.childId)
            _uiState.update {
                it.copy(
                    phase = HandshakePairPhase.Searching,
                    childName = context.childName,
                    childId = context.childId,
                    familyId = context.familyId,
                    offer = offer,
                    secondsLeft = secondsUntil(offer.expiresAtEpochMs),
                    error = null,
                    isRefreshing = false,
                )
            }
            startCountdown(offer.expiresAtEpochMs)
            watchForDevice(offer.familyId, offer.childId)
        } catch (error: AppErrorException) {
            _uiState.update {
                it.copy(
                    phase = HandshakePairPhase.Searching,
                    error = error.error,
                    isRefreshing = false,
                )
            }
        } catch (error: Throwable) {
            _uiState.update {
                it.copy(
                    phase = HandshakePairPhase.Searching,
                    error = FirebaseErrorMapper.from(error),
                    isRefreshing = false,
                )
            }
        }
    }

    private fun startCountdown(expiresAtEpochMs: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val left = secondsUntil(expiresAtEpochMs)
                _uiState.update { it.copy(secondsLeft = left) }
                if (left <= 0) {
                    _uiState.update {
                        it.copy(
                            error = AppError.Validation(
                                "This pairing code expired. Refresh to generate a new one.",
                            ),
                        )
                    }
                    return@launch
                }
                delay(1_000)
            }
        }
    }

    private fun watchForDevice(familyId: String, childId: String) {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            familyStore.observeDeviceCount(familyId, childId).collect { count ->
                if (count > 0) {
                    markPaired()
                }
            }
        }
    }

    /** Debug / emulator helper when a second device is not available. */
    fun simulateSuccessfulPair() {
        if (_uiState.value.offer == null) return
        markPaired()
    }

    fun markPaired() {
        if (hasNavigated) return
        val phase = _uiState.value.phase
        if (phase != HandshakePairPhase.Searching && phase != HandshakePairPhase.Loading) return
        countdownJob?.cancel()
        watchJob?.cancel()
        _uiState.update { it.copy(phase = HandshakePairPhase.Succeeded, error = null) }
    }

    fun skipPairing(onSkipped: () -> Unit) {
        if (hasNavigated) return
        if (_uiState.value.phase == HandshakePairPhase.Succeeded) return
        hasNavigated = true
        countdownJob?.cancel()
        watchJob?.cancel()
        _uiState.update { it.copy(phase = HandshakePairPhase.Bypassed) }
        onSkipped()
    }

    fun consumeSuccessNavigation(onPaired: () -> Unit) {
        if (hasNavigated) return
        if (_uiState.value.phase != HandshakePairPhase.Succeeded) return
        hasNavigated = true
        onPaired()
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        watchJob?.cancel()
    }

    private fun secondsUntil(expiresAtEpochMs: Long): Int {
        if (expiresAtEpochMs <= 0L) return 0
        return ((expiresAtEpochMs - System.currentTimeMillis()) / 1_000L)
            .toInt()
            .coerceAtLeast(0)
    }
}
