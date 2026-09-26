package com.meritscreen.feature.parent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.CustomPromptSanitizer
import com.meritscreen.core.common.domain.LearningRegion
import com.meritscreen.core.common.error.AppErrorMapper
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.firebase.family.ParentControlStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Persists Timeline & AI Learning settings for a child created from the dashboard
 * Add Child flow (reuses onboarding screens; writes policy via [ParentControlStore]).
 */
@HiltViewModel
class ParentAddChildPolicyViewModel @Inject constructor(
    private val parentSessionRepository: ParentSessionRepository,
    private val parentControlStore: ParentControlStore,
) : ViewModel() {

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun saveTimeline(
        childId: String,
        ageBand: AgeBand,
        budgetMinutes: Int,
        quizFreqMinutes: Int,
        cooldownMinutes: Int,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            try {
                val session = parentSessionRepository.current()
                    ?: throw IllegalStateException("Parent session not found. Please log in.")
                val existing = parentControlStore.getPolicy(session.familyId, childId)
                val updated = existing.copy(
                    dailyCeilingMinutes = budgetMinutes.coerceIn(15, 360),
                    defaultBlockMinutes = quizFreqMinutes.coerceIn(
                        AppConfig.SESSION_CHUNK_MIN_MINUTES,
                        AppConfig.SESSION_CHUNK_MAX_MINUTES,
                    ),
                    defaultCooldownMinutes = cooldownMinutes.coerceIn(5, 60),
                    gradeStandard = gradeStandardFor(ageBand),
                    region = existing.region.takeIf { it != LearningRegion.OTHER }
                        ?: LearningRegion.US,
                    adaptiveDifficultyEnabled = true,
                    aiQuizzesEnabled = true,
                )
                parentControlStore.updatePolicy(session.familyId, childId, updated)
                onSuccess()
            } catch (error: Throwable) {
                _error.value = AppErrorMapper.from(error).userMessage
            } finally {
                _saving.value = false
            }
        }
    }

    fun saveAiPrompt(
        childId: String,
        ageBand: AgeBand,
        rawPrompt: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            try {
                val session = parentSessionRepository.current()
                    ?: throw IllegalStateException("Parent session not found. Please log in.")
                val sanitized = CustomPromptSanitizer.sanitize(rawPrompt)
                val existing = parentControlStore.getPolicy(session.familyId, childId)
                val updated = existing.copy(
                    customPromptGuidelines = sanitized,
                    gradeStandard = existing.gradeStandard.ifBlank { gradeStandardFor(ageBand) },
                    aiQuizzesEnabled = true,
                )
                parentControlStore.updatePolicy(session.familyId, childId, updated)
                onSuccess()
            } catch (error: Throwable) {
                _error.value = AppErrorMapper.from(error).userMessage
            } finally {
                _saving.value = false
            }
        }
    }

    companion object {
        fun parseAgeBand(raw: String): AgeBand =
            AgeBand.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: AgeBand.AGE_7_TO_9

        fun gradeLabelFor(ageBand: AgeBand): String = when (ageBand) {
            AgeBand.AGE_3_TO_6 -> "Kindergarten"
            AgeBand.AGE_7_TO_9 -> "3rd Grade"
            AgeBand.AGE_10_TO_12 -> "6th Grade"
        }

        fun gradeStandardFor(ageBand: AgeBand): String = when (ageBand) {
            AgeBand.AGE_3_TO_6 -> "Grade K"
            AgeBand.AGE_7_TO_9 -> "Grade 3"
            AgeBand.AGE_10_TO_12 -> "Grade 6"
        }
    }
}
