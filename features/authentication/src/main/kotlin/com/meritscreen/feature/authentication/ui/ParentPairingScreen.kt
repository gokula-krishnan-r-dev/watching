package com.meritscreen.feature.authentication.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.firebase.pairing.PairingOffer
import com.meritscreen.core.ui.components.ChildAvatar
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.components.MeritQrCodeImage
import com.meritscreen.core.ui.theme.MeritCodeLabelStyle
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritPinDigitStyleLarge
import com.meritscreen.core.ui.theme.MeritSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ParentPairingScreen(
    onPaired: () -> Unit,
    onSkip: () -> Unit,
    onBack: (() -> Unit)?,
    viewModel: ParentPairingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error = state.error
    val child = state.child
    val childName = child?.displayName?.takeIf { it.isNotBlank() } ?: "your child"

    when {
        state.devicePaired -> {
            DevicePairedSuccessfullyContent(
                childName = childName,
                ageBandLabel = child?.ageBandLabel ?: "Ages —",
                explorationLabel = child?.explorationLabel ?: "Adaptive Quest",
                avatar = child?.avatar ?: AvatarPreset.Default,
                deviceName = state.pairedDevice?.displayName
                    ?: if (childName == "your child") "Linked device" else "$childName's device",
                batteryPercent = state.pairedDevice?.batteryPercent,
                isOnline = state.pairedDevice?.isOnline ?: true,
                pairedLabel = state.pairedDevice?.pairedLabel ?: "Paired just now",
                rules = state.rules,
                onGoToDashboard = onPaired,
                onPairAnotherDevice = viewModel::pairAnotherDevice,
            )
        }
        state.isLoading && state.offer == null && error == null ->
            LoadingState(message = "Creating a pairing code")
        error != null && state.offer == null ->
            ErrorState(message = error.userMessage, onRetry = viewModel::refresh)
        else -> {
            ParentPairingCodeContent(
                offer = state.offer,
                isLoading = state.isLoading,
                error = error,
                onRefresh = viewModel::refresh,
                onSkip = onSkip,
                onBack = onBack,
                childName = childName,
                ageBandLabel = child?.ageBandLabel ?: "Ages —",
                explorationLabel = child?.explorationLabel ?: "Adaptive Quest",
                avatar = child?.avatar ?: AvatarPreset.Default,
            )
        }
    }
}

