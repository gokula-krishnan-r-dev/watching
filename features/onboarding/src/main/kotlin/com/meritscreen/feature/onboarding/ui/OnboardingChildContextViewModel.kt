package com.meritscreen.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.feature.onboarding.data.OnboardingDraftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Shared child context for post–Add Child onboarding steps (Timeline, AI, Setup Complete).
 * Reads the latest child from the local onboarding draft — never a hardcoded preview name.
 */
@HiltViewModel
class OnboardingChildContextViewModel @Inject constructor(
    draftRepository: OnboardingDraftRepository,
) : ViewModel() {

    data class ChildContext(
        val name: String = "your child",
        val ageBand: AgeBand = AgeBand.AGE_7_TO_9,
        val avatar: AvatarPreset = AvatarPreset.Default,
        val gradeLabel: String = gradeLabelFor(AgeBand.AGE_7_TO_9),
    )

    val childContext: StateFlow<ChildContext> = draftRepository.draft
        .map { draft ->
            val child = draft.children.lastOrNull()
            val band = child?.ageBand ?: AgeBand.AGE_7_TO_9
            ChildContext(
                name = child?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "your child",
                ageBand = band,
                avatar = child?.avatar ?: AvatarPreset.Default,
                gradeLabel = gradeLabelFor(band),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ChildContext(),
        )

    companion object {
        fun gradeLabelFor(ageBand: AgeBand): String = when (ageBand) {
            AgeBand.AGE_3_TO_6 -> "Kindergarten"
            AgeBand.AGE_7_TO_9 -> "3rd Grade"
            AgeBand.AGE_10_TO_12 -> "6th Grade"
        }
    }
}
