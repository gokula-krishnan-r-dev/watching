package com.meritscreen.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.error.AppErrorMapper
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.firebase.family.FamilyStore
import com.meritscreen.core.security.pin.PinHasher
import com.meritscreen.feature.onboarding.data.OnboardingDraftRepository
import com.meritscreen.feature.onboarding.domain.OnboardingDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether a draft is loaded yet — the actual PIN value is never round-tripped from disk. */
@HiltViewModel
class SetParentPinViewModel @Inject constructor(
    private val repository: OnboardingDraftRepository,
    private val pinHasher: PinHasher,
    private val parentSessionRepository: ParentSessionRepository,
    private val familyStore: FamilyStore,
) : ViewModel() {

    val uiState: StateFlow<UiState<Unit>> = repository.draft
        .map<OnboardingDraft, UiState<Unit>> { UiState.Success(Unit) }
        .catch { emit(UiState.Error(AppErrorMapper.from(it))) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    private val _pin = MutableStateFlow("")
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _confirmPin = MutableStateFlow("")
    val confirmPin: StateFlow<String> = _confirmPin.asStateFlow()

    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    private val _pinSaved = MutableStateFlow(false)
    val pinSaved: StateFlow<Boolean> = _pinSaved.asStateFlow()

    fun onPinChanged(value: String) {
        _pin.value = value
        _formError.value = null
    }

    fun onConfirmPinChanged(value: String) {
        _confirmPin.value = value
        _formError.value = null
    }

    fun savePin() {
        val pinValue = _pin.value
        if (pinValue.length !in AppConfig.PARENT_PIN_MIN_LENGTH..AppConfig.PARENT_PIN_MAX_LENGTH) {
            _formError.value = "PIN must be ${AppConfig.PARENT_PIN_MAX_LENGTH} digits."
            return
        }
        if (pinValue != _confirmPin.value) {
            _formError.value = "PINs don't match. Try again."
            return
        }
        viewModelScope.launch {
            val hash = pinHasher.hash(pinValue)
            repository.setParentPinHash(hash)
            // If family was bootstrapped earlier for pairing, keep Firestore PIN in sync.
            val familyId = parentSessionRepository.current()?.familyId
            if (!familyId.isNullOrBlank()) {
                runCatching { familyStore.updateParentPinHash(familyId, hash) }
            }
            _pin.value = ""
            _confirmPin.value = ""
            _pinSaved.value = true
        }
    }
}