@Composable
fun DevicePairedSuccessfullyContent(
    childName: String,
    ageBandLabel: String,
    explorationLabel: String,
    avatar: AvatarPreset,
    deviceName: String,
    batteryPercent: Int? = null,
    isOnline: Boolean = true,
    pairedLabel: String = "Paired just now",
    rules: ParentPairingRulesInfo? = null,
    onGoToDashboard: () -> Unit,
    onPairAnotherDevice: () -> Unit,
    onCustomizeAllowlist: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var isBedtimePaused by remember { mutableStateOf(false) }
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var toastIcon by remember { mutableStateOf(Icons.Default.Celebration) }

    fun triggerToast(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        toastMessage = message
        toastIcon = icon
        showToast = true
        scope.launch {
            delay(2600)
            showToast = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_animation")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave_scale",
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave_alpha",
    )

    Scaffold(
        containerColor = MeritColors.Surface,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MeritSpacing.md, vertical = MeritSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MeritSpacing.md),
            ) {
                // Top Security Indicator Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MeritColors.SurfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "FAMILY VAULT VERIFIED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = MeritColors.Secondary,
                            )
                        }
                    }

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
                            text = "Encrypted Mesh",
                            style = MaterialTheme.typography.labelMedium,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }

                // Hero Celebration Block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MeritSpacing.xs, bottom = MeritSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Radiating Wave Backdrop with Central Hero Badge
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .padding(MeritSpacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Outer radiating circle
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .scale(waveScale)
                                .alpha(waveAlpha)
                                .clip(CircleShape)
                                .background(MeritColors.Primary),
                        )
                        // Middle soft pulse circle
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(MeritColors.Primary.copy(alpha = 0.12f)),
                        )
                        // Center Emerald Linked Icon Badge
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(MeritColors.PrimaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhonelinkSetup,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                        // Floating Sparkle Checkmark Badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 12.dp, top = 8.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MeritColors.SecondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MeritColors.OnSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Device Successfully Linked!",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "The safety shield is active and permissions are safely synchronized.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeritColors.OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = MeritSpacing.md),
                    )
                }

                // Linked Device Detail Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MeritColors.SurfaceContainerLowest,
                    border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.5f)),
                    shadowElevation = 1.dp,
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MeritSpacing.md),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MeritColors.SurfaceContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TabletAndroid,
                                    contentDescription = null,
                                    tint = MeritColors.Primary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = deviceName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurface,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 2.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isOnline) MeritColors.Primary
                                                    else MeritColors.OutlineVariant,
                                                ),
                                        )
                                        Text(
                                            text = if (isOnline) "Online" else "Linked",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                            color = if (isOnline) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                                        )
                                    }
                                    if (batteryPercent != null) {
                                        Text(text = "•", color = MeritColors.OutlineVariant)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.BatteryChargingFull,
                                                contentDescription = null,
                                                tint = MeritColors.OnSurfaceVariant,
                                                modifier = Modifier.size(15.dp),
                                            )
                                            Text(
                                                text = "$batteryPercent%",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MeritColors.OnSurfaceVariant,
                                            )
                                        }
                                    }
                                    Text(text = "•", color = MeritColors.OutlineVariant)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = MeritColors.OnSurfaceVariant,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Text(
                                            text = if (isOnline) "Active" else "Synced",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MeritColors.OnSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        // Child Profile preview row
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MeritColors.SurfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MeritSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                                ) {
                                    ChildAvatar(
                                        avatar = avatar,
                                        size = 36.dp,
                                        background = MeritColors.SecondaryContainer,
                                        contentDescription = "$childName avatar",
                                    )
                                    Column {
                                        Text(
                                            text = childName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MeritColors.OnSurface,
                                        )
                                        Text(
                                            text = "$ageBandLabel • $explorationLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MeritColors.Secondary,
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MeritColors.SurfaceContainer)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        text = "Child Profile",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MeritColors.OnSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Paired timestamp
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MeritColors.Outline,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = pairedLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MeritColors.Outline,
                            )
                        }
                    }
                }

                // Rules & Boundaries Applied Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MeritColors.SurfaceContainerLowest,
                    border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.5f)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MeritSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Rules & Boundaries Applied",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MeritColors.OnSurface,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MeritColors.TertiaryFixed)
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                val active = rules?.activeRuleCount ?: 0
                                val total = rules?.totalRuleSlots ?: 3
                                Text(
                                    text = "$active / $total Active",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MeritColors.Tertiary,
                                )
                            }
                        }

                        AppliedRuleRow(
                            title = "Daily Limit",
                            subtitle = rules?.dailyLimitSubtitle
                                ?: "Set a daily allowance anytime in Time Limits",
                        )
                        AppliedRuleRow(
                            title = "Approved Apps",
                            subtitle = rules?.allowedAppsSubtitle
                                ?: "Customize the allowlist from child details",
                        )
                        AppliedRuleRow(
                            title = "Emergency Access",
                            subtitle = rules?.emergencySubtitle
                                ?: "Emergency apps stay reachable when configured",
                        )
                    }
                }

                // Bottom CTA Buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onGoToDashboard,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeritColors.Primary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Go to Parent Dashboard",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }

                    Button(
                        onClick = onPairAnotherDevice,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeritColors.SurfaceContainerHigh,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pair Another Device",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.Primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MeritSpacing.lg))
            }

            // Floating Quick Action Feedback Toast
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
                            .padding(horizontal = MeritSpacing.md, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                    ) {
                        Icon(
                            imageVector = toastIcon,
                            contentDescription = null,
                            tint = MeritColors.TertiaryFixed,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = toastMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppliedRuleRow(
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MeritColors.PrimaryFixed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MeritColors.Primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MeritColors.OnSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MeritColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    desc: String,
    descColor: Color = MeritColors.OnSurfaceVariant,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeritSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MeritSpacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
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
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.labelSmall,
                        color = descColor,
                    )
                }
            }
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MeritColors.Outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

