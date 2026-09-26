package com.meritscreen.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.CurriculumFocusCatalog
import com.meritscreen.core.common.error.AppErrorMapper
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.feature.onboarding.data.OnboardingDraftRepository
import com.meritscreen.feature.onboarding.domain.ChildDraft
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
import java.util.UUID
import javax.inject.Inject

data class AddChildUiModel(
    val children: List<ChildDraft>,
    val canAddMore: Boolean,
)

@HiltViewModel
class AddChildViewModel @Inject constructor(
    private val repository: OnboardingDraftRepository,
) : ViewModel() {

    val uiState: StateFlow<UiState<AddChildUiModel>> = repository.draft
        .map<OnboardingDraft, UiState<AddChildUiModel>> { draft ->
            UiState.Success(
                AddChildUiModel(
                    children = draft.children,
                    canAddMore = draft.children.size < AppConfig.MAX_CHILDREN_PER_PARENT,
                ),
            )
        }
        .catch { emit(UiState.Error(AppErrorMapper.from(it))) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _ageBand = MutableStateFlow(AgeBand.AGE_7_TO_9)
    val ageBand: StateFlow<AgeBand> = _ageBand.asStateFlow()

    private val _avatar = MutableStateFlow(AvatarPreset.Default)
    val avatar: StateFlow<AvatarPreset> = _avatar.asStateFlow()

    private val _curriculumFocusIds = MutableStateFlow(CurriculumFocusCatalog.defaultIds(AgeBand.AGE_7_TO_9))
    val curriculumFocusIds: StateFlow<List<String>> = _curriculumFocusIds.asStateFlow()

    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    private val _language = MutableStateFlow("English (US)")
    val language: StateFlow<String> = _language.asStateFlow()

    fun onNameChanged(value: String) {
        _name.value = value.take(MAX_NAME_LENGTH)
        _formError.value = null
    }

    fun onAgeBandSelected(value: AgeBand) {
        if (_ageBand.value == value) return
        _ageBand.value = value
        _curriculumFocusIds.value = CurriculumFocusCatalog.reconcile(value, _curriculumFocusIds.value)
    }

    fun onAvatarSelected(value: AvatarPreset) {
        _avatar.value = value
    }

    fun onLanguageSelected(value: String) {
        _language.value = value
    }

    fun onCurriculumFocusToggled(topicId: String) {
        _curriculumFocusIds.value = CurriculumFocusCatalog.toggle(
            ageBand = _ageBand.value,
            selectedIds = _curriculumFocusIds.value,
            topicId = topicId,
        )
    }

    fun addChild() {
        val trimmedName = _name.value.trim()
        if (trimmedName.isEmpty()) {
            _formError.value = "Enter your child's name."
            return
        }
        val draft = buildDraft(trimmedName)
        viewModelScope.launch {
            when (val result = repository.addChild(draft)) {
                is Outcome.Success -> resetForm()
                is Outcome.Failure -> _formError.value = result.error.userMessage
            }
        }
    }

    fun removeChild(localId: String) {
        viewModelScope.launch { repository.removeChild(localId) }
    }

    fun saveAndProceed(onContinue: (childName: String, ageBand: AgeBand, avatar: AvatarPreset) -> Unit) {
        val trimmedName = _name.value.trim()
        val currentChildren = (uiState.value as? UiState.Success)?.data?.children.orEmpty()
        if (trimmedName.isNotEmpty()) {
            val draft = buildDraft(trimmedName)
            viewModelScope.launch {
                when (val result = repository.addChild(draft)) {
                    is Outcome.Success -> {
                        resetForm()
                        onContinue(draft.name, draft.ageBand, draft.avatar)
                    }
                    is Outcome.Failure -> _formError.value = result.error.userMessage
                }
            }
        } else if (currentChildren.isNotEmpty()) {
            val primary = currentChildren.last()
            onContinue(primary.name, primary.ageBand, primary.avatar)
        } else {
            _formError.value = "Enter your child's name."
        }
    }

    private fun buildDraft(name: String): ChildDraft {
        val band = _ageBand.value
        return ChildDraft(
            localId = UUID.randomUUID().toString(),
            name = name,
            ageBand = band,
            avatar = _avatar.value,
            language = languageTagFor(_language.value),
            curriculumFocusIds = CurriculumFocusCatalog.reconcile(band, _curriculumFocusIds.value),
        )
    }

    private fun resetForm() {
        _name.value = ""
        _ageBand.value = AgeBand.AGE_7_TO_9
        _avatar.value = AvatarPreset.Default
        _language.value = "English (US)"
        _curriculumFocusIds.value = CurriculumFocusCatalog.defaultIds(AgeBand.AGE_7_TO_9)
        _formError.value = null
    }

    private companion object {
        const val MAX_NAME_LENGTH = 40

        fun languageTagFor(displayLabel: String): String = when {
            displayLabel.startsWith("Español", ignoreCase = true) -> "es"
            displayLabel.startsWith("Français", ignoreCase = true) -> "fr"
            displayLabel.startsWith("Deutsch", ignoreCase = true) -> "de"
            displayLabel.startsWith("Português", ignoreCase = true) -> "pt"
            displayLabel.startsWith("Hindi", ignoreCase = true) -> "hi"
            else -> "en"
        }
    }
}
