package com.meritscreen.feature.child.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import com.meritscreen.core.common.domain.LearningResourceVideo
import com.meritscreen.feature.child.ui.LearningResourceModal
import com.meritscreen.feature.child.ui.FullscreenResourceVideoPlayer
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.feature.child.domain.MiniLesson
import com.meritscreen.feature.child.domain.QuizAnswerFeedback
import com.meritscreen.feature.child.domain.QuizChoice
import com.meritscreen.feature.child.domain.QuizQuestion
import com.meritscreen.feature.child.domain.QuizSessionResult
import com.meritscreen.feature.child.domain.VisualTaxonomy
import kotlin.random.Random

@Composable
fun ChildQuizScreen(
    onFinished: () -> Unit,
    onOpenPin: () -> Unit,
    onContinueToApp: (() -> Unit)? = null,
    onGoHome: () -> Unit = onFinished,
    viewModel: ChildQuizViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tts = remember { QuizTtsNarrator(context) }

    DisposableEffect(Unit) {
        onDispose {
            tts.shutdown()
        }
    }

    when (val current = state) {
        UiState.Loading -> LoadingState(message = "Getting your quiz ready")
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(message = current.error.userMessage, onRetry = onFinished)
        is UiState.Success -> {
            val data = current.data
            when (val step = data.step) {
                QuizUiStep.Intro -> {
                    QuizInterruptPane(
                        appLabel = data.appLabel,
                        childName = data.childName,
                        onStartQuiz = viewModel::beginQuestions,
                        onCloseApp = onFinished,
                    )
                }

                is QuizUiStep.NurseryVideo -> {
                    NurseryVideoPlayer(
                        video = step.video,
                        childName = data.childName,
                        onVideoFinished = viewModel::onNurseryComplete,
                        onVideoError = { _ -> viewModel.onNurseryVideoError(step.video.videoId) },
                        onParentDismiss = onFinished,
                    )
                }

                is QuizUiStep.NurseryTeach -> {
                    NurseryTeachPane(
                        items = step.items,
                        childName = data.childName,
                        tts = tts,
                        onComplete = viewModel::onNurseryComplete,
                    )
                }

                is QuizUiStep.Completed -> {
                    LaunchedEffect(step.targetPackage, step.passed) {
                        if (!step.passed) {
                            onFinished()
                        } else if (onContinueToApp != null) {
                            onContinueToApp.invoke()
                        } else {
                            val intent = step.targetPackage
                                ?.takeIf { it != context.packageName }
                                ?.let(context.packageManager::getLaunchIntentForPackage)
                            if (intent != null) {
                                intent.addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                                )
                                runCatching { context.startActivity(intent) }
                                    .onFailure { onFinished() }
                            } else {
                                onFinished()
                            }
                        }
                    }
                }

                is QuizUiStep.Question -> {
                    QuizQuestionPane(
                        step = step,
                        childName = data.childName,
                        appLabel = data.appLabel,
                        tts = tts,
                        onAnswerSelected = viewModel::answer,
                        onProceedNext = viewModel::proceedToNextQuestion,
                        onOpenResourceModal = viewModel::openResourceModal,
                        onCloseResourceModal = viewModel::closeResourceModal,
                        onPlayVideo = viewModel::playResourceVideo,
                        onCloseVideo = viewModel::closeResourceVideo,
                    )
                }

                is QuizUiStep.Feedback -> {
                    AnswerTeachPane(
                        step = step,
                        childName = data.childName,
                        appLabel = data.appLabel,
                        onNext = viewModel::continueAfterFeedback,
                    )
                }

                is QuizUiStep.LockedExplanation -> {
                    LockedExplanationPane(
                        step = step,
                        childName = data.childName,
                        tts = tts,
                        onContinue = viewModel::continueAfterFeedback,
                    )
                }

                is QuizUiStep.Result -> {
                    if (step.result.passed) {
                        LaunchedEffect(Unit) {
                            onFinished()
                        }
                    } else {
                        val context = LocalContext.current
                        FailLockCooldownPane(
                            childName = data.childName,
                            secondsRemaining = step.cooldownSecondsRemaining,
                            totalSeconds = step.cooldownTotalSeconds,
                            endsAtEpochMs = step.cooldownEndsAtEpochMs,
                            onCooldownFinished = onFinished,
                            onCallMom = { launchQuizDialer(context) },
                            onCallDad = { launchQuizDialer(context) },
                            onOpenPin = onOpenPin,
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Screen C07b • Quiz Interrupt
// -----------------------------------------------------------------------------
@Composable
fun QuizInterruptPane(
    appLabel: String,
    childName: String,
    onStartQuiz: () -> Unit,
    onCloseApp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeritColors.SurfaceContainerLow)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Meta Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MeritColors.SurfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MeritColors.Primary, CircleShape),
                    )
                    Text(
                        text = "C07b • Quiz Interrupt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.OnSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MeritColors.SecondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MeritColors.OnSecondaryContainer,
                    )
                    Text(
                        text = "Watching Assist",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.OnSecondaryContainer,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Ambient Paused App Context Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MeritColors.SurfaceContainer,
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MeritColors.Error.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = MeritColors.Error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Active App Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                        Text(
                            text = "$appLabel • Session Complete",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MeritColors.OnSurface,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeritColors.SurfaceContainerHighest,
                ) {
                    Text(
                        text = "30m Cap",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.OnSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Primary Soft Interrupt Canvas
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MeritColors.SurfaceContainerLowest,
            shadowElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Tactile Hero Illustration
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .background(MeritColors.SecondaryContainer.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .rotate(3f)
                            .background(MeritColors.PrimaryFixed, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MeritColors.Primary,
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 4.dp, bottom = 4.dp),
                        shape = CircleShape,
                        color = MeritColors.TertiaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "30m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MeritColors.OnTertiary,
                                fontWeight = FontWeight.Bold,
                            )
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MeritColors.OnTertiary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "GENTLE CHECK-IN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.Primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Time’s up for $appLabel! ⏳",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MeritColors.OnSurface,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "You finished your session with flying colors! Answer 3 quick fun questions to unlock 30 more minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))

                // Bento Split Cards: Quest Goal & Unlock Perk
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MeritColors.SurfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MeritColors.PrimaryFixed.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Quest Goal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                                Text(
                                    text = "3 Fun Steps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.Primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = "Grade 3 Math & Nature Quiz",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MeritColors.OnSurface,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MeritColors.SecondaryContainer.copy(alpha = 0.45f),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MeritColors.SecondaryContainer, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MeritColors.OnSecondaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Unlock Perk",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.OnSecondaryContainer,
                                )
                                Text(
                                    text = "+ Instant",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.Tertiary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = "+30 min bonus + 1 Explorer Sticker 🌟",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MeritColors.OnSurface,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Micro Challenge Tags
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MicroTag(icon = Icons.Default.Spa, label = "Math & Logic", tint = MeritColors.Tertiary)
                    MicroTag(icon = Icons.Default.Psychology, label = "Brain Warmup", tint = MeritColors.Primary)
                    MicroTag(icon = Icons.Default.Timer, label = "~90 secs", tint = MeritColors.Secondary)
                }

                Spacer(Modifier.height(20.dp))

                // Oversized Action Suite
                Button(
                    onClick = onStartQuiz,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeritColors.Primary),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Start Quick Quiz 🚀",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MeritColors.OnPrimary,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MeritColors.OnPrimary,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onCloseApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MeritColors.OnSurfaceVariant),
                ) {
                    Text(
                        text = "Take a break & close $appLabel",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Safety & Parent Assurance Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MeritColors.SurfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MeritColors.TertiaryFixed, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MeritColors.OnTertiaryFixedVariant,
                    )
                }
                Column {
                    Text(
                        text = "Always safe & reachable",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = "Emergency calls to Mom & Dad remain available at all times.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Screen C09 • Quiz Question
// -----------------------------------------------------------------------------

/** Debug-only chip so we can tell AI packs from the builtin bank on device. */
@Composable
private fun QuizQuestionSourceDebugBadge(source: String) {
    val context = LocalContext.current
    val debuggable =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!debuggable) return

    val isAi = source.equals("ai", ignoreCase = true)
    Spacer(Modifier.height(10.dp))
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isAi) {
            MeritColors.PrimaryContainer
        } else {
            MeritColors.SurfaceContainerHigh
        },
    ) {
        Text(
            text = if (isAi) "Source: AI (Gemini)" else "Source: Static (builtin)",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isAi) MeritColors.OnPrimaryContainer else MeritColors.OnSurfaceVariant,
        )
    }
}

@Composable
fun QuizQuestionPane(
    step: QuizUiStep.Question,
    childName: String,
    appLabel: String,
    tts: QuizTtsNarrator,
    onAnswerSelected: (String) -> Unit,
    onProceedNext: () -> Unit = {},
    onOpenResourceModal: () -> Unit = {},
    onCloseResourceModal: () -> Unit = {},
    onPlayVideo: (LearningResourceVideo) -> Unit = {},
    onCloseVideo: () -> Unit = {},
) {
    val question = step.question
    var selectedChoiceId by remember(question.id) { mutableStateOf<String?>(null) }
    var hintVisible by remember(question.id) { mutableStateOf(false) }

    // Auto-narrate prompt for Early Learner (Ages 3-6)
    LaunchedEffect(question.id) {
        if (question.ageBand == AgeBand.AGE_3_TO_6 || question.interactionType != "TAP_TEXT") {
            tts.speak(question.prompt)
        }
    }

    // Auto-narrate explanation when answered incorrectly
    LaunchedEffect(step.evaluatedChoiceId) {
        if (step.isCorrect == false && !step.whyWrongText.isNullOrBlank()) {
            tts.speak("Not quite. ${step.whyWrongText}. The correct answer is ${step.correctChoiceText}.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeritColors.SurfaceContainerLow)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Compact header: progress + concept + reward
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MeritColors.SurfaceContainerLowest,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "${step.index}/${step.total}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.Primary,
                    )
                    Text(
                        text = question.conceptTitle.ifBlank { "Quiz" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MeritColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Listen to question",
                        tint = MeritColors.Primary,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { tts.speak(question.prompt) }
                            .padding(4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (i in 1..step.total) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(
                                    color = when {
                                        i < step.index -> MeritColors.Primary
                                        i == step.index -> MeritColors.Primary
                                        else -> MeritColors.SurfaceContainerHigh
                                    },
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MeritColors.OnSecondaryContainer,
                    )
                    Text(
                        text = "+30 min $appLabel on pass",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MeritColors.OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Question Prompt Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MeritColors.SurfaceContainerLowest,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MeritColors.SurfaceContainer,
                ) {
                    Text(
                        text = question.topic.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MeritColors.OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = question.prompt,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MeritColors.OnSurface,
                    lineHeight = 24.sp,
                )

                if (question.interactionType == "COUNT_AND_TAP" && question.promptTag != null) {
                    Spacer(Modifier.height(12.dp))
                    EarlyLearnerCountDisplay(
                        tag = question.promptTag,
                        count = question.promptCount ?: 3,
                    )
                } else if (question.interactionType == "MATCH_COLOR_SHAPE" && question.promptTag != null) {
                    Spacer(Modifier.height(12.dp))
                    EarlyLearnerMatchDisplay(tag = question.promptTag)
                }
            }
        }

        // Expandable Friendly Hint Card
        AnimatedVisibility(visible = hintVisible) {
            Column {
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MeritColors.TertiaryFixed.copy(alpha = 0.35f),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MeritColors.TertiaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MeritColors.OnTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "Friendly hint",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MeritColors.Tertiary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = question.conceptExplainer,
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Choice Options Rendering
        if (question.interactionType == "TAP_IMAGE" || question.interactionType == "COUNT_AND_TAP" || question.interactionType == "MATCH_COLOR_SHAPE") {
            // Early Learner Visual Grid
            EarlyLearnerVisualChoicesGrid(
                choices = question.choices,
                selectedChoiceId = selectedChoiceId,
                evaluatedChoiceId = step.evaluatedChoiceId,
                isCorrect = step.isCorrect,
                tts = tts,
                onChoiceSelected = { choice ->
                    selectedChoiceId = choice.id
                    choice.imageTag?.let { tts.speak(VisualTaxonomy.label(it)) }
                },
            )
        } else {
            // Standard Multiple Choice List
            StandardChoiceList(
                choices = question.choices,
                selectedChoiceId = selectedChoiceId,
                evaluatedChoiceId = step.evaluatedChoiceId,
                isCorrect = step.isCorrect,
                onChoiceSelected = { selectedChoiceId = it.id },
            )
        }

        Spacer(Modifier.height(12.dp))

        if (step.evaluatedChoiceId == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { hintVisible = !hintVisible }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MeritColors.Tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (hintVisible) "Hide hint" else "I need a hint",
                        style = MaterialTheme.typography.labelMedium,
                        color = MeritColors.OnSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Check Answer CTA Button
            Button(
                onClick = {
                    selectedChoiceId?.let { onAnswerSelected(it) }
                },
                enabled = selectedChoiceId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeritColors.Primary,
                    disabledContainerColor = MeritColors.SurfaceContainerHigh,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Check Answer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedChoiceId != null) MeritColors.OnPrimary else MeritColors.OnSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = if (selectedChoiceId != null) MeritColors.OnPrimary else MeritColors.OnSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        } else if (step.isCorrect == false) {
            // Inline Explanation Card for Wrong Answer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, MeritColors.Error.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .background(MeritColors.SurfaceContainerLowest, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Column {
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
                                    .size(32.dp)
                                    .background(MeritColors.Error.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = MeritColors.Error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = "Let's learn why",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MeritColors.OnSurface,
                            )
                        }
                        IconButton(
                            onClick = {
                                val speech = "${step.whyWrongText}. The correct answer is ${step.correctChoiceText}. ${step.question.whyCorrect}"
                                tts.speak(speech)
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Read explanation",
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    step.whyWrongText?.let { why ->
                        val timedOut = why.contains("time", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MeritColors.Error.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (timedOut) "Time ran out" else "Why that wasn't quite right",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MeritColors.Error,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = why,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MeritColors.OnSurface,
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeritColors.TertiaryFixed.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MeritColors.Tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                            Column {
                                Text(
                                    text = "Correct Answer: ${step.correctChoiceText}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MeritColors.Tertiary,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = step.question.whyCorrect,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MeritColors.OnSurface,
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Actions: Learning Resources and Next Question buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onOpenResourceModal,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeritColors.SecondaryContainer),
                        ) {
                            Text(
                                text = "Learn more",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MeritColors.OnSecondaryContainer,
                            )
                        }

                        Button(
                            onClick = onProceedNext,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeritColors.Primary),
                        ) {
                            Text(
                                text = "Next",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MeritColors.OnPrimary,
                            )
                        }
                    }
                }
            }
        } else {
            // Inline Feedback Card for Correct Answer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, MeritColors.Primary, RoundedCornerShape(16.dp))
                    .background(MeritColors.PrimaryFixed, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Spot on, $childName!",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MeritColors.OnPrimaryFixed,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = step.question.whyCorrect,
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnPrimaryFixedVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onProceedNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeritColors.Primary),
                    ) {
                        Text(
                            text = "Next question",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MeritColors.OnPrimary,
                        )
                    }
                }
            }
        }
    }

    // Modal dialog for Learning Resources (hidden while fullscreen video is playing)
    if (step.showResourceModal && step.activeFullscreenVideo == null && step.resource != null) {
        LearningResourceModal(
            resource = step.resource,
            tts = tts,
            onDismiss = onCloseResourceModal,
            onPlayVideo = onPlayVideo,
        )
    }

    // Fullscreen Landscape Video Player
    step.activeFullscreenVideo?.let { activeVideo ->
        FullscreenResourceVideoPlayer(
            video = activeVideo,
            onDismiss = onCloseVideo,
        )
    }
}

// -----------------------------------------------------------------------------
// Screen C09b • Answer Teach (Correct affirmation & concept breakdown)
// -----------------------------------------------------------------------------
@Composable
fun AnswerTeachPane(
    step: QuizUiStep.Feedback,
    childName: String,
    appLabel: String,
    onNext: () -> Unit,
) {
    val feedback = step.feedback
    val question = step.question

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeritColors.SurfaceContainerLow)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        // Progress Tracker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MeritColors.Primary,
                )
                Text(
                    text = "Question ${step.index} of ${step.total} • Concept Cleared",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
            Text(
                text = "${(step.index * 100) / step.total}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MeritColors.Primary,
            )
        }

        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (step.index.toFloat() / step.total.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = MeritColors.Primary,
            trackColor = MeritColors.SurfaceContainer,
        )

        Spacer(Modifier.height(16.dp))

        // Celebratory Delight Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, MeritColors.Primary, RoundedCornerShape(16.dp))
                .background(MeritColors.PrimaryFixed, RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MeritColors.Primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MeritColors.OnPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column {
                    Text(
                        text = "Spot on, $childName!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnPrimaryFixed,
                    )
                    Text(
                        text = "That is completely correct!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeritColors.OnPrimaryFixedVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Question Recap Math Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MeritColors.SurfaceContainerLowest,
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "QUESTION RECAP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnSurfaceVariant,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = question.whyCorrect.take(40).ifBlank { question.prompt },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.Primary,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MeritColors.SecondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MeritColors.OnSecondaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "+10 Seeds",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MeritColors.OnSecondaryContainer,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Concept at a Glance
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MeritColors.SurfaceContainerLowest,
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Concept at a Glance",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MeritColors.OnSurface,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MeritColors.SurfaceContainer,
                    ) {
                        Text(
                            text = question.conceptTitle,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = feedback.conceptLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurfaceVariant,
                )

                // Visual Distribution Baskets model
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (basket in 1..3) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MeritColors.SurfaceContainerLow,
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "Group $basket",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MeritColors.Secondary,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "🍎 🍎\n🍎 🍎",
                                    textAlign = TextAlign.Center,
                                    fontSize = 16.sp,
                                )
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MeritColors.PrimaryFixed.copy(alpha = 0.5f),
                                ) {
                                    Text(
                                        text = "4 items",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MeritColors.OnPrimaryFixedVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Educational "Why this works" explanation box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MeritColors.SurfaceContainerLowest,
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Why this works",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnSurface,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = feedback.whyLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurfaceVariant,
                    lineHeight = 22.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Mini Knowledge Spark
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MeritColors.SecondaryContainer.copy(alpha = 0.6f),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MeritColors.Secondary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MeritColors.OnSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = "MINI KNOWLEDGE SPARK",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnSecondaryContainer,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = question.miniLesson?.bodyLines?.firstOrNull()
                            ?: "Keep learning step by step! Every concept helps unlock more screen time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSecondaryContainer,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Next Question Button
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MeritColors.Primary),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (step.index >= step.total) "Complete Quiz 🎉" else "Next Question (${step.index + 1} of ${step.total})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MeritColors.OnPrimary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MeritColors.OnPrimary,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 30-Second Locked Teaching Pause Screen (Mandatory Calm View Gate)
// -----------------------------------------------------------------------------
@Composable
fun LockedExplanationPane(
    step: QuizUiStep.LockedExplanation,
    childName: String,
    tts: QuizTtsNarrator,
    onContinue: () -> Unit,
) {
    // Disable Back navigation during mandatory locked explanation
    BackHandler(enabled = step.lockSecondsRemaining > 0) {}

    // Auto speak explanation once when locked screen appears
    LaunchedEffect(step.question.id) {
        val speech = "${step.feedback.resultLine}. ${step.feedback.whyLine}. The correct answer is ${step.correctChoiceText}."
        tts.speak(speech)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeritColors.SurfaceContainerLow)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Status Shield Badge
        Surface(
            shape = CircleShape,
            color = MeritColors.SecondaryContainer.copy(alpha = 0.8f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Spa,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MeritColors.OnSecondaryContainer,
                )
                Text(
                    text = "30s Focused Teaching Pause",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MeritColors.OnSecondaryContainer,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Hero Graphic Artwork: Gentle Resting Moon & Sprout
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(MeritColors.TertiaryFixed.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(MeritColors.SurfaceContainerLowest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Spa,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MeritColors.Tertiary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Not quite this time, $childName 🌿",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MeritColors.OnSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Let's pause our fingers and understand this idea together before moving on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MeritColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        // Big Timer Countdown Card (30s)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MeritColors.SurfaceContainerLowest,
            shadowElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Learning Window",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MeritColors.Secondary,
                    )
                }

                Spacer(Modifier.height(8.dp))

                val seconds = step.lockSecondsRemaining
                val display = "00:${if (seconds < 10) "0$seconds" else "$seconds"}"

                Text(
                    text = display,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MeritColors.OnSurface,
                )

                Spacer(Modifier.height(8.dp))

                // Sip of water & breathe encouragement bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MeritColors.Tertiary,
                        )
                        Text(
                            text = "Take a sip of water",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Pause & breathe",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.Tertiary,
                    )
                }

                Spacer(Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { ((30 - seconds) / 30f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MeritColors.TertiaryContainer,
                    trackColor = MeritColors.SurfaceContainerHigh,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Correct Answer Explicit Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MeritColors.TertiaryFixed.copy(alpha = 0.35f),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MeritColors.Tertiary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MeritColors.OnTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = "CORRECT ANSWER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.Tertiary,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = step.correctChoiceText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Mini Lesson & Explanation Box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MeritColors.SurfaceContainerLowest,
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = step.miniLesson.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MeritColors.OnSurface,
                        )
                    }
                    IconButton(
                        onClick = {
                            val text = "${step.feedback.whyLine}. ${step.miniLesson.bodyLines.joinToString(". ")}"
                            tts.speak(text)
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Read aloud",
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = step.feedback.whyLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurfaceVariant,
                    lineHeight = 22.sp,
                )

                step.miniLesson.bodyLines.forEach { line ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurface,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Mandatory Gated Continue Button
        val canContinue = step.lockSecondsRemaining <= 0
        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MeritColors.Primary,
                disabledContainerColor = MeritColors.SurfaceContainerHigh,
            ),
        ) {
            Text(
                text = if (canContinue) "Continue to Next Question 🚀" else "Please pause & learn (${step.lockSecondsRemaining}s)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (canContinue) MeritColors.OnPrimary else MeritColors.OnSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Screen C10 • Quiz Result Pass (Challenge Cleared Celebration)
// -----------------------------------------------------------------------------
@Composable
fun QuizPassResultPane(
    result: QuizSessionResult,
    childName: String,
    appLabel: String,
    unlockedMinutes: Int,
    onContinueToApp: () -> Unit,
    onGoHome: () -> Unit,
    stickerTitle: String? = null,
    stickerEmoji: String? = null,
    stickerStageLabel: String? = null,
    explorerLevel: Int = 1,
    xpGained: Int = 0,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeritColors.SurfaceContainerLow),
    ) {
        // Confetti Canvas Particles
        ConfettiCelebrationEffect()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Badge
            Surface(
                shape = CircleShape,
                color = MeritColors.TertiaryFixed,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MeritColors.OnTertiaryFixed,
                    )
                    Text(
                        text = "CHALLENGE CLEARED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnTertiaryFixed,
                        letterSpacing = 1.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Awesome job, $childName! 🎉",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MeritColors.OnSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "You unlocked more time and earned a new explorer reward.",
                style = MaterialTheme.typography.bodyMedium,
                color = MeritColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            // Central Hero Badge: Super Thinker
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MeritColors.SurfaceContainerLowest,
                shadowElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Laurel Wreath Canvas
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color(0xFF96D5A6),
                                radius = size.minDimension / 2f,
                                style = Stroke(width = 3.dp.toPx(), pathEffect = null),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MeritColors.PrimaryFixed, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "Super Thinker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = "Explorer Level $explorerLevel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MeritColors.Primary,
                    )
                    if (xpGained > 0) {
                        Text(
                            text = "+$xpGained XP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Primary Reward Card: +30 Minutes Unlocked
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MeritColors.Primary,
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "REWARD ACTIVATED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MeritColors.OnPrimaryContainer,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = "+$unlockedMinutes Minutes Unlocked",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MeritColors.OnPrimary,
                        )
                        Text(
                            text = "Enjoy screen time on $appLabel!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnPrimaryContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Unlocked Sticker Card (only when a new sticker was earned)
            if (stickerTitle != null && stickerEmoji != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MeritColors.SurfaceContainerLowest,
                    shadowElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(MeritColors.SecondaryContainer, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = stickerEmoji, fontSize = 32.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "$stickerTitle Sticker",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MeritColors.OnSurface,
                                )
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MeritColors.Tertiary,
                                )
                            }
                            Text(
                                text = buildString {
                                    append("Added to $childName's Sticker Book")
                                    if (!stickerStageLabel.isNullOrBlank()) {
                                        append(" • ${stickerStageLabel}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            } else {
                Spacer(Modifier.height(14.dp))
            }

            // Quiz Performance Summary Bento
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MeritColors.SurfaceContainer,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Quiz Recap",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MeritColors.OnSurface,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MeritColors.Tertiary,
                            )
                            Text(
                                text = "All Passed",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MeritColors.Tertiary,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MeritColors.SurfaceContainerLowest,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Accuracy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                                Text(
                                    text = "${result.correctCount} / ${result.total} Correct",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MeritColors.OnSurface,
                                )
                                Text(
                                    text = "${result.percent}% mastery",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.Tertiary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MeritColors.SurfaceContainerLowest,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Pacing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                                Text(
                                    text = "1m 42s",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MeritColors.OnSurface,
                                )
                                Text(
                                    text = "Thoughtful pacing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "Concepts Mastered:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MeritColors.SurfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MeritColors.Tertiary,
                                )
                                Text(
                                    text = "Equal grouping",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MeritColors.SurfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MeritColors.Tertiary,
                                )
                                Text(
                                    text = "Counting on",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Primary CTAs
            Button(
                onClick = onContinueToApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MeritColors.Primary),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Continue to $appLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnPrimary,
                    )
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = MeritColors.OnPrimary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MeritColors.OnSurface),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Go to Child Home",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Safety footnote
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MeritColors.Secondary,
                )
                Text(
                    text = "Emergency calls to Mom and Dad are always available.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Screen C12 • Fail Lock Calm Cooldown (Calm Horizon resting screen)
// -----------------------------------------------------------------------------
@Composable
fun FailLockCooldownPane(
    childName: String,
    secondsRemaining: Int,
    totalSeconds: Int,
    endsAtEpochMs: Long,
    onCooldownFinished: () -> Unit,
    onCallMom: () -> Unit,
    onCallDad: () -> Unit,
    onOpenPin: () -> Unit,
) {
    val safeTotal = totalSeconds.coerceAtLeast(1)
    val clampedRemaining = secondsRemaining.coerceAtLeast(0)
    val progress = ((safeTotal - clampedRemaining).toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    val finishesAtLabel = remember(endsAtEpochMs) { formatCooldownEndTime(endsAtEpochMs) }
    val tip = remember(clampedRemaining / 20) {
        cooldownBreathTips[((clampedRemaining / 20).coerceAtLeast(0)) % cooldownBreathTips.size]
    }

    LaunchedEffect(clampedRemaining) {
        if (clampedRemaining <= 0) {
            onCooldownFinished()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeritColors.SurfaceContainerLow)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MeritColors.SecondaryContainer.copy(alpha = 0.8f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Spa,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MeritColors.OnSecondaryContainer,
                )
                Text(
                    text = "Fail Lock Shield",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MeritColors.OnSecondaryContainer,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(112.dp)
                .background(MeritColors.TertiaryFixed.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MeritColors.SurfaceContainerLowest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🌙", fontSize = 42.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Let's rest our eyes and brain",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MeritColors.OnSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Great try, $childName! Non-emergency apps are resting until this timer finishes. Stretch, drink water, or look out the window.",
            style = MaterialTheme.typography.bodyMedium,
            color = MeritColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MeritColors.SurfaceContainerLowest,
            shadowElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Resting Window",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MeritColors.Secondary,
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = formatCountdown(clampedRemaining),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MeritColors.OnSurface,
                )

                Spacer(Modifier.height(6.dp))

                Surface(
                    shape = CircleShape,
                    color = MeritColors.SurfaceContainer,
                ) {
                    Text(
                        text = if (clampedRemaining > 0) {
                            "Cooldown finishes at $finishesAtLabel"
                        } else {
                            "Cooldown finished"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MeritColors.Tertiary,
                        )
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Pause & breathe",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.Tertiary,
                    )
                }

                Spacer(Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MeritColors.TertiaryFixedDim,
                    trackColor = MeritColors.SurfaceContainerHigh,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MeritColors.SurfaceContainerLow,
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                                .size(28.dp)
                                .background(MeritColors.TertiaryFixed, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MeritColors.Tertiary,
                            )
                        }
                        Column {
                            Text(
                                text = "Phone & Emergency Contacts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MeritColors.OnSurface,
                            )
                            Text(
                                text = "Always Unlocked & Reachable",
                                style = MaterialTheme.typography.labelSmall,
                                color = MeritColors.Tertiary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = MeritColors.OnSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EmergencyCallButton(
                        label = "Call Mom",
                        iconColor = MeritColors.Primary,
                        onClick = onCallMom,
                        modifier = Modifier.weight(1f),
                    )
                    EmergencyCallButton(
                        label = "Call Dad",
                        iconColor = MeritColors.Secondary,
                        onClick = onCallDad,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Parent override? Enter PIN",
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onOpenPin() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MeritColors.OnSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

private val cooldownBreathTips = listOf(
    "Take a sip of water",
    "Look out the window",
    "Stretch your arms",
    "Roll your shoulders",
    "Take three slow breaths",
)

private fun formatCooldownEndTime(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    val formatter = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(epochMs))
}

private fun launchQuizDialer(context: Context) {
    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val target = runCatching {
        if (dialIntent.resolveActivity(context.packageManager) != null) {
            dialIntent
        } else {
            context.packageManager.getLaunchIntentForPackage("com.google.android.dialer")
                ?: context.packageManager.getLaunchIntentForPackage("com.android.dialer")
                ?: dialIntent
        }
    }.getOrDefault(dialIntent)
    runCatching { context.startActivity(target) }
}

// -----------------------------------------------------------------------------
// Screen C16 • Bedtime Lock (Sleep horizon & wind-down screen)
// -----------------------------------------------------------------------------
@Composable
fun BedtimeLockPane(
    childName: String,
    onCallMom: () -> Unit,
    onCallDad: () -> Unit,
    onOpenPin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeritColors.SurfaceContainerLow)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MeritColors.Surface,
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsPaused,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MeritColors.Primary,
                    )
                    Text(
                        text = "BEDTIME MODE ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.Primary,
                        letterSpacing = 1.sp,
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MeritColors.SecondaryContainer,
            ) {
                Text(
                    text = "Silent",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MeritColors.OnSecondaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Sleep Mascot Artwork (Moon with nightcap)
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MeritColors.SecondaryFixed.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🌜", fontSize = 54.sp)
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Time to dream, $childName 😴",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MeritColors.OnSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "All play and video apps are softly resting until tomorrow morning at 7:00 AM.",
            style = MaterialTheme.typography.bodyMedium,
            color = MeritColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        // Card 1: Morning Countdown & Sleep Streak
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MeritColors.Surface,
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MeritColors.SecondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "☀️", fontSize = 20.sp)
                        }
                        Column {
                            Text(
                                text = "WAKES UP IN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MeritColors.OnSurfaceVariant,
                                letterSpacing = 1.sp,
                            )
                            Text(
                                text = "9h 18m",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MeritColors.OnSurface,
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MeritColors.SurfaceContainerHigh,
                    ) {
                        Text(
                            text = "7:00 AM",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MeritColors.SurfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Sleep Streak",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MeritColors.OnSurface,
                            )
                        }
                        Text(
                            text = "4 peaceful nights 🌟",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MeritColors.Primary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Card 2: Wind-Down Activity Ideas
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MeritColors.Surface,
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MeritColors.SurfaceContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "📖", fontSize = 18.sp)
                }
                Column {
                    Text(
                        text = "Calm wind-down ideas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Try reading a quiet book, listening to a sleep song, or taking three slow, deep turtle breaths.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Card 3: Emergency Calling Always Reachable
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MeritColors.Surface,
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Need Mom or Dad?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = "Family calling is always awake & ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EmergencyCallButton(label = "Call Mom", iconColor = MeritColors.Primary, onClick = onCallMom, modifier = Modifier.weight(1f))
                    EmergencyCallButton(label = "Call Dad", iconColor = MeritColors.Primary, onClick = onCallDad, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Parent Passcode Override Trigger
        Text(
            text = "Parent override? Enter PIN",
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onOpenPin() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MeritColors.OnSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

// -----------------------------------------------------------------------------
// Component Helpers & Early Learner Visual Displays
// -----------------------------------------------------------------------------
@Composable
private fun MicroTag(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color) {
    Surface(
        shape = CircleShape,
        color = MeritColors.SurfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MeritColors.OnSurfaceVariant)
        }
    }
}

@Composable
private fun StandardChoiceList(
    choices: List<QuizChoice>,
    selectedChoiceId: String?,
    evaluatedChoiceId: String? = null,
    isCorrect: Boolean? = null,
    onChoiceSelected: (QuizChoice) -> Unit,
) {
    val labels = listOf("A", "B", "C", "D")
    val isEvaluated = evaluatedChoiceId != null
    val shape = RoundedCornerShape(14.dp)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEachIndexed { idx, choice ->
            val isSelected = choice.id == selectedChoiceId
            val badgeLabel = labels.getOrElse(idx) { "${idx + 1}" }

            val isEvaluatedWrong = isEvaluated && isCorrect == false && choice.id == evaluatedChoiceId
            val isRevealedCorrect = isEvaluated && choice.correct

            val backgroundColor = when {
                isEvaluatedWrong -> MeritColors.ErrorContainer.copy(alpha = 0.55f)
                isRevealedCorrect -> MeritColors.TertiaryFixed
                isSelected -> MeritColors.PrimaryFixed
                else -> MeritColors.SurfaceContainerLowest
            }

            val strokeColor = when {
                isEvaluatedWrong -> MeritColors.Error
                isRevealedCorrect -> MeritColors.Tertiary
                isSelected -> MeritColors.Primary
                else -> MeritColors.OutlineVariant.copy(alpha = 0.55f)
            }

            val strokeWidth = when {
                isEvaluatedWrong || isRevealedCorrect || isSelected -> 2.dp
                else -> 1.dp
            }

            val badgeBgColor = when {
                isEvaluatedWrong -> MeritColors.Error
                isRevealedCorrect -> MeritColors.Tertiary
                isSelected -> MeritColors.Primary
                else -> MeritColors.SurfaceContainerHigh
            }

            val badgeTextColor = when {
                isEvaluatedWrong || isRevealedCorrect || isSelected -> MeritColors.OnPrimary
                else -> MeritColors.OnSurfaceVariant
            }

            val textColor = when {
                isEvaluatedWrong -> MeritColors.OnErrorContainer
                isRevealedCorrect -> MeritColors.OnTertiaryFixed
                isSelected -> MeritColors.OnPrimaryFixed
                else -> MeritColors.OnSurface
            }

            val isDimmed = isEvaluated && !isEvaluatedWrong && !isRevealedCorrect

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .border(strokeWidth, strokeColor, shape)
                    .background(
                        color = if (isDimmed) backgroundColor.copy(alpha = 0.45f) else backgroundColor,
                        shape = shape,
                    )
                    .clip(shape)
                    .clickable(enabled = !isEvaluated) { onChoiceSelected(choice) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color = badgeBgColor, shape = CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor,
                            )
                        }
                        Column {
                            Text(
                                text = choice.text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected || isEvaluatedWrong || isRevealedCorrect) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                },
                                color = if (isDimmed) textColor.copy(alpha = 0.5f) else textColor,
                            )
                            if (isEvaluatedWrong) {
                                Text(
                                    text = "Your answer",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MeritColors.Error,
                                )
                            } else if (isRevealedCorrect && isCorrect == false) {
                                Text(
                                    text = "Correct answer",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MeritColors.OnTertiaryFixedVariant,
                                )
                            }
                        }
                    }

                    val trailingIcon = when {
                        isEvaluatedWrong -> Icons.Default.Close
                        isRevealedCorrect -> Icons.Default.CheckCircle
                        isSelected -> Icons.Default.CheckCircle
                        else -> Icons.Default.RadioButtonUnchecked
                    }

                    val iconTint = when {
                        isEvaluatedWrong -> MeritColors.Error
                        isRevealedCorrect -> MeritColors.Tertiary
                        isSelected -> MeritColors.Primary
                        else -> MeritColors.OutlineVariant
                    }

                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EarlyLearnerVisualChoicesGrid(
    choices: List<QuizChoice>,
    selectedChoiceId: String?,
    evaluatedChoiceId: String? = null,
    isCorrect: Boolean? = null,
    tts: QuizTtsNarrator,
    onChoiceSelected: (QuizChoice) -> Unit,
) {
    val isEvaluated = evaluatedChoiceId != null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        choices.forEach { choice ->
            val isSelected = choice.id == selectedChoiceId
            val tag = choice.imageTag ?: ""
            val emoji = VisualTaxonomy.emoji(tag)
            val label = choice.text.ifBlank { VisualTaxonomy.label(tag) }

            val isEvaluatedWrong = isEvaluated && isCorrect == false && choice.id == evaluatedChoiceId
            val isRevealedCorrect = isEvaluated && choice.correct

            val bgColor = when {
                isEvaluatedWrong -> MeritColors.ErrorContainer.copy(alpha = 0.55f)
                isRevealedCorrect -> MeritColors.TertiaryFixed
                isSelected -> MeritColors.PrimaryFixed
                else -> MeritColors.SurfaceContainerLowest
            }

            val strokeColor = when {
                isEvaluatedWrong -> MeritColors.Error
                isRevealedCorrect -> MeritColors.Tertiary
                isSelected -> MeritColors.Primary
                else -> MeritColors.OutlineVariant.copy(alpha = 0.55f)
            }

            val strokeWidth = when {
                isEvaluatedWrong || isRevealedCorrect || isSelected -> 2.dp
                else -> 1.dp
            }

            val isDimmed = isEvaluated && !isEvaluatedWrong && !isRevealedCorrect
            val shape = RoundedCornerShape(16.dp)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.85f)
                    .border(strokeWidth, strokeColor, shape)
                    .background(
                        color = if (isDimmed) bgColor.copy(alpha = 0.45f) else bgColor,
                        shape = shape,
                    )
                    .clip(shape)
                    .clickable(enabled = !isEvaluated) { onChoiceSelected(choice) }
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = emoji, fontSize = 42.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (isEvaluatedWrong) {
                            "❌ $label"
                        } else if (isRevealedCorrect && isCorrect == false) {
                            "✅ $label"
                        } else {
                            label
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isEvaluatedWrong -> MeritColors.OnErrorContainer
                            isRevealedCorrect -> MeritColors.OnTertiaryFixed
                            isSelected -> MeritColors.OnPrimaryFixed
                            else -> MeritColors.OnSurface
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EarlyLearnerCountDisplay(tag: String, count: Int) {
    val emoji = VisualTaxonomy.emoji(tag)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Count them:",
                style = MaterialTheme.typography.labelSmall,
                color = MeritColors.OnSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 1..count) {
                    Text(text = emoji, fontSize = 34.sp)
                }
            }
        }
    }
}

@Composable
private fun EarlyLearnerMatchDisplay(tag: String) {
    val item = VisualTaxonomy.getItem(tag)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Match this target:",
                style = MaterialTheme.typography.labelSmall,
                color = MeritColors.OnSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(text = item?.emoji ?: "🌟", fontSize = 44.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = item?.label ?: tag,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MeritColors.OnSurface,
            )
        }
    }
}

@Composable
private fun EmergencyCallButton(
    label: String,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconColor,
                )
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = "Fast Dial",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfettiCelebrationEffect() {
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "confettiFloat",
    )

    val colors = listOf(
        Color(0xFF00514D),
        Color(0xFF685D4B),
        Color(0xFF13522F),
        Color(0xFFE59A00),
        Color(0xFFBA1A1A),
        Color(0xFF1976D2),
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val count = 28
        for (i in 0 until count) {
            val randomX = ((i * 137.5f) % size.width)
            val randomSpeed = 1f + ((i % 5) * 0.25f)
            val yPos = ((animProgress * size.height * randomSpeed) + (i * 30f)) % size.height
            val color = colors[i % colors.size]
            val particleRadius = 4.dp.toPx()

            drawCircle(
                color = color.copy(alpha = 0.85f),
                radius = particleRadius,
                center = Offset(randomX, yPos),
            )
        }
    }
}