enum class PairingLinkMethod {
    SHARE_LINK,
    QR_CODE,
    SIX_DIGIT_PIN,
}

@Composable
fun ParentPairingCodeContent(
    offer: PairingOffer?,
    isLoading: Boolean,
    error: AppError?,
    onRefresh: () -> Unit,
    onSkip: () -> Unit,
    onBack: (() -> Unit)?,
    childName: String = "your child",
    ageBandLabel: String = "Ages —",
    explorationLabel: String = "Adaptive Quest",
    avatar: AvatarPreset = AvatarPreset.Default,
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    var selectedMethod by remember { mutableStateOf(PairingLinkMethod.QR_CODE) }
    var copyToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copyToast) {
        if (copyToast != null) {
            delay(2200)
            copyToast = null
        }
    }

    val codeText = offer?.code ?: "842916"
    val formattedCode = if (codeText.length >= 6) {
        "${codeText.take(3)} - ${codeText.substring(3, 6)}"
    } else {
        codeText
    }
    val inviteUrl = "meritscreen.family/pair/${childName.lowercase()}-${codeText.takeLast(4)}"

    Surface(
        modifier = Modifier.fillMaxSize(),
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
            // Top Bar with Flow Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MeritSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (onBack != null) {
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
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
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
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MeritColors.OnSecondaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Step 4 of 4 • Device Pairing",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSecondaryContainer,
                    )
                }

                // Pulsing activity indicator
                val infiniteTransition = rememberInfiniteTransition(label = "pulse_ping")
                val pingAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "ping_alpha",
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .alpha(pingAlpha)
                        .clip(CircleShape)
                        .background(MeritColors.Primary),
                )
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Active Child Profile Anchor Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MeritColors.SurfaceContainerLowest,
                border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.4f)),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MeritSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ChildAvatar(
                            avatar = avatar,
                            size = 48.dp,
                            background = MeritColors.SecondaryContainer,
                            contentDescription = "$childName avatar",
                        )

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = childName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MeritColors.OnSurface,
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MeritColors.TertiaryFixed)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = ageBandLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MeritColors.Tertiary,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = explorationLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                                Text(text = "•", color = MeritColors.OutlineVariant)
                                Text(
                                    text = "Ready to pair",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.Primary,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Method Selector Header & Tabs
            Text(
                text = "How would you like to link $childName's device?",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = MeritColors.OnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerHigh.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PairingTabItem(
                        icon = Icons.Default.QrCode2,
                        title = "Scan QR",
                        isSelected = selectedMethod == PairingLinkMethod.QR_CODE,
                        modifier = Modifier.weight(1f),
                    ) {
                        selectedMethod = PairingLinkMethod.QR_CODE
                    }

                    PairingTabItem(
                        icon = Icons.Default.Pin,
                        title = "6-Digit Code",
                        isSelected = selectedMethod == PairingLinkMethod.SIX_DIGIT_PIN,
                        modifier = Modifier.weight(1f),
                    ) {
                        selectedMethod = PairingLinkMethod.SIX_DIGIT_PIN
                    }

                    PairingTabItem(
                        icon = Icons.Default.Link,
                        title = "Share Link",
                        isSelected = selectedMethod == PairingLinkMethod.SHARE_LINK,
                        modifier = Modifier.weight(1f),
                    ) {
                        selectedMethod = PairingLinkMethod.SHARE_LINK
                    }
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Tab Content Panes
            when (selectedMethod) {
                PairingLinkMethod.QR_CODE -> {
                    QrCodePairingView(
                        qrPayload = offer?.qrPayload.orEmpty(),
                        formattedCode = formattedCode,
                        expiresLabel = offer?.let { formatOfferCountdown(it.expiresAtEpochMs) } ?: "—",
                        onCopyCode = {
                            clipboardManager.setText(AnnotatedString(codeText))
                            copyToast = "Pairing code copied to clipboard!"
                        },
                        onRegenerate = onRefresh,
                    )
                }
                PairingLinkMethod.SIX_DIGIT_PIN -> {
                    SixDigitPinPairingView(
                        formattedCode = formattedCode,
                        codeRaw = codeText,
                        childName = childName,
                        onCopyCode = {
                            clipboardManager.setText(AnnotatedString(codeText))
                            copyToast = "Pairing code copied to clipboard!"
                        },
                        onRegenerate = onRefresh,
                    )
                }
                PairingLinkMethod.SHARE_LINK -> {
                    ShareLinkPairingView(
                        inviteUrl = inviteUrl,
                        childName = childName,
                        onCopyLink = {
                            clipboardManager.setText(AnnotatedString(inviteUrl))
                            copyToast = "Magic link copied to clipboard!"
                        },
                        onShareChannel = { channel ->
                            copyToast = "Opening $channel share..."
                        },
                    )
                }
            }

            // Copy Feedback Toast
            AnimatedVisibility(visible = copyToast != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MeritColors.OnSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MeritColors.TertiaryFixed,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = copyToast ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Setup Instructions Card
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
                        .padding(MeritSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                ) {
                    Text(
                        text = "Setup Instructions",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )

                    PairingInstructionRow(
                        stepNumber = "1",
                        title = "Install Watching on child's phone",
                        subtitle = "Search 'Watching Kids Launcher' on $childName's device",
                    )
                    PairingInstructionRow(
                        stepNumber = "2",
                        title = "Open app & tap 'Set up this Child Device'",
                        subtitle = "Select child launcher mode on the first screen",
                    )
                    PairingInstructionRow(
                        stepNumber = "3",
                        title = "Scan QR or type 6-digit code",
                        subtitle = "Pairing completes immediately and syncs your rules",
                    )
                }
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(MeritSpacing.sm))
                Text(
                    text = error.userMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(MeritSpacing.lg))

            // Bottom CTAs
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeritColors.Primary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Refresh Code",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }

                OutlinedButton(
                    onClick = onSkip,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MeritColors.OutlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        text = "I'll pair later",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.Primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))
        }
    }
}

