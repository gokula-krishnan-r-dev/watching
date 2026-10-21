package com.meritscreen.feature.child.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.meritscreen.core.common.device.DefaultHomeChecker
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import com.meritscreen.feature.child.service.ForegroundAppDetector
import com.meritscreen.feature.launcher.HomeRoleManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ChildPermissionStep(val stepNumber: Int, val title: String) {
    UsageAccess(1, "Usage Access"),
    Overlay(2, "Quiz Popup"),
    DefaultHome(3, "Safe Home"),
}

@Composable
fun ChildPermissionsOnboardingScreen(
    onAllPermissionsGranted: () -> Unit,
    onOpenPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var usageGranted by remember { mutableStateOf(ForegroundAppDetector.hasUsageStatsPermission(context)) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var homeGranted by remember { mutableStateOf(DefaultHomeChecker.isDefaultHome(context)) }

    fun isStepGranted(step: ChildPermissionStep): Boolean = when (step) {
        ChildPermissionStep.UsageAccess -> usageGranted
        ChildPermissionStep.Overlay -> overlayGranted
        ChildPermissionStep.DefaultHome -> homeGranted
    }

    // Determine the first ungranted step
    fun determineFirstUngrantedStep(): ChildPermissionStep {
        return when {
            !usageGranted -> ChildPermissionStep.UsageAccess
            !overlayGranted -> ChildPermissionStep.Overlay
            !homeGranted -> ChildPermissionStep.DefaultHome
            else -> ChildPermissionStep.DefaultHome
        }
    }

    var currentStep by remember { mutableStateOf(determineFirstUngrantedStep()) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var notGrantedNotice by remember { mutableStateOf<String?>(null) }
    var isAutoNavigating by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        val prevUsage = usageGranted
        val prevOverlay = overlayGranted
        val prevHome = homeGranted

        usageGranted = ForegroundAppDetector.hasUsageStatsPermission(context)
        overlayGranted = Settings.canDrawOverlays(context)
        homeGranted = DefaultHomeChecker.isDefaultHome(context)

        val newlyGranted = when (currentStep) {
            ChildPermissionStep.UsageAccess -> !prevUsage && usageGranted
            ChildPermissionStep.Overlay -> !prevOverlay && overlayGranted
            ChildPermissionStep.DefaultHome -> !prevHome && homeGranted
        }

        if (usageGranted && overlayGranted && homeGranted) {
            notGrantedNotice = null
            successMessage = "All permissions granted! Safe Mode is now active."
            if (!isAutoNavigating) {
                isAutoNavigating = true
                scope.launch {
                    delay(1_100)
                    onAllPermissionsGranted()
                }
            }
        } else if (newlyGranted) {
            notGrantedNotice = null
            successMessage = "${currentStep.title} granted successfully!"
            if (!isAutoNavigating) {
                isAutoNavigating = true
                scope.launch {
                    delay(1_000)
                    isAutoNavigating = false
                    successMessage = null
                    currentStep = determineFirstUngrantedStep()
                }
            }
        } else if (!isStepGranted(currentStep)) {
            // Keep error/retry message clear
            notGrantedNotice = "Permission not detected yet. Please tap below and turn it on in Settings."
        }
    }

    // Role setup activity result launcher for Step 3
    val homeSetupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshPermissions()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Check on initial entry if all permissions are already active
    LaunchedEffect(Unit) {
        if (usageGranted && overlayGranted && homeGranted) {
            onAllPermissionsGranted()
        }
    }

    fun openNativeSettingsForCurrentStep() {
        notGrantedNotice = null
        when (currentStep) {
            ChildPermissionStep.UsageAccess -> {
                val usageIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val packageSpecificIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching {
                    if (context.packageManager.resolveActivity(packageSpecificIntent, 0) != null) {
                        context.startActivity(packageSpecificIntent)
                    } else {
                        context.startActivity(usageIntent)
                    }
                }.onFailure {
                    runCatching { context.startActivity(usageIntent) }
                }
            }
            ChildPermissionStep.Overlay -> {
                val overlayIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching {
                    context.startActivity(overlayIntent)
                }.onFailure {
                    val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(fallback) }
                }
            }
            ChildPermissionStep.DefaultHome -> {
                HomeRoleManager.enableHomeLauncher(context)
                val intent = HomeRoleManager.createSetupIntent(context)
                try {
                    homeSetupLauncher.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    val fallback = HomeRoleManager.homeSettingsIntent()
                        ?: HomeRoleManager.legacyHomeChooserIntent(context)
                    if (context is Activity) {
                        homeSetupLauncher.launch(fallback)
                    } else {
                        context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } catch (_: SecurityException) {
                    notGrantedNotice = "Android blocked the Home request. Open Settings to set Watching as Home manually."
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(MeritColors.Background),
        color = MeritColors.Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MeritSpacing.lg, vertical = MeritSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Bar: Brand, Onboarding indicator, and Parent PIN Lock gate
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MeritSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MeritColors.Primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Safe Mode Setup",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnBackground,
                        )
                        Text(
                            text = "Step ${currentStep.stepNumber} of 3",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }

                IconButton(
                    onClick = onOpenPin,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MeritColors.SurfaceContainer),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Parent PIN Unlock",
                        tint = MeritColors.OnSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(MeritSpacing.lg))

            // Step Progress Indicator
            StepProgressHeader(
                currentStep = currentStep,
                usageGranted = usageGranted,
                overlayGranted = overlayGranted,
                homeGranted = homeGranted,
                onStepClicked = { clickedStep ->
                    if (isStepGranted(clickedStep) || clickedStep.stepNumber <= currentStep.stepNumber) {
                        currentStep = clickedStep
                        notGrantedNotice = null
                    }
                },
            )

            Spacer(Modifier.height(MeritSpacing.xl))

            // Animated Step Card Content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState.stepNumber > initialState.stepNumber) {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                    }
                },
                label = "ChildPermissionOnboardingStep",
            ) { step ->
                StepCardContent(
                    step = step,
                    isGranted = isStepGranted(step),
                    onEnableClicked = { openNativeSettingsForCurrentStep() },
                )
            }

            Spacer(Modifier.height(MeritSpacing.lg))

            // Feedback: Success Message Banner
            AnimatedVisibility(visible = successMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MeritColors.Tertiary.copy(alpha = 0.14f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.Tertiary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MeritSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MeritColors.Tertiary,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = successMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.Tertiary,
                        )
                    }
                }
            }

            // Feedback: Not Granted Notice Banner
            AnimatedVisibility(visible = notGrantedNotice != null && !isStepGranted(currentStep)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MeritSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = notGrantedNotice.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f, fill = false))
            Spacer(Modifier.height(MeritSpacing.md))

            // Bottom Primary Action Button
            val isCurrentGranted = isStepGranted(currentStep)
            Button(
                onClick = {
                    if (isCurrentGranted) {
                        val next = determineFirstUngrantedStep()
                        if (next == currentStep && usageGranted && overlayGranted && homeGranted) {
                            onAllPermissionsGranted()
                        } else {
                            currentStep = next
                        }
                    } else {
                        openNativeSettingsForCurrentStep()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrentGranted) MeritColors.Tertiary else MeritColors.Primary,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (isAutoNavigating) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Validating...",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                } else {
                    Icon(
                        imageVector = if (isCurrentGranted) Icons.Default.Check else actionButtonIcon(currentStep),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isCurrentGranted) "Next Permission" else actionButtonText(currentStep),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }

            Spacer(Modifier.height(MeritSpacing.sm))

            Text(
                text = "All 3 permissions are required so parental safety and daily limits remain active.",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MeritColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = MeritSpacing.sm),
            )
        }
    }
}

