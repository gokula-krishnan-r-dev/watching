package com.meritscreen.feature.child.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meritscreen.core.common.domain.NurseryTeachItem
import com.meritscreen.core.ui.theme.MeritColors
import kotlinx.coroutines.delay

/**
 * Local offline-first teaching cards for Early Learners (Ages 3-6: Nursery, LKG, UKG).
 * Teaches through large pictures, spoken audio, and cheerful cards without quiz anxiety.
 * Automatically narrates with sound and directly navigates to the app upon completion.
 */
@Composable
fun NurseryTeachPane(
    items: List<NurseryTeachItem>,
    childName: String,
    tts: QuizTtsNarrator,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onComplete() }
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    val currentItem = items.getOrNull(currentIndex) ?: items.first()

    // Bouncy scale animation for tactile card hero
    val infiniteTransition = rememberInfiniteTransition(label = "card_bounce")
    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hero_scale",
    )

    // Auto-narrate prompt when card appears
    LaunchedEffect(currentItem.id) {
        tts.speak(currentItem.speechText)
    }

    // Auto-advance after 7 seconds of exposure
    LaunchedEffect(currentIndex) {
        delay(7000L)
        if (currentIndex + 1 < items.size) {
            currentIndex++
        } else {
            onComplete()
        }
    }

    val cardBgColor = runCatching {
        Color(android.graphics.Color.parseColor(currentItem.colorHex))
    }.getOrDefault(MeritColors.SurfaceContainerLowest)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        cardBgColor.copy(alpha = 0.35f),
                        MeritColors.SurfaceContainerLow,
                    ),
                ),
            )
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MeritColors.SecondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MeritColors.OnSecondaryContainer,
                    )
                    Text(
                        text = "Little Learner • ${childName}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnSecondaryContainer,
                    )
                }
            }

            // Speaker icon to repeat speech
            IconButton(
                onClick = { tts.speak(currentItem.speechText) },
                modifier = Modifier
                    .size(42.dp)
                    .background(MeritColors.SurfaceContainerHighest, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Speak",
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Animated Card Hero Container
        AnimatedContent(
            targetState = currentItem,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
            label = "teach_card",
        ) { target ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.92f)
                    .clip(RoundedCornerShape(32.dp))
                    .clickable { tts.speak(target.speechText) },
                shape = RoundedCornerShape(32.dp),
                color = cardBgColor,
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Big Tactile Hero Emoji/Artwork
                    Box(
                        modifier = Modifier
                            .scale(bounceScale)
                            .size(160.dp)
                            .background(Color.White.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = target.emoji,
                            fontSize = 84.sp,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // Simple Concept Label (e.g. "Apple", "Umbrella", "Star")
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MeritColors.OnSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Bottom Controls: Progress Dots + Next Card Button
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Segment Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.indices.forEach { idx ->
                    val active = idx == currentIndex
                    Box(
                        modifier = Modifier
                            .size(if (active) 12.dp else 8.dp)
                            .background(
                                color = if (active) MeritColors.Primary else MeritColors.OutlineVariant,
                                shape = CircleShape,
                            ),
                    )
                }
            }

            // Big Child-Friendly Next / Done Action
            Button(
                onClick = {
                    if (currentIndex + 1 < items.size) {
                        currentIndex++
                    } else {
                        onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MeritColors.Primary),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (currentIndex + 1 < items.size) "Next Card 🌟" else "All Done! 🎉",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnPrimary,
                    )
                    Icon(
                        imageVector = if (currentIndex + 1 < items.size) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
                        contentDescription = null,
                        tint = MeritColors.OnPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