@Composable
private fun PairingTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MeritColors.SurfaceContainerLowest else Color.Transparent,
        shadowElevation = if (isSelected) 1.dp else 0.dp,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                ),
                color = if (isSelected) MeritColors.Primary else MeritColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QrCodePairingView(
    qrPayload: String,
    formattedCode: String,
    expiresLabel: String,
    onCopyCode: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
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
            Text(
                text = "Point child device camera at this code",
                style = MaterialTheme.typography.bodyMedium,
                color = MeritColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            if (qrPayload.isNotBlank()) {
                MeritQrCodeImage(
                    payload = qrPayload,
                    size = 200.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MeritColors.SurfaceContainerLow),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MeritColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.sm))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Position viewfinder over code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(MeritColors.SurfaceContainerHigh))
                Text(
                    text = "OR ENTER CODE MANUALLY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MeritColors.OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Box(modifier = Modifier.weight(1f).height(1.dp).background(MeritColors.SurfaceContainerHigh))
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Quick PIN Box
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MeritSpacing.md, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formattedCode,
                        style = MeritCodeLabelStyle,
                        color = MeritColors.OnSurface,
                    )

                    IconButton(
                        onClick = onCopyCode,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MeritColors.SurfaceContainerLowest),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Expiration pill & regenerate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MeritColors.SecondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MeritColors.OnSecondaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Expires in $expiresLabel",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MeritColors.OnSecondaryContainer,
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRegenerate)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Regenerate Code",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.Primary,
                    )
                }
            }
        }
    }
}

