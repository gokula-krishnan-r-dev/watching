package com.meritscreen.feature.authentication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.feature.authentication.domain.CompleteParentAuthUseCase
import com.meritscreen.feature.authentication.domain.CredentialsValidator
import com.meritscreen.feature.authentication.domain.ParentAuthResult
import com.meritscreen.feature.authentication.google.GoogleIdTokenClient
import com.meritscreen.feature.authentication.google.GoogleSignInCancelled
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

data class SignInUiState(
    val email: String = "",
    val isValidEmail: Boolean = false,
    val rememberDevice: Boolean = true,
    val isSubmitting: Boolean = false,
    val isGoogleSubmitting: Boolean = false,
    val error: AppError? = null,
    val googleAvailable: Boolean = true,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val completeParentAuth: CompleteParentAuthUseCase,
    googleIdTokenClient: GoogleIdTokenClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SignInUiState(googleAvailable = true),
    )
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onEmailChanged(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                isValidEmail = CredentialsValidator.isValidEmail(value),
                error = null,
            )
        }
    }

    fun onRememberDeviceChanged(value: Boolean) {
        _uiState.update { it.copy(rememberDevice = value) }
    }

    fun sendOtp(onOtpSent: (email: String) -> Unit) {
        val email = _uiState.value.email.trim()
        val errorMsg = CredentialsValidator.emailError(email)
        if (errorMsg != null) {
            _uiState.update { it.copy(error = AppError.Validation(errorMsg)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (val outcome = completeParentAuth.sendEmailOtp(email)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    onOtpSent(email)
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
                }
            }
        }
    }

    fun submitGoogleToken(idToken: String, onResult: (ParentAuthResult) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGoogleSubmitting = true, error = null) }
            when (val outcome = completeParentAuth.signInWithGoogleIdToken(idToken)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(isGoogleSubmitting = false) }
                    onResult(outcome.value)
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isGoogleSubmitting = false, error = outcome.error) }
                }
            }
        }
    }

    fun resumeIfAlreadySignedIn(onResult: (ParentAuthResult) -> Unit) {
        viewModelScope.launch {
            when (val outcome = completeParentAuth.resumeIfAlreadySignedIn()) {
                null -> Unit
                is Outcome.Success -> {
                    _uiState.update { it.copy(isSubmitting = true, error = null) }
                    onResult(outcome.value)
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
                }
            }
        }
    }

    fun onGoogleCancelled() {
        _uiState.update { it.copy(isGoogleSubmitting = false) }
    }

    fun showGoogleError(error: AppError) {
        _uiState.update { it.copy(isGoogleSubmitting = false, error = error) }
    }

    fun ignoreCancellation(error: Throwable): Boolean = error is GoogleSignInCancelled
}

data class OtpVerificationUiState(
    val email: String = "",
    val otp: String = "",
    val resendCountdownSeconds: Int = AppConfig.EMAIL_OTP_RESEND_COOLDOWN_SECONDS,
    val canResend: Boolean = false,
    val isSubmitting: Boolean = false,
    val isResending: Boolean = false,
    val error: AppError? = null,
    val isSuccess: Boolean = false,
)

@HiltViewModel
class OtpVerificationViewModel @Inject constructor(
    private val completeParentAuth: CompleteParentAuthUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OtpVerificationUiState())
    val uiState: StateFlow<OtpVerificationUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    fun initEmail(email: String) {
        if (_uiState.value.email != email) {
            _uiState.update {
                it.copy(
                    email = email,
                    otp = "",
                    error = null,
                    resendCountdownSeconds = AppConfig.EMAIL_OTP_RESEND_COOLDOWN_SECONDS,
                    canResend = false,
                )
            }
            startCountdown()
        }
    }

    fun onOtpChanged(otp: String, onComplete: ((ParentAuthResult) -> Unit)? = null) {
        if (_uiState.value.isSubmitting || _uiState.value.isSuccess) return
        val filtered = otp.filter { it.isDigit() }.take(AppConfig.EMAIL_OTP_LENGTH)
        _uiState.update { it.copy(otp = filtered, error = null) }
        if (filtered.length == AppConfig.EMAIL_OTP_LENGTH && onComplete != null) {
            submitOtp(onComplete)
        }
    }

    fun onDigitEntered(digit: Char, onComplete: ((ParentAuthResult) -> Unit)? = null) {
        if (_uiState.value.isSubmitting || _uiState.value.isSuccess) return
        if (!digit.isDigit()) return
        val currentOtp = _uiState.value.otp
        if (currentOtp.length >= AppConfig.EMAIL_OTP_LENGTH) return

        val newOtp = currentOtp + digit
        _uiState.update { it.copy(otp = newOtp, error = null) }

        if (newOtp.length == AppConfig.EMAIL_OTP_LENGTH && onComplete != null) {
            submitOtp(onComplete)
        }
    }

    fun onBackspace() {
        val currentOtp = _uiState.value.otp
        if (currentOtp.isNotEmpty()) {
            _uiState.update { it.copy(otp = currentOtp.dropLast(1), error = null) }
        }
    }

    fun resendCode() {
        val state = _uiState.value
        if (!state.canResend || state.isResending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isResending = true, error = null) }
            when (val outcome = completeParentAuth.sendEmailOtp(state.email)) {
                is Outcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isResending = false,
                            otp = "",
                            resendCountdownSeconds = AppConfig.EMAIL_OTP_RESEND_COOLDOWN_SECONDS,
                            canResend = false,
                        )
                    }
                    startCountdown()
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isResending = false, error = outcome.error) }
                }
            }
        }
    }

    fun submitOtp(onResult: (ParentAuthResult) -> Unit) {
        val state = _uiState.value
        if (state.isSubmitting) return

        val validation = CredentialsValidator.otpError(state.otp)
        if (validation != null) {
            _uiState.update { it.copy(error = AppError.Validation(validation)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (val outcome = completeParentAuth.verifyEmailOtp(state.email, state.otp)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
                    onResult(outcome.value)
                }
                is Outcome.Failure -> {
                    _uiState.update { it.copy(isSubmitting = false, error = outcome.error) }
                }
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var secondsLeft = AppConfig.EMAIL_OTP_RESEND_COOLDOWN_SECONDS
            while (isActive && secondsLeft > 0) {
                delay(1000L)
                secondsLeft -= 1
                _uiState.update {
                    it.copy(
                        resendCountdownSeconds = secondsLeft,
                        canResend = secondsLeft <= 0,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
