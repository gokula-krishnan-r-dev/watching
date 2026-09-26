package com.meritscreen.feature.onboarding.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import com.meritscreen.core.ui.theme.headlineLargeMobile
import kotlinx.serialization.Serializable

/**
 * S01 / P01 — Google Stitch UI Kit v3 ("Calm Horizon"):
 * Primary welcome & role gate (S01). Presents MeritScreen's value proposition
 * with dedicated paths for parents ("Get Started as Parent") and child setup ("Set Up Child Device").
 * This is the only first-run welcome — the former standalone P01 Welcome screen was removed.
 */
@Serializable
data object RoleSelectRoute

@Composable
fun RoleSelectScreen(
    onParentChosen: () -> Unit,
    onChildChosen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoleSelectContent(
        onParentChosen = onParentChosen,
        onChildChosen = onChildChosen,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleSelectContent(
    onParentChosen: () -> Unit,
    onChildChosen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dialog & Bottom Sheet state for enhanced interactivity
    var showHelpDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var legalSheetTopic by remember { mutableStateOf<LegalSheetType?>(null) }
    var selectedLanguage by remember { mutableStateOf("English (US)") }

    val scrollState = rememberScrollState()
    val glowSecondary = MeritColors.SecondaryContainer.copy(alpha = 0.35f)
    val glowPrimary = MeritColors.PrimaryFixed.copy(alpha = 0.22f)

    Surface(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Subtle ambient decorative glow (mineral & paper tones) as per Stitch v3 specification
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowSecondary,
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.92f, size.height * 0.05f),
                        radius = size.width * 0.65f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowPrimary,
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.02f, size.height * 0.32f),
                        radius = size.width * 0.60f,
                    ),
                )
            },
        color = MeritColors.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .padding(horizontal = MeritSpacing.md, vertical = MeritSpacing.xs)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // --- 1. TOP BAR: Mode Indicator & Action Icons ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // "Parent Guardian Portal" Mode Indicator Pill
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MeritColors.SecondaryContainer.copy(alpha = 0.55f))
                        .border(
                            BorderStroke(1.dp, MeritColors.SecondaryContainer),
                            CircleShape,
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "Parent Guardian Portal",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            letterSpacing = 0.3.sp,
                        ),
                        color = MeritColors.OnSecondaryContainer,
                    )
                }

                // Help & Language Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help & FAQ",
                            tint = MeritColors.OnSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- 2. BRAND-FIRST HERO CENTER ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Official Watching Shield & Star Logo Card
                Box(
                    modifier = Modifier.padding(bottom = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Soft background ambient halo
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(MeritColors.PrimaryFixed.copy(alpha = 0.35f)),
                    )

                    // Card emblem
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFFFEFBF6),
                        border = BorderStroke(1.dp, MeritColors.SurfaceVariant.copy(alpha = 0.8f)),
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(76.dp),
                    ) {
                        Image(
                            painter = painterResource(id = com.meritscreen.core.ui.R.drawable.ic_watching_logo),
                            contentDescription = "Watching Shield Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                // Category Pill Badge
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MeritColors.SurfaceContainerHigh)
                        .border(
                            BorderStroke(1.dp, MeritColors.SurfaceVariant.copy(alpha = 0.8f)),
                            CircleShape,
                        )
                        .padding(horizontal = 10.dp, vertical = 3.5.dp),
                ) {
                    Text(
                        text = "PARENTAL CONTROL & MICRO-LEARNING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            letterSpacing = 0.8.sp,
                        ),
                        color = MeritColors.Secondary,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Brand Headline
                Text(
                    text = "Watching",
                    style = MaterialTheme.typography.headlineLargeMobile.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = MeritColors.OnSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Conversational Tagline
                Text(
                    text = "Turn screen time battles into curious minds.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = MeritColors.OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = MeritSpacing.sm),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // --- 3. VALUE PROPOSITION CARD: 3 CORE PILLARS ---
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MeritColors.SurfaceContainerLow,
                    border = BorderStroke(1.dp, MeritColors.SurfaceVariant.copy(alpha = 0.8f)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Pillar 1: Micro-Learning Gate
                        StitchPillarRow(
                            icon = Icons.Default.Quiz,
                            iconBg = MeritColors.PrimaryContainer.copy(alpha = 0.12f),
                            iconTint = MeritColors.PrimaryContainer,
                            title = "Micro-Learning Gate",
                            description = "Kids earn app minutes by solving bite-sized challenges",
                        )

                        HorizontalDivider(
                            color = MeritColors.SurfaceVariant.copy(alpha = 0.6f),
                            thickness = 1.dp,
                        )

                        // Pillar 2: Loophole-Free Shield
                        StitchPillarRow(
                            icon = Icons.Default.LockClock,
                            iconBg = MeritColors.Tertiary.copy(alpha = 0.12f),
                            iconTint = MeritColors.Tertiary,
                            title = "Loophole-Free Shield",
                            description = "Tamper-proof kiosk launcher with instant remote freeze",
                        )

                        HorizontalDivider(
                            color = MeritColors.SurfaceVariant.copy(alpha = 0.6f),
                            thickness = 1.dp,
                        )

                        // Pillar 3: Gentle Bedtime Rest
                        StitchPillarRow(
                            icon = Icons.Default.Bedtime,
                            iconBg = MeritColors.Secondary.copy(alpha = 0.12f),
                            iconTint = MeritColors.Secondary,
                            title = "Gentle Bedtime Rest",
                            description = "Automated soothing bedtime schedule without conflict",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- 4. TRUST ASSURANCES ROW ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = MeritColors.Tertiary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "Zero Ads",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MeritColors.OnSurfaceVariant.copy(alpha = 0.85f),
                        )
                    }

                    Text(
                        text = "  •  ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MeritColors.OutlineVariant,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "Encrypted",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MeritColors.OnSurfaceVariant.copy(alpha = 0.85f),
                        )
                    }

                    Text(
                        text = "  •  ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MeritColors.OutlineVariant,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MeritColors.Secondary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "COPPA Compliant",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MeritColors.OnSurfaceVariant.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- 5. BOTTOM ACTIONS AREA ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Action 1: Primary High-Emphasis Button ("Get Started as Parent")
                val parentInteraction = remember { MutableInteractionSource() }
                val isParentPressed by parentInteraction.collectIsPressedAsState()
                val parentScale by animateFloatAsState(
                    targetValue = if (isParentPressed) 0.985f else 1f,
                    label = "parent_btn_scale",
                )

                Button(
                    onClick = onParentChosen,
                    interactionSource = parentInteraction,
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeritColors.PrimaryContainer,
                        contentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 4.dp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .scale(parentScale)
                        .testTag("role_select_parent"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Get Started as Parent",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                letterSpacing = 0.2.sp,
                            ),
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

                // Action 2: Secondary Device Setup Card ("Set Up Child Device")
                val childInteraction = remember { MutableInteractionSource() }
                val isChildPressed by childInteraction.collectIsPressedAsState()
                val childScale by animateFloatAsState(
                    targetValue = if (isChildPressed) 0.985f else 1f,
                    label = "child_card_scale",
                )

                Surface(
                    onClick = onChildChosen,
                    interactionSource = childInteraction,
                    shape = RoundedCornerShape(22.dp),
                    color = MeritColors.PrimaryFixed.copy(alpha = 0.32f),
                    border = BorderStroke(1.dp, MeritColors.Primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(childScale)
                        .testTag("role_select_child"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            // Left devices icon in white tile
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MeritColors.SurfaceContainerLowest)
                                    .border(
                                        BorderStroke(1.dp, MeritColors.Primary.copy(alpha = 0.1f)),
                                        RoundedCornerShape(12.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = MeritColors.Primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Middle title, badge & subtitle
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "Set Up Child Device",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp,
                                        ),
                                        color = MeritColors.PrimaryContainer,
                                    )

                                    // "KID'S DEVICE" tag pill
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MeritColors.SurfaceContainerLowest)
                                            .border(
                                                BorderStroke(1.dp, MeritColors.Primary.copy(alpha = 0.15f)),
                                                CircleShape,
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = "KID'S DEVICE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp,
                                                letterSpacing = 0.5.sp,
                                            ),
                                            color = MeritColors.Primary,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(1.dp))

                                Text(
                                    text = "Pair tablet or phone to start learning quests",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                    ),
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            }
                        }

                        // Right QR Scanner button icon
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MeritColors.SurfaceContainerLowest.copy(alpha = 0.85f))
                                .border(
                                    BorderStroke(1.dp, MeritColors.Primary.copy(alpha = 0.12f)),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Pairing QR",
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Legal Terms & Privacy Guarantee Links (wrapped to two lines if needed)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // First line of legal consent
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "By continuing you agree to ",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            ),
                            color = MeritColors.OnSurfaceVariant.copy(alpha = 0.75f),
                        )
                        Text(
                            text = "Terms of Service",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline,
                            ),
                            color = MeritColors.OnSurfaceVariant,
                            modifier = Modifier.clickable { legalSheetTopic = LegalSheetType.TERMS },
                        )
                        Text(
                            text = " &",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            ),
                            color = MeritColors.OnSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                    // Second line wraps automatically if overflow
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Family Privacy Guarantee",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline,
                            ),
                            color = MeritColors.OnSurfaceVariant,
                            modifier = Modifier.clickable { legalSheetTopic = LegalSheetType.PRIVACY },
                        )
                        Text(
                            text = ".",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MeritColors.OnSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }
  
            }
        }
    }

    // --- Interactive Help & FAQ Dialog ---
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                    )
                    Text(
                        text = "Watching Guide",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "• Parent Mode: Manage schedules, time allowances, and approve apps directly from your personal device.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    )
                    Text(
                        text = "• Child Device Mode: Configures this phone or tablet with a peaceful kiosk launcher where extra screen time is earned via curiosity quests.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    )
                    Text(
                        text = "• Tamper-Proof & Safe: Backed by device administrator controls, on-device encryption, and zero advertising.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it", color = MeritColors.Primary)
                }
            },
        )
    }

    // --- Interactive Legal / Privacy Bottom Sheet ---
    if (legalSheetTopic != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { legalSheetTopic = null },
            sheetState = sheetState,
            containerColor = MeritColors.SurfaceContainerLowest,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
            ) {
                Text(
                    text = if (legalSheetTopic == LegalSheetType.TERMS) "Terms of Service" else "Family Privacy Guarantee",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (legalSheetTopic == LegalSheetType.TERMS) {
                        "Watching is designed for parents and legal guardians to encourage mindful technology usage for children. By utilizing our services, you confirm that you are the parent or authorized guardian of any managed child profile. Our tamper-resistant launcher is designed solely for healthy boundaries and learning reinforcement."
                    } else {
                        "We believe child privacy is sacred. Watching does not track children for behavioral advertising, does not sell telemetry or learning data to third parties, and is strictly built in compliance with COPPA and global child protection standards. All encryption keys are held on-device."
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MeritColors.OnSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { legalSheetTopic = null },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeritColors.PrimaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

/**
 * Feature highlight item row for the value proposition card.
 */
@Composable
private fun StitchPillarRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                ),
                color = MeritColors.OnSurface,
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                ),
                color = MeritColors.OnSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

private enum class LegalSheetType {
    TERMS,
    PRIVACY,
}
