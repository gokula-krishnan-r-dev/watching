package com.meritscreen.feature.authentication.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.ui.components.PairingQrScanner
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritPinDigitStyle
import com.meritscreen.core.ui.theme.MeritSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChildPairingScreen(
    onPaired: () -> Unit,
    onBack: (() -> Unit)?,
    viewModel: ChildPairingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.pairedSuccessfully) {
        if (state.pairedSuccessfully) {
            delay(1_100)
            viewModel.consumeSuccessNavigation(onPaired)
        }
    }

    ChildPairingContent(
        code = state.code,
        isSubmitting = state.isSubmitting,
        isScanning = state.isScanning,
        pairedSuccessfully = state.pairedSuccessfully,
        error = state.error,
        usageAccessGranted = state.usageAccessGranted,
        confirmedProfile = state.confirmedProfile,
        onCodeChanged = viewModel::onCodeChanged,
        onToggleUsageAccess = viewModel::toggleUsageAccess,
        onConfirmProfile = viewModel::confirmProfile,
        onStartScanning = viewModel::startScanning,
        onStopScanning = viewModel::stopScanning,
        onQrScanned = viewModel::onQrScanned,
        onCompleteSetup = { viewModel.submit() },
        onBack = onBack,
    )
}

@Composable
fun ChildPairingContent(
    code: String,
    isSubmitting: Boolean,
    isScanning: Boolean = false,
    pairedSuccessfully: Boolean = false,
    error: AppError?,
    usageAccessGranted: Boolean,
    confirmedProfile: Boolean,
    onCodeChanged: (String) -> Unit,
    onToggleUsageAccess: () -> Unit,
    onConfirmProfile: (Boolean) -> Unit,
    onStartScanning: () -> Unit = {},
    onStopScanning: () -> Unit = {},
    onQrScanned: (com.meritscreen.core.ui.components.PairingQrPayload.Parsed) -> Unit = {},
    onCompleteSetup: () -> Unit,
    onBack: (() -> Unit)? = null,
    childName: String = "Leo",
    childAge: Int = 8,
    childBand: String = "Elementary Quest",
    inviterName: String = "Mom & Dad (Sarah Miller)",
) {
    val scope = rememberCoroutineScope()
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var toastSubMessage by remember { mutableStateOf("") }

    fun triggerToast(msg: String, sub: String) {
        toastMessage = msg
        toastSubMessage = sub
        showToast = true
        scope.launch {
            delay(2800)
            showToast = false
        }
    }

    Scaffold(
        containerColor = MeritColors.Surface,
        topBar = {
            ChildPairingTopBar(
                title = "Set up child device",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MeritSpacing.md, vertical = MeritSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MeritSpacing.md),
            ) {
                // Kid-Friendly Header & Status
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MeritSpacing.xs, bottom = MeritSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Welcome to your new digital playground! 🎈",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MeritColors.OnSurface,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Safe, balanced, and ready for your favorite games and learning quests.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeritColors.OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = MeritSpacing.md),
                    )
                }

                // Main Card Container: Profile Confirmation & Code
                Surface(
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MeritSpacing.md),
                    ) {

                        // QR scan zone — above the 6-digit code for instant parent→child pairing
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MeritColors.SurfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MeritSpacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Scan parent QR",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MeritColors.OnSurfaceVariant,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = when {
                                            pairedSuccessfully -> MeritColors.TertiaryFixed
                                            isScanning -> MeritColors.PrimaryContainer
                                            else -> MeritColors.SecondaryContainer
                                        },
                                    ) {
                                        Text(
                                            text = when {
                                                pairedSuccessfully -> "Paired"
                                                isScanning -> "Scanning…"
                                                else -> "Fastest"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = when {
                                                pairedSuccessfully -> MeritColors.OnTertiaryFixed
                                                isScanning -> MeritColors.OnPrimaryContainer
                                                else -> MeritColors.OnSecondaryContainer
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                }

                                if (pairedSuccessfully) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MeritColors.Tertiary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MeritColors.Tertiary,
                                                modifier = Modifier.size(56.dp),
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Device paired successfully!",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MeritColors.OnSurface,
                                            )
                                            Text(
                                                text = "Opening your playground…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MeritColors.OnSurfaceVariant,
                                            )
                                        }
                                    }
                                } else if (isScanning) {
                                    PairingQrScanner(
                                        enabled = !isSubmitting,
                                        onPayload = { parsed ->
                                            onQrScanned(parsed)
                                            triggerToast("QR recognized!", "Linking with your family…")
                                        },
                                        onClose = onStopScanning,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MeritColors.InverseSurface)
                                            .clickable(enabled = !isSubmitting, onClick = onStartScanning),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.QrCodeScanner,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(48.dp),
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "Tap to scan parent QR",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = Color.White,
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Hold this tablet up to the code on the parent phone",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.8f),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Split 6-Digit Pairing Code Block
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MeritColors.SurfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MeritSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Pairing Code",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MeritColors.OnSurfaceVariant,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MeritColors.TertiaryFixed,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MeritColors.OnTertiaryFixed,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                text = when {
                                                    pairedSuccessfully -> "Paired"
                                                    code.length == AppConfig.PAIRING_CODE_LENGTH -> "Code Ready"
                                                    else -> "Enter Code"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MeritColors.OnTertiaryFixed,
                                            )
                                        }
                                    }
                                }

                                // Interactive Split Digit Display — error only after a failed submit
                                SplitDigitInputRow(
                                    code = code,
                                    onCodeChanged = onCodeChanged,
                                    maxLength = AppConfig.PAIRING_CODE_LENGTH,
                                    isError = error != null && !isSubmitting && !pairedSuccessfully,
                                    enabled = !isSubmitting && !pairedSuccessfully,
                                )

                                Text(
                                    text = "Or type the 6-digit code from the parent phone",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MeritColors.Secondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                // Error card if any
                if (error != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = error.userMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(MeritSpacing.sm),
                        )
                    }
                }

                // Primary Launch Action
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MeritSpacing.xs),
                ) {
                    Button(
                        onClick = onCompleteSetup,
                        enabled = code.length == AppConfig.PAIRING_CODE_LENGTH &&
                            !isSubmitting &&
                            !pairedSuccessfully,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeritColors.Primary,
                            disabledContainerColor = MeritColors.Primary.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        when {
                            pairedSuccessfully -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Paired — continuing…",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                            }
                            isSubmitting -> {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Preparing $childName's World...",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.RocketLaunch,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Complete Setup & Enter Launcher",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MeritColors.Tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Protected by Watching Shield • Safe & Private",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MeritSpacing.md))
            }

            // Animated Celebration Toast
            AnimatedVisibility(
                visible = showToast,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(MeritSpacing.md),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MeritColors.OnSurface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MeritSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MeritColors.PrimaryFixed),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Celebration,
                                contentDescription = null,
                                tint = MeritColors.OnPrimaryFixed,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = toastMessage,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                            )
                            if (toastSubMessage.isNotEmpty()) {
                                Text(
                                    text = toastSubMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitDigitInputRow(
    code: String,
    onCodeChanged: (String) -> Unit,
    maxLength: Int,
    isError: Boolean,
    enabled: Boolean = true,
) {
    val digitsOnly = code.filter(Char::isDigit).take(maxLength)

    BasicTextField(
        value = TextFieldValue(digitsOnly, selection = androidx.compose.ui.text.TextRange(digitsOnly.length)),
        onValueChange = { new ->
            if (!enabled) return@BasicTextField
            val digits = new.text.filter(Char::isDigit).take(maxLength)
            onCodeChanged(digits)
        },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions.Default,
        decorationBox = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // First 3 digits
                repeat(3) { i ->
                    DigitBox(
                        digit = digitsOnly.getOrNull(i)?.toString(),
                        isError = isError,
                    )
                    if (i < 2) Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = "—",
                    color = MeritColors.Secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                // Last 3 digits
                repeat(3) { i ->
                    val index = i + 3
                    DigitBox(
                        digit = digitsOnly.getOrNull(index)?.toString(),
                        isError = isError,
                    )
                    if (i < 2) Spacer(modifier = Modifier.width(6.dp))
                }
            }
        },
    )
}

@Composable
private fun DigitBox(
    digit: String?,
    isError: Boolean,
) {
    val filled = !digit.isNullOrEmpty()
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        filled -> MeritColors.Primary
        else -> MeritColors.OutlineVariant.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MeritColors.SurfaceContainerLowest)
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (filled) {
            Text(
                text = digit.orEmpty(),
                style = MeritPinDigitStyle.copy(fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
                color = MeritColors.Primary,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MeritColors.OutlineVariant.copy(alpha = 0.4f)),
            )
        }
    }
}

@Composable
private fun TabletReadyFeatureRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    desc: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MeritColors.SurfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeritSpacing.sm),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChildPairingTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MeritColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = MeritSpacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading — back or spacer so the title stays centered
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (onBack != null) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MeritColors.SurfaceContainerLow,
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
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Trailing balance spacer (matches leading width)
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    isReady: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MeritColors.SurfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MeritSpacing.sm, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Pending,
                    contentDescription = null,
                    tint = if (isReady) MeritColors.Tertiary else MeritColors.Secondary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MeritColors.OnSurface,
                )
            }
            Text(
                text = if (isReady) "Ready" else "Pending",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isReady) MeritColors.Tertiary else MeritColors.Secondary,
            )
        }
    }
}

