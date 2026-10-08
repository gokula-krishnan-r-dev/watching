package com.meritscreen.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.CurriculumFocusCatalog
import com.meritscreen.core.common.domain.CustomPromptSanitizer
import com.meritscreen.core.firebase.family.FamilyStore
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.feature.onboarding.domain.OnboardingWizardState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OnboardingWizardViewModel @Inject constructor(
    private val familyStore: FamilyStore,
    private val parentControlStore: ParentControlStore,
    private val firebaseAuth: FirebaseAuth,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingWizardState())
    val uiState: StateFlow<OnboardingWizardState> = _uiState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun updateChildProfile(
        name: String,
        ageBand: AgeBand,
        grade: String,
        avatar: AvatarPreset,
        curriculumFocusIds: List<String>,
    ) {
        _uiState.update {
            it.copy(
                childName = name,
                ageBand = ageBand,
                grade = grade,
                avatar = avatar,
                curriculumFocusIds = CurriculumFocusCatalog.reconcile(ageBand, curriculumFocusIds),
            )
        }
    }

    fun updateTimelineSchedule(
        dailyCeilingMinutes: Int,
        quizFrequencyMinutes: Int,
        bedtimeEnabled: Boolean,
        bedtimeStart: String,
        bedtimeEnd: String,
        cooldownMinutes: Int,
    ) {
        _uiState.update {
            it.copy(
                dailyCeilingMinutes = dailyCeilingMinutes,
                quizFrequencyMinutes = quizFrequencyMinutes,
                bedtimeEnabled = bedtimeEnabled,
                bedtimeStart = bedtimeStart,
                bedtimeEnd = bedtimeEnd,
                defaultCooldownMinutes = cooldownMinutes,
            )
        }
    }

    fun simulatePairingSuccess(deviceModel: String = "Leo's Tablet (Galaxy Tab)") {
        _uiState.update {
            it.copy(
                isPaired = true,
                pairedDeviceModel = deviceModel,
            )
        }
    }

    fun updateAppRule(rule: AppRule) {
        _uiState.update { current ->
            val updated = current.appRules.map {
                if (it.appId == rule.appId) rule else it
            }
            current.copy(appRules = updated)
        }
    }

    fun toggleGlobalScreenTimeLimit(enabled: Boolean) {
        _uiState.update { it.copy(globalScreenTimeLimitEnabled = enabled) }
    }

    fun applyRulesPreset(presetName: String) {
        _uiState.update { current ->
            val updatedRules = when (presetName) {
                "Balanced" -> current.appRules.map { rule ->
                    rule.withBlockDuration(
                        allowed = true,
                        minutes = AppConfig.DEFAULT_BLOCK_MINUTES,
                    )
                }
                "Strict" -> current.appRules.map { rule ->
                    rule.withBlockDuration(
                        allowed = rule.appId == "duo_abc" || rule.appId == "khan_kids",
                        minutes = AppConfig.DEFAULT_BLOCK_MINUTES,
                    )
                }
                "Weekend" -> current.appRules.map { rule ->
                    rule.withBlockDuration(
                        allowed = true,
                        minutes = AppConfig.DEFAULT_BLOCK_MINUTES,
                    )
                }
                else -> current.appRules
            }
            current.copy(appRules = updatedRules)
        }
    }

    fun setParentPin(pin: String) {
        _uiState.update { it.copy(parentPin = pin) }
    }

    fun updateAiLearningContext(rawPrompt: String) {
        val sanitized = CustomPromptSanitizer.sanitize(rawPrompt)
        _uiState.update { it.copy(aiPromptContext = sanitized) }
    }

    fun commitOnboardingToFirebase(onComplete: (childId: String) -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            val currentState = _uiState.value
            val uid = firebaseAuth.currentUser?.uid ?: "onboarding_parent_${System.currentTimeMillis()}"
            val email = firebaseAuth.currentUser?.email ?: "parent@meritscreen.com"

            try {
                withContext(dispatchers.io) {
                    val draft = currentState.toFamilyDraft()
                    val createdFamily = familyStore.createFamilyFromDraft(
                        uid = uid,
                        email = email,
                        draft = draft,
                    )
                    val primaryChild = createdFamily.children.firstOrNull()
                    val childId = primaryChild?.childId ?: "child_primary"

                    // Commit policy and allowlist
                    val policy = currentState.toChildPolicy()
                    parentControlStore.updatePolicy(createdFamily.familyId, childId, policy)

                    currentState.appRules.forEach { rule ->
                        parentControlStore.upsertAppRule(createdFamily.familyId, childId, rule)
                    }

                    _isSaving.value = false
                    withContext(dispatchers.main) {
                        onComplete(childId)
                    }
                }
            } catch (_: Exception) {
                // Graceful fallback for offline / mock testing
                _isSaving.value = false
                withContext(dispatchers.main) {
                    onComplete("child_primary")
                }
            }
        }
    }
}

private fun AppRule.withBlockDuration(allowed: Boolean, minutes: Int): AppRule = copy(
    allowed = allowed,
    blockMinutes = minutes,
    grantOnPassMinutes = minutes.takeIf { it > 0 } ?: AppConfig.DEFAULT_BLOCK_MINUTES,
)
