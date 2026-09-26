package com.meritscreen.feature.onboarding.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.ui.components.ChildAvatar
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data class SetupCompleteRoute(
    val childName: String = "your child",
    val grade: String = "3rd Grade",
    val avatar: String = "RABBIT",
)

@Composable
fun SetupCompleteScreen(
    childName: String = "your child",
    grade: String = "3rd Grade",
    avatar: AvatarPreset = AvatarPreset.Default,
    allowanceMinutes: Int = 90,
    allowlistCount: Int = 5,
    quizIntervalMinutes: Int = 30,
    cooldownMinutes: Int = 10,
    bedtimeRange: String = "8:30 PM – 7:00 AM",
    onOpenDashboard: () -> Unit = {},
    autoRedirect: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var countdownSeconds by remember { mutableIntStateOf(5) }

    if (autoRedirect) {
        LaunchedEffect(Unit) {
            while (countdownSeconds > 0) {
                delay(1000L)
                countdownSeconds -= 1
            }
            onOpenDashboard()
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (5 - countdownSeconds) / 5f,
        animationSpec = tween(1000),
        label = "auto_redirect_progress",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeritColors.Surface)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .padding(horizontal = MeritSpacing.margin),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Glowing check badge
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MeritColors.PrimaryFixed),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MeritColors.Primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.PrimaryFixed,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = MeritColors.OnPrimaryFixedVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Profile & Rules Ready!",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnPrimaryFixedVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "$childName's Guardian Shield is Active",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MeritColors.OnSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "All initial boundaries and micro-learning intervals are configured and saved to your family vault.",
                style = MaterialTheme.typography.bodyMedium,
                color = MeritColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Bento Summary Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MeritColors.SurfaceContainerLowest,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Profile Row
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MeritColors.SurfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ChildAvatar(
                                    avatar = avatar,
                                    size = 44.dp,
                                    background = MeritColors.SecondaryContainer,
                                    contentDescription = "$childName avatar",
                                )
                                Column {
                                    Text(
                                        text = "$childName ($grade)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MeritColors.OnSurface,
                                    )
                                    Text(
                                        text = "Curiosity Quests & Focus active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MeritColors.Primary,
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MeritColors.OnSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    // Rules stack
                    val hours = allowanceMinutes / 60
                    val mins = allowanceMinutes % 60
                    SummaryRuleRow(
                        icon = Icons.Default.HourglassBottom,
                        iconBg = MeritColors.SecondaryContainer,
                        iconTint = MeritColors.OnSecondaryContainer,
                        title = "Daily Allowance",
                        subtitle = if (hours > 0) "${hours}h ${mins}m daily screen time limit" else "${mins}m daily limit",
                    )
                    SummaryRuleRow(
                        icon = Icons.Default.Apps,
                        iconBg = MeritColors.PrimaryFixed,
                        iconTint = MeritColors.Primary,
                        title = "Allowlist",
                        subtitle = "$allowlistCount approved apps (educational safe list)",
                    )
                    SummaryRuleRow(
                        icon = Icons.Default.Psychology,
                        iconBg = MeritColors.SecondaryContainer,
                        iconTint = MeritColors.OnSecondaryContainer,
                        title = "Learning Gate",
                        subtitle = "Quiz every ${quizIntervalMinutes}m • ${cooldownMinutes}m fail-cooldown",
                    )
                    SummaryRuleRow(
                        icon = Icons.Default.Bedtime,
                        iconBg = MeritColors.SurfaceVariant,
                        iconTint = MeritColors.OnSurfaceVariant,
                        title = "Bedtime Curfew",
                        subtitle = "$bedtimeRange blackout",
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Reassuring note
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MeritColors.Outline,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "You can adjust these limits anytime from the Parent Dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom CTA & 5-second countdown redirection
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Auto-redirect timer bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Opening Parent Dashboard in $countdownSeconds s...",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MeritColors.OnSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = MeritColors.Primary,
                    trackColor = MeritColors.SurfaceContainerHigh,
                )
            }

            Button(
                onClick = onOpenDashboard,
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
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Open Parent Dashboard",
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

@Composable
private fun SummaryRuleRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MeritColors.SurfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
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
                    modifier = Modifier.size(18.dp),
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
    }
}
