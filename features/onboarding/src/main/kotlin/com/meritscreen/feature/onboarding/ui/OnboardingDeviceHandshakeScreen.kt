package com.meritscreen.feature.onboarding.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.ui.components.MeritQrCodeImage
import com.meritscreen.core.ui.theme.MeritCodeLabelStyle
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data class OnboardingDeviceHandshakeRoute(
    val childId: String = "",
)

@Composable
fun OnboardingDeviceHandshakeScreen(
    onPaired: () -> Unit = {},
    onSkipToDashboard: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: OnboardingDeviceHandshakeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var copyFeedback by remember { mutableStateOf(false) }

    val pairPhase = state.phase
    val scanSucceeded = pairPhase == HandshakePairPhase.Succeeded
    val isSearching = pairPhase == HandshakePairPhase.Searching
    val childName = state.childName
    val pairingCode = state.offer?.code.orEmpty()
    val qrPayload = state.offer?.qrPayload.orEmpty()
    val formattedCode = remember(pairingCode) {
        if (pairingCode.length >= 6) {
            "${pairingCode.take(3)} ${pairingCode.drop(3)}"
        } else {
            pairingCode
        }
    }
    val secondsLeft = state.secondsLeft

    LaunchedEffect(pairPhase) {
        when (pairPhase) {
            HandshakePairPhase.Succeeded -> {
                delay(1_600)
                viewModel.consumeSuccessNavigation(onPaired)
            }
            else -> Unit
        }
    }

    LaunchedEffect(copyFeedback) {
        if (!copyFeedback) return@LaunchedEffect
        delay(1_600)
        copyFeedback = false
    }

    if (pairPhase == HandshakePairPhase.Loading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MeritColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MeritColors.Primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Creating a secure pairing code…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeritColors.Surface)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MeritSpacing.sm, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onBack,
                enabled = isSearching,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MeritColors.SurfaceContainer),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MeritColors.OnSurface,
                )
            }
            Text(
                text = "Device pairing",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurface,
            )
            TextButton(
                onClick = { viewModel.skipPairing(onSkipToDashboard) },
                enabled = isSearching,
            ) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.Primary,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MeritSpacing.margin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MeritColors.SecondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Step 3 of 5 • Device Handshake",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSecondaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Show this QR to $childName's device",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "On the child tablet, tap Scan Parent QR — or type the 6-digit code below. This screen advances automatically once the device is linked.",
                style = MaterialTheme.typography.bodyMedium,
                color = MeritColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.error != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MeritColors.ErrorContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = state.error!!.userMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnErrorContainer,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = viewModel::refreshCode,
                            enabled = !state.isRefreshing,
                        ) {
                            Text(
                                text = if (state.isRefreshing) "Refreshing…" else "Refresh code",
                                color = MeritColors.Primary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (scanSucceeded) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MeritColors.Tertiary.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MeritColors.Tertiary,
                            modifier = Modifier.size(22.dp),
                        )
                        Column {
                            Text(
                                text = "Device paired successfully",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MeritColors.OnSurface,
                            )
                            Text(
                                text = "Connected to $childName's device — continuing…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Primary: real pairing QR (child scans this)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MeritColors.SurfaceContainerLowest,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Pairing QR",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    if (qrPayload.isNotBlank() && !scanSucceeded) {
                        MeritQrCodeImage(
                            payload = qrPayload,
                            size = 220.dp,
                        )
                    } else if (scanSucceeded) {
                        Box(
                            modifier = Modifier.size(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MeritColors.Tertiary,
                                modifier = Modifier.size(72.dp),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MeritColors.Primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Expires in ${formatCountdown(secondsLeft)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.Primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isSearching) {
                TextButton(
                    onClick = viewModel::simulateSuccessfulPair,
                    enabled = state.offer != null,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MeritColors.OnSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "Quick Demo: Pair Device Instantly",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSecondaryContainer,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MeritColors.SurfaceContainerLowest,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Or pair with a 6-digit code",
                        style = MaterialTheme.typography.labelMedium,
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Type this code on the child's device under Pairing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MeritColors.SurfaceContainerLow)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (formattedCode.isNotBlank()) formattedCode else "••••••",
                            style = MeritCodeLabelStyle,
                            color = MeritColors.Primary,
                        )
                        Button(
                            onClick = {
                                if (pairingCode.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(pairingCode))
                                    copyFeedback = true
                                }
                            },
                            enabled = isSearching && pairingCode.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeritColors.SurfaceContainerHighest,
                                contentColor = MeritColors.OnSurface,
                            ),
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = if (copyFeedback) "Copied" else "Copy",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isSearching) {
                UtilityActionButton(
                    label = "Help pairing",
                    icon = Icons.Default.HelpOutline,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { /* help sheet later */ },
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "The QR code securely links your family devices. No personal photos or data are uploaded — pairing connects directly through Watching's secure family connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UtilityActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainer,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MeritColors.OnSurface,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurface,
            )
        }
    }
}

private fun formatCountdown(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%d:%02d".format(minutes, seconds)
}
