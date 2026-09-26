package com.meritscreen.feature.onboarding.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.error.AppErrorMapper
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import com.meritscreen.feature.onboarding.data.OnboardingDraftRepository
import com.meritscreen.feature.onboarding.domain.OnboardingDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

/** P05 — family name is optional; the meaningful step is adding a child next. */
@Serializable
data object CreateFamilyRoute

@HiltViewModel
class CreateFamilyViewModel @Inject constructor(
    private val repository: OnboardingDraftRepository,
) : ViewModel() {

    val uiState: StateFlow<UiState<String>> = repository.draft
        .map<OnboardingDraft, UiState<String>> { UiState.Success(it.familyName) }
        .catch { emit(UiState.Error(AppErrorMapper.from(it))) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    fun setFamilyName(name: String) {
        viewModelScope.launch { repository.setFamilyName(name) }
    }
}

@Composable
fun CreateFamilyScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateFamilyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        UiState.Loading -> LoadingState()
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(message = current.error.userMessage)
        is UiState.Success -> {
            CreateFamilyContent(
                familyName = current.data,
                onFamilyNameChanged = viewModel::setFamilyName,
                onContinue = onContinue,
                onBack = onBack,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateFamilyContent(
    familyName: String,
    onFamilyNameChanged: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
                .padding(horizontal = MeritSpacing.md, vertical = MeritSpacing.xs),
        ) {
            // Top Bar with Back and Step Progress
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MeritSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MeritColors.SurfaceContainerLow),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MeritColors.OnSurface,
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MeritColors.SecondaryContainer.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
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
                        text = "Step 2 of 4 • Family Sanctuary",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSecondaryContainer,
                    )
                }

                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Conversational Headline
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Name your family space",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Give your household a friendly name for your parent dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(MeritSpacing.lg))

            // Family Decorative Illustration Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MeritColors.SurfaceContainerLowest,
                border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.4f)),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MeritSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MeritColors.PrimaryFixed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.FamilyRestroom,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(MeritSpacing.md))

                    OutlinedTextField(
                        value = familyName,
                        onValueChange = onFamilyNameChanged,
                        label = { Text("Family name (optional)") },
                        placeholder = { Text("Enter family name") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeritColors.Primary,
                            unfocusedBorderColor = MeritColors.OutlineVariant,
                            focusedContainerColor = MeritColors.SurfaceContainerLowest,
                            unfocusedContainerColor = MeritColors.SurfaceContainerLowest,
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("family_name_input"),
                    )

                    Spacer(modifier = Modifier.height(MeritSpacing.md))

                    // Quick Pick Suggestions
                    Text(
                        text = "Quick suggestions",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MeritColors.OnSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start),
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val suggestions = listOf("The Andersons", "Miller Crew", "Star Explorers", "The Sunshine Clan")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        suggestions.forEach { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (familyName == suggestion) MeritColors.PrimaryFixed else MeritColors.SurfaceContainerLow,
                                border = BorderStroke(
                                    1.dp,
                                    if (familyName == suggestion) MeritColors.Primary else MeritColors.OutlineVariant.copy(alpha = 0.5f),
                                ),
                                modifier = Modifier.clickable { onFamilyNameChanged(suggestion) },
                            ) {
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (familyName == suggestion) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (familyName == suggestion) MeritColors.OnPrimaryFixed else MeritColors.OnSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Reassurance info pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MeritSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "This name is private to your parent account. Children never see billing or account data on their launcher.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.xl))

            // Continue Button
            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeritColors.PrimaryContainer,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("create_family_continue"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Continue to Add Child",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))
        }
    }
}
