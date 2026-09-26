package com.meritscreen.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing

@Immutable
data class AppUsageSegment(
    val label: String,
    val minutes: Int,
    val color: Color,
)

/**
 * Animated circular progress ring showing quiz scores / percentage.
 * Matches Stitch P12 score circle widget.
 */
@Composable
fun MeritCircularProgressRing(
    percent: Int,
    modifier: Modifier = Modifier.size(56.dp),
    trackColor: Color = MeritColors.SurfaceContainer,
    progressColor: Color = MeritColors.Tertiary,
    strokeWidth: Dp = 4.dp,
    showPercentText: Boolean = true,
) {
    val clamped = percent.coerceIn(0, 100)
    val animatedProgress by animateFloatAsState(
        targetValue = clamped / 100f,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "circular_progress",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val strokePx = strokeWidth.toPx()

            // Background Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // Foreground Progress Arc
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
        if (showPercentText) {
            Text(
                text = "$clamped%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Multi-segmented horizontal progress bar with app breakdown segments and animated fills.
 * Matches Stitch P12 Screen Time breakdown bar.
 */
@Composable
fun MeritSegmentedProgressBar(
    segments: List<AppUsageSegment>,
    ceilingMinutes: Int,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(14.dp),
    backgroundColor: Color = MeritColors.SurfaceContainer,
) {
    val totalMinutes = ceilingMinutes.coerceAtLeast(1)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(backgroundColor)
            .padding(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            segments.filter { it.minutes > 0 }.forEach { segment ->
                val weight = (segment.minutes.toFloat() / totalMinutes).coerceIn(0.01f, 1f)
                val animatedWeight by animateFloatAsState(
                    targetValue = weight,
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    label = "segment_${segment.label}",
                )
                Box(
                    modifier = Modifier
                        .weight(animatedWeight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9999.dp))
                        .background(segment.color),
                )
            }
            val usedMinutes = segments.sumOf { it.minutes }
            val remainingMinutes = (totalMinutes - usedMinutes).coerceAtLeast(0)
            if (remainingMinutes > 0) {
                val remainingWeight = (remainingMinutes.toFloat() / totalMinutes).coerceIn(0.01f, 1f)
                Spacer(
                    modifier = Modifier
                        .weight(remainingWeight)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Legend displaying the colored dots, labels, and formatted minutes for app breakdown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUsageLegend(
    segments: List<AppUsageSegment>,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MeritSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MeritSpacing.xs),
    ) {
        segments.filter { it.minutes > 0 }.forEach { segment ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MeritSpacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(segment.color),
                )
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatMinutes(segment.minutes),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Pulsing animated dot indicator recreating the Stitch design live state indicator.
 */
@Composable
fun PulsingStatusDot(
    modifier: Modifier = Modifier,
    color: Color = MeritColors.Tertiary,
    dotSize: Dp = 10.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_scale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_alpha",
    )

    Box(
        modifier = modifier.size(dotSize * 2),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize * pulseScale)
                .clip(CircleShape)
                .background(color.copy(alpha = pulseAlpha)),
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(color),
        )
    }
}

private fun formatMinutes(minutes: Int): String = when {
    minutes <= 0 -> "0m"
    minutes < 60 -> "${minutes}m"
    else -> {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "${h}h" else "${h}h ${m}m"
    }
}
