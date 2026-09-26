package com.meritscreen.feature.onboarding.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.components.NativePinInputField
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import kotlinx.serialization.Serializable

/** P07 — Parent PIN, hashed on-device (PBKDF2) and never stored or logged in plain text. */
@Serializable
data object SetParentPinRoute

enum class PinStep {
    ENTER_INITIAL,
    CONFIRM,
}

@Composable
fun SetParentPinScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetParentPinViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pinSaved by viewModel.pinSaved.collectAsStateWithLifecycle()

    LaunchedEffect(pinSaved) {
        if (pinSaved) onContinue()
    }

    when (val current = state) {
        UiState.Loading -> LoadingState()
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(message = current.error.userMessage)
        is UiState.Success -> {
            val pin by viewModel.pin.collectAsStateWithLifecycle()
            val confirmPin by viewModel.confirmPin.collectAsStateWithLifecycle()
            val formError by viewModel.formError.collectAsStateWithLifecycle()
            SetParentPinContent(
                modifier = modifier,
                pin = pin,
                confirmPin = confirmPin,
                formError = formError,
                onPinChanged = viewModel::onPinChanged,
                onConfirmPinChanged = viewModel::onConfirmPinChanged,
                onSave = viewModel::savePin,
                onBack = onBack,
            )
        }
    }
}

