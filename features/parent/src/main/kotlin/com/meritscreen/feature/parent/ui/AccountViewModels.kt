package com.meritscreen.feature.parent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.auth.DisplayNameFromEmail
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.CurriculumFocusCatalog
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorMapper
import com.meritscreen.core.common.session.ParentSession
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.firebase.auth.AuthClient
import com.meritscreen.core.firebase.family.FamilyChildProfile
import com.meritscreen.core.firebase.family.FamilyDraftChild
import com.meritscreen.core.firebase.family.FamilyStore
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.core.security.pin.PinHasher
import com.meritscreen.feature.onboarding.domain.ChildDraft
import com.meritscreen.feature.parent.data.ParentNotificationPrefs
import com.meritscreen.feature.parent.data.ParentNotificationPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AccountUi(
    val email: String,
    val displayName: String,
    val familyName: String,
    val childCount: Int,
    val children: List<FamilyChildProfile> = emptyList(),
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authClient: AuthClient,
    private val parentSessionRepository: ParentSessionRepository,
    private val parentControlStore: ParentControlStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<AccountUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<AccountUi>> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val session = parentSessionRepository.current()
                    ?: run {
                        _uiState.value = UiState.Error(AppError.Auth())
                        return@launch
                    }
                val meta = parentControlStore.getFamilyMeta(session.familyId)
                val children = parentControlStore.listChildren(session.familyId)
                _uiState.value = UiState.Success(
                    AccountUi(
                        email = authClient.currentUser?.email.orEmpty(),
                        displayName = DisplayNameFromEmail.fromAuth(
                            displayName = authClient.currentUser?.displayName,
                            email = authClient.currentUser?.email,
                        ),
                        familyName = meta?.name.orEmpty().ifBlank { "My Family" },
                        childCount = children.size,
                        children = children,
                    ),
                )
            } catch (error: Throwable) {
                _uiState.value = UiState.Error(AppErrorMapper.from(error))
            }
        }
    }
}

data class AddChildAccountUi(
    val pendingChildren: List<ChildDraft>,
    val existingChildCount: Int,
    val canAddMore: Boolean,
)

/**
 * Existing-family "Add child" — reuses the onboarding [AddChildContent] UI and commits
 * directly to Firestore via [ParentControlStore] (not the onboarding draft DataStore).
 */
