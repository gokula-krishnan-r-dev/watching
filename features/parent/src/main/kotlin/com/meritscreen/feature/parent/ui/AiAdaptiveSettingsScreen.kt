package com.meritscreen.feature.parent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.LearningPromptQuickAdds
import com.meritscreen.core.common.domain.LearningRegion
import com.meritscreen.core.common.domain.QuizMode
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import kotlin.math.roundToInt

/**
 * P15 — AI Adaptive Settings (quiz timing, length, adaptive AI toggles, learning context,
 * and parent system-prompt guidelines). Persists via [PolicyEditorViewModel] → Firestore
 * policy/current → FCM policy_sync on the child.
 */
@Composable
fun QuizSettingsScreen(
    onBack: () -> Unit,
    viewModel: PolicyEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.policy.collectAsStateWithLifecycle()
    val ageBand by viewModel.ageBand.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }

    when (val current = state) {
        UiState.Loading -> LoadingState(message = "Loading adaptive settings")
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(message = current.error.userMessage, onRetry = viewModel::refresh)
        is UiState.Success -> AiAdaptiveSettingsContent(
            policy = current.data,
            ageBand = ageBand,
            saving = saving,
            error = error,
            onUpdatePolicy = viewModel::update,
            onSave = viewModel::save,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAdaptiveSettingsContent(
    modifier: Modifier = Modifier,
    policy: ChildPolicy,
    ageBand: AgeBand = AgeBand.AGE_7_TO_9,
    saving: Boolean = false,
    error: String? = null,
    childName: String = "your child",
    onUpdatePolicy: ((ChildPolicy) -> ChildPolicy) -> Unit = {},
    onSave: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val promptMax = AppConfig.CUSTOM_PROMPT_GUIDELINES_MAX_CHARS
    val promptLen = policy.customPromptGuidelines.length
    val gradePresets = remember(policy.region) { GradeStandardPresets.forRegion(policy.region) }
    val promptPresets = remember(ageBand, policy.customPromptGuidelines) {
        LearningPromptQuickAdds.forAgeBand(ageBand, policy.customPromptGuidelines)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MeritColors.SurfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MeritSpacing.lg, vertical = MeritSpacing.xs),
        ) {
            AdaptiveSettingsHeader(onBack = onBack)

            Spacer(Modifier.height(MeritSpacing.md))

            SettingsIntroCard()

            Spacer(Modifier.height(MeritSpacing.md))

            SettingsSectionCard(
                icon = Icons.Default.Tune,
                title = "When to quiz",
                subtitle = "Choose when Watching asks for a short unlock check",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuizMode.entries.forEach { mode ->
                        QuizModeOption(
                            mode = mode,
                            selected = policy.quizMode == mode,
                            onClick = { onUpdatePolicy { it.copy(quizMode = mode) } },
                        )
                    }
                }
                Spacer(Modifier.height(MeritSpacing.sm))
                Text(
                    text = policy.quizMode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            Spacer(Modifier.height(MeritSpacing.md))

            SettingsSectionCard(
                icon = Icons.Default.School,
                title = "Length & pass score",
                subtitle = "Keep checks short and fair",
            ) {
                AdaptiveSliderRow(
                    label = "${policy.questionsPerQuiz} questions",
                    value = policy.questionsPerQuiz,
                    range = 3f..5f,
                    steps = 1,
                    onChange = { n -> onUpdatePolicy { it.copy(questionsPerQuiz = n) } },
                )
                Spacer(Modifier.height(MeritSpacing.sm))
                AdaptiveSliderRow(
                    label = "Pass at ${policy.passScorePercent}%",
                    value = policy.passScorePercent,
                    range = 50f..100f,
                    steps = 9,
                    onChange = { n -> onUpdatePolicy { it.copy(passScorePercent = n) } },
                )
            }

            Spacer(Modifier.height(MeritSpacing.md))

            SettingsSectionCard(
                icon = Icons.Default.Psychology,
                title = "Adaptive AI",
                subtitle = "How quizzes personalize on-device",
            ) {
                AdaptiveSwitchRow(
                    title = "Adaptive difficulty",
                    description = "Raise or ease levels from recent answers",
                    checked = policy.adaptiveDifficultyEnabled,
                    onCheckedChange = { enabled ->
                        onUpdatePolicy { it.copy(adaptiveDifficultyEnabled = enabled) }
                    },
                )
                AdaptiveSwitchRow(
                    title = "Show explanations",
                    description = "Short teach-back after each answer",
                    checked = policy.showExplanations,
                    onCheckedChange = { enabled ->
                        onUpdatePolicy { it.copy(showExplanations = enabled) }
                    },
                )
                AdaptiveSwitchRow(
                    title = "AI quiz packs",
                    description = "Background packs when the device is idle",
                    checked = policy.aiQuizzesEnabled,
                    onCheckedChange = { enabled ->
                        onUpdatePolicy { it.copy(aiQuizzesEnabled = enabled) }
                    },
                )
            }

            Spacer(Modifier.height(MeritSpacing.md))

            SettingsSectionCard(
                icon = Icons.Default.School,
                title = "Learning context",
                subtitle = "Curriculum framing for AI quiz packs",
            ) {
                Text(
                    text = "Region",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LearningRegion.entries.forEach { region ->
                        SelectableChip(
                            label = region.displayLabel,
                            selected = policy.region == region,
                            onClick = { onUpdatePolicy { it.copy(region = region) } },
                        )
                    }
                }
                Spacer(Modifier.height(MeritSpacing.md))
                Text(
                    text = "Grade / standard",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gradePresets.forEach { grade ->
                        SelectableChip(
                            label = grade,
                            selected = policy.gradeStandard.equals(grade, ignoreCase = true),
                            onClick = { onUpdatePolicy { it.copy(gradeStandard = grade) } },
                        )
                    }
                }
                Spacer(Modifier.height(MeritSpacing.sm))
                OutlinedTextField(
                    value = policy.gradeStandard,
                    onValueChange = { value ->
                        onUpdatePolicy { it.copy(gradeStandard = value.take(40)) }
                    },
                    label = { Text("Custom grade label") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = adaptiveFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(MeritSpacing.md))

            // System prompt / parent guidance
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerLowest,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(MeritSpacing.md)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MeritColors.PrimaryFixed),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Personalized Learning Guidance",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MeritColors.OnSurface,
                            )
                            Text(
                                text = "Guide AI focus toward specific skills, topics, and strengths",
                                style = MaterialTheme.typography.labelSmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                        Text(
                            text = "$promptLen / $promptMax",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (promptLen >= promptMax) MeritColors.Error else MeritColors.Secondary,
                        )
                    }

                    Spacer(Modifier.height(MeritSpacing.sm))

                    OutlinedTextField(
                        value = policy.customPromptGuidelines,
                        onValueChange = { value ->
                            onUpdatePolicy {
                                it.copy(customPromptGuidelines = value.take(promptMax))
                            }
                        },
                        placeholder = {
                            Text(
                                text = "Enter learning goals, focus topics, or guidance for $childName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MeritColors.OutlineVariant,
                            )
                        },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(16.dp),
                        colors = adaptiveFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("Never include names, emails, or private details. Blocked instruction overrides are cleared on save.")
                        },
                    )

                    Spacer(Modifier.height(MeritSpacing.sm))

                    Text(
                        text = "Quick add",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        promptPresets.forEach { chip ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MeritColors.SurfaceContainerHigh,
                                modifier = Modifier.clickable {
                                    onUpdatePolicy { current ->
                                        val snippet = chip.snippet
                                        val next = if (current.customPromptGuidelines.isBlank()) {
                                            snippet
                                        } else {
                                            "${current.customPromptGuidelines.trim()} $snippet"
                                        }
                                        current.copy(customPromptGuidelines = next.take(promptMax))
                                    }
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MeritColors.Primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = chip.label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MeritColors.OnSurface,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(MeritSpacing.sm))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeritColors.SurfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Sanitized locally before sync. Used only as topic weighting for quiz generation — never as system instructions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.md))

            AnimatedVisibility(visible = error != null) {
                if (error != null) {
                    Text(
                        text = error,
                        color = MeritColors.Error,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(bottom = MeritSpacing.sm),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MeritColors.Primary,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable(enabled = !saving, onClick = onSave),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            color = MeritColors.OnPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Saving…",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MeritColors.OnPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Save Adaptive Settings",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.xl))
        }
    }
}

@Composable
private fun AdaptiveSettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MeritColors.OnSurface,
                )
            }
            Column {
                Text(
                    text = "AI Adaptive Settings",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = "Quizzes, difficulty & MeritAI guidance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MeritColors.PrimaryFixed)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MeritColors.Primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}


@Composable
private fun SettingsIntroCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(MeritSpacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MeritColors.PrimaryFixed),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Personalized unlock checks",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Adaptive settings tailor on-device quizzes to your child's learning journey. Your guidance helps prioritize subjects and topics, while age-appropriate safety limits always remain active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(MeritSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MeritColors.SecondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MeritColors.OnSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun QuizModeOption(
    mode: QuizMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MeritColors.SecondaryContainer else MeritColors.SurfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = mode.displayLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (selected) MeritColors.OnSecondaryContainer else MeritColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AdaptiveSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = MeritSpacing.sm)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MeritColors.OnSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MeritColors.OnSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MeritColors.OnPrimary,
                checkedTrackColor = MeritColors.Primary,
                uncheckedThumbColor = MeritColors.Outline,
                uncheckedTrackColor = MeritColors.SurfaceContainerHighest,
            ),
        )
    }
}