@Composable
fun SetParentPinContent(
    pin: String,
    confirmPin: String,
    formError: String?,
    onPinChanged: (String) -> Unit,
    onConfirmPinChanged: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var currentStep by remember { mutableStateOf(PinStep.ENTER_INITIAL) }

    val activeDigits = if (currentStep == PinStep.ENTER_INITIAL) pin else confirmPin
    val targetLength = 4

    val isPinValid = pin.length in AppConfig.PARENT_PIN_MIN_LENGTH..AppConfig.PARENT_PIN_MAX_LENGTH
    val isConfirmValid = confirmPin.length in AppConfig.PARENT_PIN_MIN_LENGTH..AppConfig.PARENT_PIN_MAX_LENGTH
    val pinsMatch = isPinValid && isConfirmValid && pin == confirmPin

    fun handleDigit(d: String) {
        if (currentStep == PinStep.ENTER_INITIAL) {
            if (pin.length < AppConfig.PARENT_PIN_MAX_LENGTH) {
                val next = pin + d
                onPinChanged(next)
                if (next.length == targetLength) {
                    currentStep = PinStep.CONFIRM
                }
            }
        } else {
            if (confirmPin.length < AppConfig.PARENT_PIN_MAX_LENGTH) {
                val next = confirmPin + d
                onConfirmPinChanged(next)
            }
        }
    }

    fun handleDelete() {
        if (currentStep == PinStep.ENTER_INITIAL) {
            if (pin.isNotEmpty()) {
                onPinChanged(pin.dropLast(1))
            }
        } else {
            if (confirmPin.isNotEmpty()) {
                onConfirmPinChanged(confirmPin.dropLast(1))
            } else {
                currentStep = PinStep.ENTER_INITIAL
            }
        }
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
                .verticalScroll(scrollState)
                .padding(horizontal = MeritSpacing.md, vertical = MeritSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
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
                    onClick = {
                        if (currentStep == PinStep.CONFIRM) {
                            currentStep = PinStep.ENTER_INITIAL
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MeritColors.SurfaceContainerLowest),
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
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MeritColors.OnSecondaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Step 3 of 4 • Parent Security",
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
                    text = if (currentStep == PinStep.ENTER_INITIAL) "Create Parent PIN" else "Confirm Parent PIN",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (currentStep == PinStep.ENTER_INITIAL) {
                        "This 4-digit code keeps limits, approvals, and launcher controls safely guarded while your child explores."
                    } else {
                        "Re-enter your 4-digit code to ensure it's correct before saving."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Step Switcher Tabs (Create PIN vs Confirm PIN)
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MeritColors.SurfaceContainerHigh)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (currentStep == PinStep.ENTER_INITIAL) MeritColors.SurfaceContainerLowest else Color.Transparent,
                    modifier = Modifier.clickable { currentStep = PinStep.ENTER_INITIAL },
                ) {
                    Text(
                        text = "1. Choose PIN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (currentStep == PinStep.ENTER_INITIAL) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (currentStep == PinStep.ENTER_INITIAL) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (currentStep == PinStep.CONFIRM) MeritColors.SurfaceContainerLowest else Color.Transparent,
                    modifier = Modifier.clickable {
                        if (pin.length >= AppConfig.PARENT_PIN_MIN_LENGTH) currentStep = PinStep.CONFIRM
                    },
                ) {
                    Text(
                        text = "2. Confirm PIN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (currentStep == PinStep.CONFIRM) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (currentStep == PinStep.CONFIRM) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Interactive PIN Display with Native Soft Keyboard
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
                        .padding(vertical = MeritSpacing.lg, horizontal = MeritSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    var isMasked by remember { mutableStateOf(true) }

                    // Native PIN Input Field with soft keyboard
                    NativePinInputField(
                        pin = activeDigits,
                        onPinChange = { updated ->
                            if (currentStep == PinStep.ENTER_INITIAL) {
                                onPinChanged(updated)
                                if (updated.length == targetLength) {
                                    currentStep = PinStep.CONFIRM
                                }
                            } else {
                                onConfirmPinChanged(updated)
                            }
                        },
                        length = targetLength,
                        isMasked = isMasked,
                        isError = formError != null,
                        autoFocus = true,
                        onComplete = {
                            if (currentStep == PinStep.ENTER_INITIAL) {
                                currentStep = PinStep.CONFIRM
                            } else if (pinsMatch) {
                                onSave()
                            }
                        },
                        modifier = Modifier.padding(vertical = MeritSpacing.sm),
                    )

                    Spacer(modifier = Modifier.height(MeritSpacing.sm))

                    // Micro-feedback state text & visibility toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = MeritSpacing.sm),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (formError != null) {
                                Text(
                                    text = formError,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else if (currentStep == PinStep.CONFIRM && pinsMatch) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MeritColors.Primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "PINs match! Ready to save",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MeritColors.Primary,
                                )
                            } else if (currentStep == PinStep.ENTER_INITIAL) {
                                val remaining = (targetLength - pin.length).coerceAtLeast(0)
                                Icon(
                                    imageVector = Icons.Default.LockClock,
                                    contentDescription = null,
                                    tint = MeritColors.Primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (remaining > 0) "Enter $remaining more digit${if (remaining > 1) "s" else ""}" else "PIN entered! Tap next to confirm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            } else {
                                val remaining = (targetLength - confirmPin.length).coerceAtLeast(0)
                                Icon(
                                    imageVector = Icons.Default.LockClock,
                                    contentDescription = null,
                                    tint = MeritColors.Primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (remaining > 0) "Re-enter $remaining more digit${if (remaining > 1) "s" else ""}" else "Verifying match...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            }
                        }

                        // Mask / Unmask Toggle
                        Text(
                            text = if (isMasked) "Show PIN" else "Hide PIN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MeritColors.Primary,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isMasked = !isMasked }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Reassuring "Why create a PIN?" Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MeritSpacing.md),
                    verticalAlignment = Alignment.Top,
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
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MeritColors.OnPrimaryFixed,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Why create a PIN?",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "When your child holds the phone, this PIN lets you bypass lockouts, grant instant bonus time, or exit straight to Android settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.lg))

            // Action / Save Button
            Button(
                onClick = {
                    if (currentStep == PinStep.ENTER_INITIAL) {
                        if (isPinValid) currentStep = PinStep.CONFIRM
                    } else {
                        onSave()
                    }
                },
                enabled = if (currentStep == PinStep.ENTER_INITIAL) isPinValid else isConfirmValid,
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
                    .testTag("save_parent_pin"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (currentStep == PinStep.ENTER_INITIAL) "Next: Confirm PIN" else "Set PIN & Continue",
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

            // Hardware Keystore Peace-of-Mind Pill
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MeritColors.SurfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.EnhancedEncryption,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Stored in on-device hardware Keystore. Never sent to cloud.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(MeritSpacing.sm))
        }
    }
}
