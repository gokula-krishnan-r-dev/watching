package com.meritscreen.feature.onboarding.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.CurriculumFocusCatalog
import com.meritscreen.core.common.domain.CurriculumFocusTopic
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import com.meritscreen.feature.onboarding.domain.ChildDraft
import kotlinx.serialization.Serializable

/** P06 — add one or more child profiles (name, age band, avatar preset). No camera required. */
@Serializable
data object AddChildRoute

@Composable
fun AddChildScreen(
    onContinue: (childName: String, ageBand: AgeBand, avatar: AvatarPreset) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddChildViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        UiState.Loading -> LoadingState()
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(message = current.error.userMessage)
        is UiState.Success -> {
            val name by viewModel.name.collectAsStateWithLifecycle()
            val ageBand by viewModel.ageBand.collectAsStateWithLifecycle()
            val avatar by viewModel.avatar.collectAsStateWithLifecycle()
            val language by viewModel.language.collectAsStateWithLifecycle()
            val curriculumFocusIds by viewModel.curriculumFocusIds.collectAsStateWithLifecycle()
            val formError by viewModel.formError.collectAsStateWithLifecycle()
            AddChildContent(
                modifier = modifier,
                children = current.data.children,
                canAddMore = current.data.canAddMore,
                name = name,
                ageBand = ageBand,
                avatar = avatar,
                language = language,
                curriculumFocusIds = curriculumFocusIds,
                formError = formError,
                onNameChanged = viewModel::onNameChanged,
                onAgeBandSelected = viewModel::onAgeBandSelected,
                onAvatarSelected = viewModel::onAvatarSelected,
                onLanguageSelected = viewModel::onLanguageSelected,
                onCurriculumFocusToggled = viewModel::onCurriculumFocusToggled,
                onAddChild = viewModel::addChild,
                onRemoveChild = viewModel::removeChild,
                onSaveAndContinue = { viewModel.saveAndProceed(onContinue) },
                onBack = onBack,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddChildContent(
    modifier: Modifier = Modifier,
    children: List<ChildDraft> = emptyList(),
    canAddMore: Boolean = true,
    name: String = "",
    ageBand: AgeBand = AgeBand.AGE_7_TO_9,
    avatar: AvatarPreset = AvatarPreset.Default,
    language: String = "English (US)",
    curriculumFocusIds: List<String> = CurriculumFocusCatalog.defaultIds(AgeBand.AGE_7_TO_9),
    formError: String? = null,
    isSubmitting: Boolean = false,
    stepLabel: String = "Step 2 of 4",
    progressFraction: Float = 0.5f,
    onNameChanged: (String) -> Unit = {},
    onAgeBandSelected: (AgeBand) -> Unit = {},
    onAvatarSelected: (AvatarPreset) -> Unit = {},
    onLanguageSelected: (String) -> Unit = {},
    onCurriculumFocusToggled: (String) -> Unit = {},
    onAddChild: () -> Unit = {},
    onRemoveChild: (String) -> Unit = {},
    onSaveAndContinue: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MeritColors.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = MeritSpacing.lg, vertical = MeritSpacing.xs),
        ) {
            // Top Navigation & Step Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MeritColors.OnSurface,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MeritColors.SecondaryContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MeritColors.Primary),
                        )
                        Text(
                            text = stepLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSecondaryContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.sm))

            // Progress track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MeritColors.SurfaceContainer),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.coerceIn(0.05f, 1f))
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MeritColors.Primary),
                )
            }

            Spacer(Modifier.height(MeritSpacing.lg))

            // Headline
            Text(
                text = "Add Child Profile",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Customize age-appropriate learning rules, screen limits, and quiz difficulty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MeritColors.OnSurfaceVariant,
            )

            Spacer(Modifier.height(MeritSpacing.lg))

            // 1. Choose Avatar Preset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Choose an Avatar",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
            }

            Spacer(Modifier.height(MeritSpacing.sm))

            // Avatar Horizontal Row
            val avatarOptions = listOf(
                AvatarPreset.FOX,
                AvatarPreset.RABBIT,
                AvatarPreset.OWL,
                AvatarPreset.BEAR,
                AvatarPreset.ASTRO,
                AvatarPreset.PANDA,
                AvatarPreset.LION,
                AvatarPreset.TURTLE,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MeritSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                avatarOptions.forEach { preset ->
                    val isSelected = preset == avatar
                    val scale by animateFloatAsState(targetValue = if (isSelected) 1.08f else 1f, label = "scale")
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MeritColors.Primary else MeritColors.SurfaceContainerHigh,
                        label = "avatarBg",
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .scale(scale)
                            .clickable { onAvatarSelected(preset) }
                            .padding(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(bgColor)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, MeritColors.PrimaryFixed, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = preset.emoji,
                                fontSize = 28.sp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = preset.label.ifBlank { preset.name.lowercase().replaceFirstChar { it.uppercase() } },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (isSelected) MeritColors.OnSurface else MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.lg))

            // 2. First Name Text Field
            Text(
                text = "Child's First Name",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurface,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                placeholder = { Text("Enter child name", color = MeritColors.OnSurfaceVariant.copy(alpha = 0.6f)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                    )
                },
                trailingIcon = {
                    if (name.isNotEmpty()) {
                        IconButton(onClick = { onNameChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear name",
                                tint = MeritColors.OnSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                isError = formError != null,
                supportingText = formError?.let { { Text(it, color = MeritColors.Error) } },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MeritColors.SurfaceContainerLowest,
                    unfocusedContainerColor = MeritColors.SurfaceContainerLowest,
                    focusedBorderColor = MeritColors.Primary,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(MeritSpacing.lg))

            // 3. Age Group & Adventure Stage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Age Group & Adventure Stage",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
            }
            Spacer(Modifier.height(MeritSpacing.sm))

            Column(verticalArrangement = Arrangement.spacedBy(MeritSpacing.sm)) {
                AgeBandCard(
                    title = "Ages 3–6",
                    subtitle = "Early Explorers • Big icons, sensory play",
                    icon = Icons.Default.Toys,
                    selected = ageBand == AgeBand.AGE_3_TO_6,
                    onClick = { onAgeBandSelected(AgeBand.AGE_3_TO_6) },
                )
                AgeBandCard(
                    title = "Ages 7–9",
                    subtitle = "Elementary Quest • Logic, reading quests",
                    icon = Icons.Default.Explore,
                    selected = ageBand == AgeBand.AGE_7_TO_9,
                    onClick = { onAgeBandSelected(AgeBand.AGE_7_TO_9) },
                )
                AgeBandCard(
                    title = "Ages 10–12",
                    subtitle = "Middle Prep • Advanced projects, autonomy",
                    icon = Icons.Default.Psychology,
                    selected = ageBand == AgeBand.AGE_10_TO_12,
                    onClick = { onAgeBandSelected(AgeBand.AGE_10_TO_12) },
                )
            }

            Spacer(Modifier.height(MeritSpacing.lg))

            // 4. Language of Instruction
            Text(
                text = "Language of Instruction",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurface,
            )
            Spacer(Modifier.height(6.dp))
            var languageMenuExpanded by remember { mutableStateOf(false) }
            val languageOptions = listOf(
                "English (US)",
                "Español (Latin America)",
                "Français",
                "Deutsch",
                "Português",
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MeritColors.SurfaceContainerLowest,
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { languageMenuExpanded = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = MeritSpacing.md, vertical = MeritSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = language,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MeritColors.OnSurface,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Select language",
                            tint = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
                DropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false },
                ) {
                    languageOptions.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                onLanguageSelected(lang)
                                languageMenuExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.lg))

            // 6. Curriculum Focus Multi-Select Chips (age-band specific)
            val focusOptions = remember(ageBand) { CurriculumFocusCatalog.optionsFor(ageBand) }
            val selectedFocus = remember(ageBand, curriculumFocusIds) {
                CurriculumFocusCatalog.reconcile(ageBand, curriculumFocusIds).toSet()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Curriculum Focus",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = "Multi-select · up to ${CurriculumFocusCatalog.MAX_SELECTION}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
            Spacer(modifier.height(MeritSpacing.xs))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                focusOptions.forEach { topic ->
                    CurriculumFocusChip(
                        topic = topic,
                        selected = topic.id in selectedFocus,
                        onToggle = { onCurriculumFocusToggled(topic.id) },
                    )
                }
            }

            // Children Draft list if already added
            if (children.isNotEmpty()) {
                Spacer(Modifier.height(MeritSpacing.lg))
                Text(
                    text = "Configured Children (${children.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Spacer(Modifier.height(MeritSpacing.xs))
                children.forEach { draft ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeritColors.SurfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = MeritSpacing.md, vertical = MeritSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                            ) {
                                Text(draft.avatar.emoji, fontSize = 24.sp)
                                Column {
                                    Text(
                                        text = draft.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MeritColors.OnSurface,
                                    )
                                    Text(
                                        text = draft.ageBand.displayLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MeritColors.OnSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { onRemoveChild(draft.localId) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove child",
                                    tint = MeritColors.OnSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.xl))

            // 6. Save & Continue Action Button
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MeritColors.Primary,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable(enabled = !isSubmitting, onClick = onSaveAndContinue),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = MeritColors.OnPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(MeritSpacing.sm))
                        Text(
                            text = "Saving Profile...",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnPrimary,
                        )
                    } else {
                        Text(
                            text = "Save & Continue to Pairing",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnPrimary,
                        )
                        Spacer(Modifier.width(MeritSpacing.xs))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MeritColors.OnPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.sm))

            // Security Footnote
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Protected by Parent PIN • Settings can be adjusted anytime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeritColors.OnSurfaceVariant.copy(alpha = 0.85f),
                )
            }

            Spacer(Modifier.height(MeritSpacing.lg))
        }
    }
}

@Composable
private fun AgeBandCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MeritColors.Primary else MeritColors.SurfaceContainerLow,
        label = "ageCardBg",
    )
    val contentColor = if (selected) MeritColors.OnPrimary else MeritColors.OnSurface
    val subColor = if (selected) MeritColors.OnPrimaryContainer else MeritColors.OnSurfaceVariant

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (selected) MeritColors.PrimaryContainer else MeritColors.SurfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) MeritColors.OnPrimary else MeritColors.OnSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = contentColor,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subColor,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) MeritColors.OnPrimary else MeritColors.SurfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}


@Composable
private fun CurriculumFocusChip(
    topic: CurriculumFocusTopic,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) MeritColors.Primary else MeritColors.SurfaceContainerHigh,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = Modifier.clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MeritColors.OnPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = topic.displayLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (selected) MeritColors.OnPrimary else MeritColors.OnSurface,
            )
        }
    }
}



private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

