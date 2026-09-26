package com.meritscreen.feature.authentication.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.core.ui.components.GoogleSignInButton
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import com.meritscreen.core.ui.theme.headlineLargeMobile

import com.meritscreen.feature.authentication.domain.ParentAuthResult
import com.meritscreen.feature.authentication.google.GoogleIdTokenClient
import com.meritscreen.feature.authentication.google.GoogleSignInCancelled
import kotlinx.coroutines.launch

@Composable
fun SignInRoute(
    onAuthenticated: (ParentAuthResult) -> Unit,
    onNeedsOnboarding: () -> Unit,
    onOtpRequested: (email: String) -> Unit,
    onBack: (() -> Unit)?,
    googleIdTokenClient: GoogleIdTokenClient,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? Activity

    LaunchedEffect(Unit) {
        viewModel.resumeIfAlreadySignedIn { result ->
            dispatchAuthResult(result, onAuthenticated, onNeedsOnboarding)
        }
    }

    SignInScreen(
        state = state,
        onEmailChanged = viewModel::onEmailChanged,
        onRememberDeviceChanged = viewModel::onRememberDeviceChanged,
        onSubmitEmail = {
            viewModel.sendOtp { email ->
                onOtpRequested(email)
            }
        },
        onGoogleSignIn = {
            val host = activity ?: return@SignInScreen
            scope.launch {
                try {
                    when (val tokenOutcome = googleIdTokenClient.getIdToken(host)) {
                        is Outcome.Success -> {
                            viewModel.submitGoogleToken(tokenOutcome.value) { result ->
                                dispatchAuthResult(result, onAuthenticated, onNeedsOnboarding)
                            }
                        }
                        is Outcome.Failure -> {
                            viewModel.showGoogleError(tokenOutcome.error)
                        }
                    }
                } catch (_: GoogleSignInCancelled) {
                    viewModel.onGoogleCancelled()
                }
            }
        },
        onBack = onBack,
    )
}

@Composable
fun SignInScreen(
    state: SignInUiState,
    onEmailChanged: (String) -> Unit,
    onRememberDeviceChanged: (Boolean) -> Unit,
    onSubmitEmail: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    
    fun onNavigateToTour() {
        // TODO: Implement tour navigation
        println("Navigating to tour")
        //navigate to tour screen

    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = MeritSpacing.margin)
            .verticalScroll(scrollState),
    ) {
        Spacer(Modifier.height(MeritSpacing.xs))

        // Top App Bar
        ParentPortalTopBar(
            onBack = onBack,
            onHelp = { /* Support and help handler */ },
        )

        Spacer(Modifier.height(MeritSpacing.sm))

        // Header Section
        HeaderSection()

        Spacer(Modifier.height(MeritSpacing.md))

        // Main Interaction Card Container
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Google Single Sign-On Button (Always visible)
                GoogleSignInButton(
                    onClick = onGoogleSignIn,
                    loading = state.isGoogleSubmitting,
                    enabled = !state.isSubmitting,
                )

                Spacer(Modifier.height(20.dp))

                // Visual Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        thickness = 1.dp,
                    )
                    Text(
                        text = "OR SIGN IN WITH EMAIL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        thickness = 1.dp,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Email Input Field
                EmailInputField(
                    email = state.email,
                    isValid = state.isValidEmail,
                    onEmailChanged = onEmailChanged,
                    onSubmit = onSubmitEmail,
                    enabled = !state.isSubmitting && !state.isGoogleSubmitting,
                )

                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.error.userMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Primary CTA Button (Unified Email OTP dispatch)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable(
                            enabled = !state.isSubmitting && !state.isGoogleSubmitting,
                            role = Role.Button,
                            onClick = onSubmitEmail,
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Sending Code...",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                text = "Sign in to Dashboard",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Unified Access & Compliance Footer (Login & Registration Unified)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MeritSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Single unified access for existing and new parent accounts",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "COPPA & GDPR Compliant • 256-bit encryption",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

            }
}

@Composable
private fun ParentPortalTopBar(
    onBack: (() -> Unit)?,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: Back Button or Spacer
        if (onBack != null) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        tint = MeritColors.OnSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }

        // Center: Pill Badge
        Surface(
            shape = RoundedCornerShape(9999.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "PARENT PORTAL",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MeritColors.OnSurface,
                    letterSpacing = 0.5.sp,
                )
            }
        }

        // Right: Info Button
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            IconButton(onClick = { showInfoDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Sign In Info",
                    tint = MeritColors.OnSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Info Dialog about sign in
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = {
                    Text("About Sign In", style = MaterialTheme.typography.titleMedium)
                },
                text = {
                    Text(
                        "Sign in to securely access your Parent Portal. " +
                        "You'll get a verification code sent to your email. " +
                        "New and existing parents use the same simple sign in process."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun HeaderSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineLargeMobile,
                color = MeritColors.OnSurface,
            )
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "👋",
                        fontSize = 15.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Sign in or enter your email to get started. We'll send a secure 6-digit verification code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(14.dp))

        // Family Protection Badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FamilyPillBadge(
                label = "Screen Limits",
                icon = Icons.Default.Shield,
                color = MaterialTheme.colorScheme.secondaryFixed,
            )
            FamilyPillBadge(
                label = "Study Rewards",
                icon = Icons.Default.VerifiedUser,
                color = MaterialTheme.colorScheme.primaryFixed,
            )
        }
    }
}

@Composable
private fun FamilyPillBadge(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = RoundedCornerShape(9999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                color = color,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MeritColors.OnSurface,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MeritColors.OnSurface,
            )
        }
    }
}

@Composable
private fun EmailInputField(
    email: String,
    isValid: Boolean,
    onEmailChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Parent Email",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Mail,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )

                Spacer(Modifier.width(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (email.isEmpty()) {
                        Text(
                            text = "Enter your parent email",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    BasicTextField(
                        value = email,
                        onValueChange = onEmailChanged,
                        enabled = enabled,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MeritColors.OnSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AnimatedVisibility(
                    visible = isValid,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryFixed,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Valid email",
                                tint = MaterialTheme.colorScheme.onTertiaryFixed,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun dispatchAuthResult(
    result: ParentAuthResult,
    onAuthenticated: (ParentAuthResult) -> Unit,
    onNeedsOnboarding: () -> Unit,
) {
    if (result.needsOnboarding) onNeedsOnboarding() else onAuthenticated(result)
}