@Composable
private fun StepProgressHeader(
    currentStep: ChildPermissionStep,
    usageGranted: Boolean,
    overlayGranted: Boolean,
    homeGranted: Boolean,
    onStepClicked: (ChildPermissionStep) -> Unit,
) {
    val steps = ChildPermissionStep.entries

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        steps.forEachIndexed { index, step ->
            val granted = when (step) {
                ChildPermissionStep.UsageAccess -> usageGranted
                ChildPermissionStep.Overlay -> overlayGranted
                ChildPermissionStep.DefaultHome -> homeGranted
            }
            val isCurrent = step == currentStep

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = index < steps.lastIndex),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onStepClicked(step) },
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    granted -> MeritColors.Tertiary
                                    isCurrent -> MeritColors.Primary
                                    else -> MeritColors.SurfaceContainer
                                }
                            )
                            .border(
                                width = if (isCurrent) 2.dp else 0.dp,
                                color = if (isCurrent) MeritColors.Primary.copy(alpha = 0.4f) else Color.Transparent,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (granted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text(
                                text = "${step.stepNumber}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isCurrent) Color.White else MeritColors.OnSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = when {
                            granted -> MeritColors.Tertiary
                            isCurrent -> MeritColors.Primary
                            else -> MeritColors.OnSurfaceVariant
                        },
                    )
                }

                if (index < steps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                if (granted) MeritColors.Tertiary.copy(alpha = 0.6f)
                                else MeritColors.SurfaceContainer
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCardContent(
    step: ChildPermissionStep,
    isGranted: Boolean,
    onEnableClicked: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hero Icon
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) MeritColors.Tertiary.copy(alpha = 0.15f)
                        else MeritColors.Primary.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (step) {
                        ChildPermissionStep.UsageAccess -> Icons.Default.HourglassTop
                        ChildPermissionStep.Overlay -> Icons.Default.Bolt
                        ChildPermissionStep.DefaultHome -> Icons.Default.Home
                    },
                    contentDescription = null,
                    tint = if (isGranted) MeritColors.Tertiary else MeritColors.Primary,
                    modifier = Modifier.size(34.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = stepHeading(step),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                color = MeritColors.OnSurface,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stepDescription(step),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                textAlign = TextAlign.Center,
                color = MeritColors.OnSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))

            // Step Instructions Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MeritColors.Surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Quick Instructions:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )
                    stepInstructions(step).forEachIndexed { i, instruction ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MeritColors.Primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${i + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = MeritColors.Primary,
                                )
                            }
                            Text(
                                text = instruction,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun stepHeading(step: ChildPermissionStep): String = when (step) {
    ChildPermissionStep.UsageAccess -> "Enable App Usage Access"
    ChildPermissionStep.Overlay -> "Allow Quiz & Interruption Popups"
    ChildPermissionStep.DefaultHome -> "Set Watching as Home Launcher"
}

private fun stepDescription(step: ChildPermissionStep): String = when (step) {
    ChildPermissionStep.UsageAccess ->
        "Watching needs Usage Access to accurately track screen time, pause games at bedtime, and award learning minutes."
    ChildPermissionStep.Overlay ->
        "Allows educational quizzes, reward unlock cards, and daily screen limit alerts to show up over games and videos."
    ChildPermissionStep.DefaultHome ->
        "Prevents bypassing screen limits and keeps approved playground apps safe and locked to your child's home screen."
}

private fun stepInstructions(step: ChildPermissionStep): List<String> = when (step) {
    ChildPermissionStep.UsageAccess -> listOf(
        "Tap the button below to open Android Usage Access Settings.",
        "Locate Watching in the list of applications.",
        "Toggle ON 'Permit usage access' and return to this app.",
    )
    ChildPermissionStep.Overlay -> listOf(
        "Tap the button below to open Display Over Apps Settings.",
        "Turn ON 'Allow display over other apps' for Watching.",
        "Press Back to return to this screen.",
    )
    ChildPermissionStep.DefaultHome -> listOf(
        "Tap the button below to trigger the system Home dialog.",
        "Select Watching and tap 'Always' as your default home app.",
        "Safe mode is activated and protection setup is complete!",
    )
}

private fun actionButtonText(step: ChildPermissionStep): String = when (step) {
    ChildPermissionStep.UsageAccess -> "Enable Usage Access"
    ChildPermissionStep.Overlay -> "Allow Quiz Popup"
    ChildPermissionStep.DefaultHome -> "Set as Home App"
}

private fun actionButtonIcon(step: ChildPermissionStep): ImageVector = when (step) {
    ChildPermissionStep.UsageAccess -> Icons.Default.HourglassTop
    ChildPermissionStep.Overlay -> Icons.Default.Bolt
    ChildPermissionStep.DefaultHome -> Icons.Default.Home
}
