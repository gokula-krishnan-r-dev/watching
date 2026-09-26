package com.meritscreen.feature.authentication.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.ui.components.NativeOtpInputField
import com.meritscreen.core.ui.components.OtpInputView
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import com.meritscreen.core.ui.theme.headlineLargeMobile

import com.meritscreen.feature.authentication.domain.ParentAuthResult

@Composable
fun OtpVerificationScreenRoute(
    email: String,
    onAuthenticated: (ParentAuthResult) -> Unit,
    onNeedsOnboarding: () -> Unit,
    onBack: () -> Unit,
    viewModel: OtpVerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(email) {
        viewModel.initEmail(email)
    }

    OtpVerificationScreen(
        state = state,
        onOtpChange = { otp ->
            viewModel.onOtpChanged(otp) { result ->
                dispatchAuthResult(result, onAuthenticated, onNeedsOnboarding)
            }
        },
        onDigitClick = { digit ->
            viewModel.onDigitEntered(digit) { result ->
                dispatchAuthResult(result, onAuthenticated, onNeedsOnboarding)
            }
        },
        onBackspaceClick = viewModel::onBackspace,
        onResendCode = viewModel::resendCode,
        onBack = onBack,
    )
}

@Composable
fun OtpVerificationScreen(
    state: OtpVerificationUiState,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onResendCode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOtpChange: ((String) -> Unit)? = null,
) {
    val scrollState = rememberScrollState()

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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(MeritSpacing.xs))

        // Top App Bar / Step Status
        OtpTopBar(
            stepText = "Security Gate • Step 2 of 3",
            onBack = onBack,
            onHelp = { /* Support help handler */ },
        )

        Spacer(Modifier.height(MeritSpacing.sm))

        // Main Icon Badge
        SecurityIconBadge()

        Spacer(Modifier.height(16.dp))

        // Title & Recipient Subtitle
        Text(
            text = "Enter 6-Digit Code",
            style = MaterialTheme.typography.headlineLargeMobile,
            fontWeight = FontWeight.SemiBold,
            color = MeritColors.OnSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "We sent a secure parent verification code to",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = state.email,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MeritColors.OnSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        // 6-Digit Segmented OTP — Autofill (Gmail) + clipboard + manual entry
        NativeOtpInputField(
            value = state.otp,
            onValueChange = { newOtp ->
                if (onOtpChange != null) {
                    onOtpChange(newOtp)
                } else {
                    val lastChar = newOtp.lastOrNull()
                    if (newOtp.length > state.otp.length && lastChar != null) {
                        onDigitClick(lastChar)
                    } else if (newOtp.length < state.otp.length) {
                        onBackspaceClick()
                    }
                }
            },
            length = 6,
            isError = state.error != null,
            enabled = !state.isSubmitting && !state.isSuccess,
            autoFocus = true,
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tip: when the email arrives, tap the suggested code above the keyboard — or copy it from Gmail and return here.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        if (state.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.error.userMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Resend / Countdown Row
        ResendCountdownRow(
            countdownSeconds = state.resendCountdownSeconds,
            canResend = state.canResend,
            isResending = state.isResending,
            onResend = onResendCode,
        )

        if (state.isSubmitting) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Verifying Code...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Security & Trust Footer Badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Secure verification • Code expires in 10 minutes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(MeritSpacing.lg))
    }
}

@Composable
private fun OtpTopBar(
    stepText: String,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

        // Center Step Pill
        Surface(
            shape = RoundedCornerShape(9999.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = stepText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SecurityIconBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(80.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer Circle
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryFixed,
            shadowElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Inner White Circle
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 1.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
        }

        // Corner Verification Badge
        Surface(
            modifier = Modifier
                .size(28.dp)
                .align(Alignment.BottomEnd),
            shape = CircleShape,
            color = MeritColors.Tertiary,
            shadowElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ResendCountdownRow(
    countdownSeconds: Int,
    canResend: Boolean,
    isResending: Boolean,
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin_timer")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin_rotation",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Didn't receive code?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (canResend) {
            if (isResending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = "Resend Code",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onResend)
                        .padding(vertical = 2.dp),
                )
            }
        } else {
            val seconds = if (countdownSeconds < 10) "0$countdownSeconds" else "$countdownSeconds"
            Surface(
                shape = RoundedCornerShape(9999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .size(14.dp)
                            .rotate(rotation),
                    )
                    Text(
                        text = "Resend in 0:$seconds",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
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
