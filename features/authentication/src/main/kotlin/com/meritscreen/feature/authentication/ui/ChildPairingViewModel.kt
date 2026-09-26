package com.meritscreen.feature.authentication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.core.ui.components.PairingQrPayload
import com.meritscreen.feature.authentication.domain.PairChildDeviceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChildPairingUiState(
    val code: String = "",
    val secret: String? = null,
    val isSubmitting: Boolean = false,
    val isScanning: Boolean = false,
    val pairedSuccessfully: Boolean = false,
    val error: AppError? = null,
    val usageAccessGranted: Boolean = false,
    val confirmedProfile: Boolean = true,
)

@HiltViewModel
class ChildPairingViewModel @Inject constructor(
    private val pairChildDevice: PairChildDeviceUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChildPairingUiState())
    val uiState: StateFlow<ChildPairingUiState> = _uiState.asStateFlow()

    private var submitJob: Job? = null
    private var autoSubmitJob: Job? = null
    private var hasPaired = false

    fun onCodeChanged(value: String) {
        val digits = value.filter(Char::isDigit).take(AppConfig.PAIRING_CODE_LENGTH)
        _uiState.update {
            it.copy(
                code = digits,
                // Manual edits drop any QR secret so a mistyped digit cannot reuse a stale secret.
                secret = if (digits == it.code) it.secret else null,
                error = null,
            )
        }
        maybeAutoSubmit()
    }

    fun onQrScanned(parsed: PairingQrPayload.Parsed) {
        if (hasPaired || _uiState.value.isSubmitting || _uiState.value.pairedSuccessfully) return
        _uiState.update {
            it.copy(
                code = parsed.code,
                secret = parsed.secret,
                isScanning = false,
                error = null,
            )
        }
        submitInternal(fromQr = true)
    }

    fun startScanning() {
        if (hasPaired || _uiState.value.isSubmitting || _uiState.value.pairedSuccessfully) return
        _uiState.update { it.copy(isScanning = true, error = null) }
    }

    fun stopScanning() {
        _uiState.update { it.copy(isScanning = false) }
    }

    fun toggleUsageAccess() {
        _uiState.update { it.copy(usageAccessGranted = !it.usageAccessGranted) }
    }

    fun confirmProfile(confirmed: Boolean) {
        _uiState.update { it.copy(confirmedProfile = confirmed) }
    }

    fun submit() {
        submitInternal(fromQr = false)
    }

    fun consumeSuccessNavigation(onPaired: () -> Unit) {
        if (hasPaired) return
        if (!_uiState.value.pairedSuccessfully) return
        hasPaired = true
        onPaired()
    }

    private fun maybeAutoSubmit() {
        autoSubmitJob?.cancel()
        val state = _uiState.value
        if (state.code.length != AppConfig.PAIRING_CODE_LENGTH) return
        if (state.isSubmitting || state.pairedSuccessfully) return
        autoSubmitJob = viewModelScope.launch {
            delay(280)
            if (_uiState.value.code.length == AppConfig.PAIRING_CODE_LENGTH &&
                !_uiState.value.isSubmitting &&
                !_uiState.value.pairedSuccessfully
            ) {
                submitInternal(fromQr = false)
            }
        }
    }

    private fun submitInternal(fromQr: Boolean) {
        if (hasPaired || _uiState.value.pairedSuccessfully) return
        val snapshot = _uiState.value
        if (snapshot.code.length != AppConfig.PAIRING_CODE_LENGTH) {
            _uiState.update {
                it.copy(
                    error = AppError.Validation(
                        "Enter the ${AppConfig.PAIRING_CODE_LENGTH}-digit code from the parent phone.",
                    ),
                )
            }
            return
        }
        if (snapshot.isSubmitting) return

        submitJob?.cancel()
        submitJob = viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, isScanning = false) }
            when (val outcome = pairChildDevice(snapshot.code, snapshot.secret)) {
                is Outcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            pairedSuccessfully = true,
                            error = null,
                        )
                    }
                }
                is Outcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = outcome.error,
                            secret = if (fromQr) snapshot.secret else null,
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        submitJob?.cancel()
        autoSubmitJob?.cancel()
    }
}
