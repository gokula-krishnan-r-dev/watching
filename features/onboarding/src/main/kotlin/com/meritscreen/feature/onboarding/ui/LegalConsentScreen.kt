package com.meritscreen.feature.onboarding.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/** S02 — a short, plain-language notice plus explicit parental consent (COPPA-style). */
@Serializable
data object LegalConsentRoute

@HiltViewModel
class LegalConsentViewModel @Inject constructor(
    private val repository: OnboardingDraftRepository,
) : ViewModel() {

    val uiState: StateFlow<UiState<Boolean>> = repository.draft
        .map<OnboardingDraft, UiState<Boolean>> { UiState.Success(it.consentGiven) }
        .catch { emit(UiState.Error(AppErrorMapper.from(it))) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    fun setConsentGiven(given: Boolean) {
        viewModelScope.launch { repository.setConsentGiven(given) }
    }
}

@Composable
fun LegalConsentScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LegalConsentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        UiState.Loading -> LoadingState(message = "Loading")
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(message = current.error.userMessage)
        is UiState.Success -> {
            LegalConsentContent(
                consentGiven = current.data,
                onConsentChanged = viewModel::setConsentGiven,
                onContinue = onContinue,
                onBack = onBack,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun LegalConsentContent(
    consentGiven: Boolean,
    onConsentChanged: (Boolean) -> Unit,
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
                        text = "Step 1 of 4 • Parental Consent",
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
                    text = "A safe space, by design",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Before we set up your family account, here is our strict privacy pledge.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(MeritSpacing.lg))

            // Pledge Cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
            ) {
                PrivacyPledgeRow(
                    icon = Icons.Default.NoAccounts,
                    iconBg = MeritColors.PrimaryFixed,
                    iconTint = MeritColors.Primary,
                    title = "Children never create accounts",
                    description = "Kids don't sign in, provide email addresses, or create public profiles. Their phone is a paired device only.",
                )

                PrivacyPledgeRow(
                    icon = Icons.Default.Lock,
                    iconBg = MeritColors.TertiaryFixed,
                    iconTint = MeritColors.Tertiary,
                    title = "Zero personal tracking",
                    description = "We never track your child's GPS location, private photos, contacts, SMS messages, or browsing history.",
                )

                PrivacyPledgeRow(
                    icon = Icons.Default.VerifiedUser,
                    iconBg = MeritColors.SecondaryContainer,
                    iconTint = MeritColors.OnSecondaryContainer,
                    title = "COPPA-compliant & ad-free",
                    description = "Strictly adheres to child safety guidelines. 100% free of advertising networks and third-party trackers.",
                )
            }

            Spacer(modifier = Modifier.height(MeritSpacing.lg))

            // Interactive Consent Box
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (consentGiven) MeritColors.PrimaryFixed.copy(alpha = 0.2f) else MeritColors.SurfaceContainerLowest,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (consentGiven) MeritColors.Primary else MeritColors.OutlineVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConsentChanged(!consentGiven) }
                    .testTag("consent_card"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MeritSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                ) {
                    Checkbox(
                        checked = consentGiven,
                        onCheckedChange = onConsentChanged,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MeritColors.Primary,
                            uncheckedColor = MeritColors.Outline,
                        ),
                        modifier = Modifier.testTag("consent_checkbox"),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "I am the parent or legal guardian and I consent",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "I confirm setting up this account on my child's behalf, agreeing to Watching's Privacy Policy and child-safe Terms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.xl))

            // CTA Button
            Button(
                onClick = onContinue,
                enabled = consentGiven,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeritColors.PrimaryContainer,
                    contentColor = Color.White,
                    disabledContainerColor = MeritColors.SurfaceContainerHigh,
                    disabledContentColor = MeritColors.OnSurfaceVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("consent_continue"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Agree & Continue",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))
        }
    }
}

@Composable
private fun PrivacyPledgeRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    description: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLowest,
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeritSpacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MeritSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}
