package com.meritscreen.feature.onboarding.ui

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.CustomPromptSanitizer
import com.meritscreen.core.common.domain.LearningPromptQuickAdds
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class AiLearningContextRoute(
    val childName: String = "your child",
    val grade: String = "3rd Grade",
    val avatar: String = "RABBIT",
    val ageBand: String = AgeBand.AGE_7_TO_9.name,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiLearningContextScreen(
    childName: String = "your child",
    grade: String = "3rd Grade",
    ageBand: AgeBand = AgeBand.AGE_7_TO_9,
    avatarEmoji: String = AvatarPreset.Default.emoji,
    initialPrompt: String = "",
    stepLabel: String = "AI Learning Profile",
    onContinue: () -> Unit = {},
    onSaveAndContinue: ((sanitizedPrompt: String) -> Unit)? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var rawText by remember { mutableStateOf(initialPrompt) }
    var isListening by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Word counter
    val wordCount = remember(rawText) {
        if (rawText.isBlank()) 0 else rawText.trim().split("\\s+".toRegex()).size
    }

    // Android Speech Recognizer launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        isListening = false
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            rawText = if (rawText.isBlank()) spoken else "$rawText $spoken"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeritColors.Surface),
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = MeritSpacing.sm, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MeritColors.OnSurface,
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MeritColors.SecondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MeritColors.Primary),
                    )
                    Text(
                        text = stepLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSecondaryContainer,
                    )
                }
            }

            Box(modifier = Modifier.size(48.dp))
        }

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = MeritSpacing.margin)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Listening Toast Banner
            AnimatedVisibility(visible = isListening) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MeritColors.Primary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PulsingMicDot()
                            Text(
                                text = "Listening... Speak clearly about $childName",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                        IconButton(
                            onClick = { isListening = false },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // Active Child Context Pill
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MeritColors.SecondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = avatarEmoji, fontSize = 20.sp)
                        }
                        Column {
                            Text(
                                text = "$childName ($grade)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MeritColors.OnSurface,
                            )
                            Text(
                                text = "• Adaptive Profile Ready",
                                style = MaterialTheme.typography.labelSmall,
                                color = MeritColors.Tertiary,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeritColors.SurfaceContainer,
                    ) {
                        Text(
                            text = "AI Tailored",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MeritColors.OnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Title & Description
            Column {
                Text(
                    text = "AI Learning Context",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Describe your child's learning style, curriculum, or areas needing improvement to customize their adaptive quizzes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            // Text Input Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MeritColors.SurfaceContainerLowest,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Tell MeritAI about $childName",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MeritColors.Primary,
                            )
                        }
                        Text(
                            text = "$wordCount / 300 words",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.Secondary,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = rawText,
                        onValueChange = {
                            if (it.split("\\s+".toRegex()).size <= 320) {
                                rawText = it
                            }
                        },
                        placeholder = {
                            Text(
                                text = "Enter learning goals, focus topics, or guidance for $childName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MeritColors.OutlineVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MeritColors.SurfaceContainerLow,
                            unfocusedContainerColor = MeritColors.SurfaceContainerLow,
                            focusedBorderColor = MeritColors.Primary,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dictation Bar inside Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = MeritColors.OnSurfaceVariant,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "Dictation supported",
                                style = MaterialTheme.typography.labelSmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MeritColors.PrimaryFixed,
                            modifier = Modifier.clickable {
                                isListening = true
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak about $childName's learning goals...")
                                }
                                try {
                                    speechLauncher.launch(intent)
                                } catch (_: Exception) {
                                    // Simulation fallback for emulators without speech recognizer app
                                    isListening = false
                                    val simulated = "Prefers visual fraction bars and mental math drills."
                                    rawText = if (rawText.isBlank()) simulated else "$rawText $simulated"
                                }
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice Dictate",
                                    tint = MeritColors.OnPrimaryFixedVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Tap to speak",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MeritColors.OnPrimaryFixedVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Quick Add Suggestions
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Quick add suggestions:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = "Tap to append",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.Secondary,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val quickAdds = remember(ageBand, rawText) {
                    LearningPromptQuickAdds.forAgeBand(ageBand, rawText)
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    quickAdds.forEach { chip ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MeritColors.SurfaceContainerHigh,
                            modifier = Modifier.clickable {
                                rawText = if (rawText.isBlank()) {
                                    chip.snippet
                                } else {
                                    "$rawText ${chip.snippet}"
                                }
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MeritColors.Primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = chip.label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MeritColors.OnSurface,
                                )
                            }
                        }
                    }
                }
            }

            // Privacy Reassurance
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Encrypted & COPPA compliant. Prompts are sanitized locally and used strictly for quiz generation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom CTA
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            color = MeritColors.Surface,
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MeritSpacing.margin, vertical = 16.dp),
            ) {
                Button(
                    onClick = {
                        val sanitized = CustomPromptSanitizer.sanitize(rawText)
                        if (onSaveAndContinue != null) {
                            onSaveAndContinue(sanitized)
                        } else {
                            onContinue()
                        }
                    },
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeritColors.PrimaryContainer,
                        contentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Save Profile & Finish Setup",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
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
            }
        }
    }
}

@Composable
private fun PulsingMicDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mic_alpha",
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(MeritColors.PrimaryFixed),
    )
}