@HiltViewModel
class AddChildAccountViewModel @Inject constructor(
    private val parentSessionRepository: ParentSessionRepository,
    private val parentControlStore: ParentControlStore,
) : ViewModel() {

    private val _pending = MutableStateFlow<List<ChildDraft>>(emptyList())
    private val _existingCount = MutableStateFlow(0)

    private val _uiModel = MutableStateFlow(
        AddChildAccountUi(pendingChildren = emptyList(), existingChildCount = 0, canAddMore = true),
    )
    val uiModel: StateFlow<AddChildAccountUi> = _uiModel.asStateFlow()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()
    private val _ageBand = MutableStateFlow(AgeBand.AGE_7_TO_9)
    val ageBand: StateFlow<AgeBand> = _ageBand.asStateFlow()
    private val _avatar = MutableStateFlow(AvatarPreset.Default)
    val avatar: StateFlow<AvatarPreset> = _avatar.asStateFlow()
    private val _language = MutableStateFlow("English (US)")
    val language: StateFlow<String> = _language.asStateFlow()
    private val _curriculumFocusIds =
        MutableStateFlow(CurriculumFocusCatalog.defaultIds(AgeBand.AGE_7_TO_9))
    val curriculumFocusIds: StateFlow<List<String>> = _curriculumFocusIds.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _createdChildId = MutableStateFlow<String?>(null)
    val createdChildId: StateFlow<String?> = _createdChildId.asStateFlow()
    private val _createdChildName = MutableStateFlow<String?>(null)
    val createdChildName: StateFlow<String?> = _createdChildName.asStateFlow()
    private val _createdAgeBand = MutableStateFlow<AgeBand?>(null)
    val createdAgeBand: StateFlow<AgeBand?> = _createdAgeBand.asStateFlow()
    private val _createdAvatar = MutableStateFlow<AvatarPreset?>(null)
    val createdAvatar: StateFlow<AvatarPreset?> = _createdAvatar.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    init {
        viewModelScope.launch {
            val session = parentSessionRepository.current() ?: return@launch
            val count = runCatching {
                parentControlStore.listChildren(session.familyId).size
            }.getOrDefault(session.childIds.size)
            _existingCount.value = count
            publishUi()
        }
    }

    fun onNameChanged(value: String) {
        _name.value = value.take(40)
        _error.value = null
    }

    fun onAgeBand(value: AgeBand) {
        if (_ageBand.value == value) return
        _ageBand.value = value
        _curriculumFocusIds.value = CurriculumFocusCatalog.reconcile(value, _curriculumFocusIds.value)
    }

    fun onAvatar(value: AvatarPreset) {
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

    fun addChildToList() {
        val trimmed = _name.value.trim()
        if (trimmed.isEmpty()) {
            _error.value = "Enter your child's name."
            return
        }
        if (_existingCount.value + _pending.value.size >= AppConfig.MAX_CHILDREN_PER_PARENT) {
            _error.value = "You can add up to ${AppConfig.MAX_CHILDREN_PER_PARENT} children for now."
            return
        }
        _pending.value = _pending.value + buildDraft(trimmed)
        resetForm()
        publishUi()
    }

    fun removeChild(localId: String) {
        _pending.value = _pending.value.filterNot { it.localId == localId }
        publishUi()
    }

    fun saveAndContinue() {
        val trimmed = _name.value.trim()
        val toCreate = buildList {
            addAll(_pending.value)
            if (trimmed.isNotEmpty()) {
                add(buildDraft(trimmed))
            }
        }
        if (toCreate.isEmpty()) {
            _error.value = "Enter your child's name."
            return
        }
        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            try {
                val session = parentSessionRepository.current() ?: return@launch
                var lastId: String? = null
                val createdIds = mutableListOf<String>()
                for (draft in toCreate) {
                    if (_existingCount.value + createdIds.size >= AppConfig.MAX_CHILDREN_PER_PARENT) {
                        break
                    }
                    val created = parentControlStore.addChild(
                        session.familyId,
                        FamilyDraftChild(
                            localId = draft.localId,
                            name = draft.name,
                            ageBand = draft.ageBand,
                            avatar = draft.avatar,
                            language = draft.language.ifBlank { "en" },
                            curriculumFocusIds = draft.curriculumFocusIds,
                        ),
                    )
                    createdIds += created.childId
                    lastId = created.childId
                }
                parentSessionRepository.set(
                    ParentSession(
                        uid = session.uid,
                        familyId = session.familyId,
                        childIds = (session.childIds + createdIds).distinct(),
                    ),
                )
                _pending.value = emptyList()
                resetForm()
                publishUi()
                _createdChildName.value = toCreate.lastOrNull()?.name
                _createdAgeBand.value = toCreate.lastOrNull()?.ageBand
                _createdAvatar.value = toCreate.lastOrNull()?.avatar
                _createdChildId.value = lastId
            } catch (error: Throwable) {
                _error.value = AppErrorMapper.from(error).userMessage
            } finally {
                _saving.value = false
            }
        }
    }

    /** @deprecated Use [saveAndContinue]; kept for older call sites/tests. */
    fun save() = saveAndContinue()

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
        _error.value = null
    }

    private fun publishUi() {
        val existing = _existingCount.value
        val pending = _pending.value
        _uiModel.value = AddChildAccountUi(
            pendingChildren = pending,
            existingChildCount = existing,
            canAddMore = existing + pending.size < AppConfig.MAX_CHILDREN_PER_PARENT,
        )
    }

    private companion object {
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

@HiltViewModel
class ResetPinViewModel @Inject constructor(
    private val parentSessionRepository: ParentSessionRepository,
    private val familyStore: FamilyStore,
    private val pinHasher: PinHasher,
) : ViewModel() {
    private val _pin = MutableStateFlow("")
    val pin: StateFlow<String> = _pin.asStateFlow()
    private val _confirm = MutableStateFlow("")
    val confirm: StateFlow<String> = _confirm.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun onPin(value: String) {
        _pin.value = value
        _error.value = null
    }

    fun onConfirm(value: String) {
        _confirm.value = value
        _error.value = null
    }

    fun save() {
        val pinValue = _pin.value
        if (pinValue.length !in AppConfig.PARENT_PIN_MIN_LENGTH..AppConfig.PARENT_PIN_MAX_LENGTH) {
            _error.value = "PIN must be ${AppConfig.PARENT_PIN_MAX_LENGTH} digits."
            return
        }
        if (pinValue != _confirm.value) {
            _error.value = "PINs don't match."
            return
        }
        viewModelScope.launch {
            _saving.value = true
            try {
                val familyId = parentSessionRepository.current()?.familyId ?: return@launch
                familyStore.updateParentPinHash(familyId, pinHasher.hash(pinValue))
                _pin.value = ""
                _confirm.value = ""
                _saved.value = true
            } catch (error: Throwable) {
                _error.value = AppErrorMapper.from(error).userMessage
            } finally {
                _saving.value = false
            }
        }
    }
}

@HiltViewModel
class DeleteFamilyViewModel @Inject constructor(
    private val parentSessionRepository: ParentSessionRepository,
    private val parentControlStore: ParentControlStore,
) : ViewModel() {
    private val _confirmText = MutableStateFlow("")
    val confirmText: StateFlow<String> = _confirmText.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun onConfirmText(value: String) {
        _confirmText.value = value
        _error.value = null
    }

    fun delete() {
        if (_confirmText.value.trim().uppercase() != "DELETE") {
            _error.value = "Type DELETE to confirm."
            return
        }
        viewModelScope.launch {
            _saving.value = true
            try {
                val familyId = parentSessionRepository.current()?.familyId ?: return@launch
                parentControlStore.deleteFamily(familyId)
                parentSessionRepository.clear()
                _deleted.value = true
            } catch (error: Throwable) {
                _error.value = AppErrorMapper.from(error).userMessage
            } finally {
                _saving.value = false
            }
        }
    }
}

@HiltViewModel
class NotificationsSettingsViewModel @Inject constructor(
    private val repository: ParentNotificationPrefsRepository,
) : ViewModel() {
    val prefs: StateFlow<ParentNotificationPrefs> = repository.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ParentNotificationPrefs())

    fun setDaily(enabled: Boolean) = viewModelScope.launch { repository.setDailySummary(enabled) }
    fun setTimeUp(enabled: Boolean) = viewModelScope.launch { repository.setTimeUp(enabled) }
    fun setQuizFailed(enabled: Boolean) = viewModelScope.launch { repository.setQuizFailedRepeatedly(enabled) }
}