@Composable
private fun AdaptiveSliderRow(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Int) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = MeritColors.OnSurface,
    )
    Slider(
        value = value.toFloat().coerceIn(range.start, range.endInclusive),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = MeritColors.Primary,
            activeTrackColor = MeritColors.Primary,
            inactiveTrackColor = MeritColors.SecondaryContainer,
        ),
    )
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MeritColors.SecondaryContainer else MeritColors.SurfaceContainerHigh,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) MeritColors.OnSecondaryContainer else MeritColors.OnSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun adaptiveFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MeritColors.SurfaceContainerLow,
    unfocusedContainerColor = MeritColors.SurfaceContainerLow,
    focusedBorderColor = MeritColors.Primary,
    unfocusedBorderColor = Color.Transparent,
)

/** Region-aware grade chips from docs/08 — free text still allowed. */
internal object GradeStandardPresets {
    fun forRegion(region: LearningRegion): List<String> = when (region) {
        LearningRegion.IN -> listOf(
            "Nursery", "LKG", "UKG",
            "Class 1", "Class 2", "Class 3", "Class 4", "Class 5",
            "Class 6", "Class 7", "Class 8",
        )
        LearningRegion.US -> listOf(
            "Pre-K", "Kindergarten",
            "Grade 1", "Grade 2", "Grade 3", "Grade 4",
            "Grade 5", "Grade 6", "Grade 7",
        )
        LearningRegion.UK -> listOf(
            "Reception",
            "Year 1", "Year 2", "Year 3", "Year 4",
            "Year 5", "Year 6", "Year 7", "Year 8",
        )
        LearningRegion.AU, LearningRegion.CA, LearningRegion.OTHER -> listOf(
            "Kindergarten",
            "Grade 1", "Grade 2", "Grade 3", "Grade 4",
            "Grade 5", "Grade 6", "Grade 7",
        )
    }
}