private fun formatOfferCountdown(expiresAtEpochMs: Long): String {
    val left = ((expiresAtEpochMs - System.currentTimeMillis()) / 1_000L).toInt().coerceAtLeast(0)
    return "%d:%02d".format(left / 60, left % 60)
}

@Composable
private fun SixDigitPinPairingView(
    formattedCode: String,
    codeRaw: String,
    childName: String,
    onCopyCode: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
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
            Text(
                text = "Type this 6-digit code on $childName's device",
                style = MaterialTheme.typography.bodyMedium,
                color = MeritColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(MeritSpacing.lg))

            // Oversized PIN Container
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MeritSpacing.lg, horizontal = MeritSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = formattedCode,
                        style = MeritPinDigitStyleLarge,
                        color = MeritColors.Primary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(MeritSpacing.sm))

                    Button(
                        onClick = onCopyCode,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeritColors.SurfaceContainerLowest,
                            contentColor = MeritColors.Primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy Code",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.Primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Pulsing status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MeritColors.Primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Waiting for child device to connect...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Expiration & regenerate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MeritColors.SecondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MeritColors.OnSecondaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Expires in 09:48",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MeritColors.OnSecondaryContainer,
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRegenerate)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Regenerate Code",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.Primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareLinkPairingView(
    inviteUrl: String,
    childName: String,
    onCopyLink: () -> Unit,
    onShareChannel: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MeritColors.SurfaceContainerLowest,
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeritSpacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MeritColors.PrimaryFixed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = "Secure Magic Link",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MeritColors.TertiaryFixed)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "Active • 24h Left",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.Tertiary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Open this link on $childName's phone or tablet to automatically install and assign their profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MeritColors.OnSurfaceVariant,
                lineHeight = 17.sp,
            )

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            // Link Box with Quick Copy
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = inviteUrl,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )

                    Button(
                        onClick = onCopyLink,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeritColors.PrimaryContainer,
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Copy", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            Spacer(modifier = Modifier.height(MeritSpacing.md))

            Text(
                text = "ONE-TAP DIRECT SHARE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MeritColors.OnSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2x2 Share Options Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DirectShareChannelButton(
                    icon = Icons.Default.Sms,
                    iconBg = Color(0xFF25D366).copy(alpha = 0.15f),
                    iconTint = Color(0xFF13522F),
                    title = "SMS / Text",
                    subtitle = "Send link to phone",
                    modifier = Modifier.weight(1f),
                ) {
                    onShareChannel("SMS")
                }

                DirectShareChannelButton(
                    icon = Icons.Default.Chat,
                    iconBg = MeritColors.TertiaryFixed,
                    iconTint = MeritColors.Tertiary,
                    title = "WhatsApp",
                    subtitle = "Direct chat invite",
                    modifier = Modifier.weight(1f),
                ) {
                    onShareChannel("WhatsApp")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DirectShareChannelButton(
                    icon = Icons.Default.NearMe,
                    iconBg = MeritColors.PrimaryFixed,
                    iconTint = MeritColors.Primary,
                    title = "Nearby Share",
                    subtitle = "Instant airbeam",
                    modifier = Modifier.weight(1f),
                ) {
                    onShareChannel("Nearby Share")
                }

                DirectShareChannelButton(
                    icon = Icons.Default.Mail,
                    iconBg = MeritColors.SecondaryContainer,
                    iconTint = MeritColors.OnSecondaryContainer,
                    title = "Email",
                    subtitle = "Send to yourself",
                    modifier = Modifier.weight(1f),
                ) {
                    onShareChannel("Email")
                }
            }
        }
    }
}

@Composable
private fun DirectShareChannelButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLow,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PairingInstructionRow(
    stepNumber: String,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MeritColors.PrimaryFixed),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MeritColors.Primary,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurface,
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MeritColors.OnSurfaceVariant,
            )
        }
    }
}


