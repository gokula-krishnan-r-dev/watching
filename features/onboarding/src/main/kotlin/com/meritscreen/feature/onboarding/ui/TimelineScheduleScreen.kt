package com.meritscreen.feature.onboarding.ui

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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.ui.components.ChildAvatar
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import com.meritscreen.feature.onboarding.domain.BedtimeSchedule
import com.meritscreen.feature.onboarding.domain.WeekDay
import kotlinx.serialization.Serializable

@Serializable
data class TimelineScheduleRoute(
    val childName: String = "your child",
    val ageBand: String = "AGE_7_TO_9",
    val avatar: String = "RABBIT",
)

@Composable
fun TimelineScheduleScreen(
    childName: String = "your child",
    avatar: AvatarPreset = AvatarPreset.Default,
    initialBudgetMinutes: Int = 90,
    initialQuizFreqMinutes: Int = 30,
    initialBedtimeEnabled: Boolean = true,
    initialCooldownMinutes: Int = 10,
    stepLabel: String = "Step 2 of 5 • Timeline",
    onContinue: () -> Unit = {},
    onSaveAndContinue: ((
        budgetMinutes: Int,
        quizFreqMinutes: Int,
        bedtimeEnabled: Boolean,
        bedtimeStart: String,
        bedtimeEnd: String,
        cooldownMinutes: Int,
        bedtimeByDay: Map<String, Pair<String, String>>,
    ) -> Unit)? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var budgetMinutes by remember { mutableIntStateOf(initialBudgetMinutes) }
    var quizFreqMinutes by remember { mutableIntStateOf(initialQuizFreqMinutes) }
    var bedtimeEnabled by remember { mutableStateOf(initialBedtimeEnabled) }
    var bedtimeSchedule by remember { mutableStateOf(BedtimeSchedule.defaultSchedule()) }
    var cooldownMinutes by remember { mutableIntStateOf(initialCooldownMinutes) }

    val bedtimeError = remember(bedtimeEnabled, bedtimeSchedule) {
        if (bedtimeEnabled) BedtimeSchedule.validateSchedule(bedtimeSchedule) else null
    }
    val mondayWindow = bedtimeSchedule.getValue(WeekDay.MON)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeritColors.Surface),
    ) {
        // Top Bar
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
                    Text(
                        text = stepLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSecondaryContainer,
                    )
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MeritColors.OutlineVariant),
                    )
                    ChildAvatar(
                        avatar = avatar,
                        size = 22.dp,
                        background = MeritColors.SurfaceContainerLowest,
                        contentDescription = "$childName avatar",
                    )
                    Text(
                        text = "For $childName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = { /* Help info */ }) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MeritColors.OnSurfaceVariant,
                )
            }
        }

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = MeritSpacing.margin)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Column {
                Text(
                    text = "Timeline & Schedule",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Calm, clear guardrails for balance between learning, free discovery, and sleep.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            // Section 1: 24h Day Breakdown Visualizer
            DayBreakdownVisualizerCard()

            // Section 2: Daily Screen Time Budget Card
            DailyScreenBudgetCard(
                budgetMinutes = budgetMinutes,
                onBudgetChanged = { budgetMinutes = it },
            )

            // Section 3: Micro-Learning Pace / Quiz Frequency
            QuizFrequencyCard(
                selectedFreq = quizFreqMinutes,
                onFreqSelected = { quizFreqMinutes = it },
            )

            // Section 4: Bedtime Window Card (day-wise)
            BedtimeWindowCard(
                enabled = bedtimeEnabled,
                onEnabledChanged = { bedtimeEnabled = it },
                schedule = bedtimeSchedule,
                onScheduleChanged = { bedtimeSchedule = it },
                validationError = bedtimeError,
            )

            // Section 5: Cooldown on 3 Failed Questions Card
            CooldownCard(
                selectedCooldown = cooldownMinutes,
                onCooldownSelected = { cooldownMinutes = it },
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom Sticky Action Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
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
                        if (bedtimeError != null) return@Button
                        if (onSaveAndContinue != null) {
                            onSaveAndContinue(
                                budgetMinutes,
                                quizFreqMinutes,
                                bedtimeEnabled,
                                mondayWindow.startLabel,
                                mondayWindow.endLabel,
                                cooldownMinutes,
                                bedtimeSchedule.mapKeys { it.key.key }.mapValues {
                                    it.value.startLabel to it.value.endLabel
                                },
                            )
                        } else {
                            onContinue()
                        }
                    },
                    enabled = bedtimeError == null,
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeritColors.PrimaryContainer,
                        contentColor = Color.White,
                        disabledContainerColor = MeritColors.SurfaceContainerHighest,
                        disabledContentColor = MeritColors.OnSurfaceVariant,
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
                            text = "Continue to QR Pairing",
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
private fun DayBreakdownVisualizerCard() {
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
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "24h Day Breakdown",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )
                }
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Multi-segment horizontal bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MeritColors.SurfaceContainer),
            ) {
                // Bedtime 00:00 - 07:00 (29%)
                Box(
                    modifier = Modifier
                        .weight(29f)
                        .fillMaxSize()
                        .background(MeritColors.InverseSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = MeritColors.InverseOnSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp),
                    )
                }
                // Morning prep (6%)
                Box(
                    modifier = Modifier
                        .weight(6f)
                        .fillMaxSize()
                        .background(MeritColors.SurfaceVariant),
                )
                // School (27%)
                Box(
                    modifier = Modifier
                        .weight(27f)
                        .fillMaxSize()
                        .background(MeritColors.SecondaryContainer),
                )
                // Active Window (23%)
                Box(
                    modifier = Modifier
                        .weight(23f)
                        .fillMaxSize()
                        .background(MeritColors.PrimaryFixed),
                )
                // Evening Bedtime (15%)
                Box(
                    modifier = Modifier
                        .weight(15f)
                        .fillMaxSize()
                        .background(MeritColors.InverseSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = MeritColors.InverseOnSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Time stamps row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                LegendItem(color = MeritColors.InverseSurface, label = "Bedtime")
                LegendItem(color = MeritColors.SecondaryContainer, label = "School")
                LegendItem(color = MeritColors.PrimaryFixed, label = "Active Window")
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MeritColors.OnSurface,
        )
    }
}

@Composable
private fun DailyScreenBudgetCard(
    budgetMinutes: Int,
    onBudgetChanged: (Int) -> Unit,
) {
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
                Column {
                    Text(
                        text = "ACTIVE ALLOWANCE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Text(
                        text = "Daily Screen Budget",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MeritColors.PrimaryFixed,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(16.dp),
                        )
                        val hours = budgetMinutes / 60
                        val mins = budgetMinutes % 60
                        Text(
                            text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.Primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Slider(
                value = budgetMinutes.toFloat(),
                onValueChange = { onBudgetChanged(it.toInt()) },
                valueRange = 30f..180f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = MeritColors.Primary,
                    activeTrackColor = MeritColors.Primary,
                    inactiveTrackColor = MeritColors.SurfaceContainer,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("30m min", style = MaterialTheme.typography.labelSmall, color = MeritColors.OnSurfaceVariant)
                Text("Balanced Range", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MeritColors.Primary)
                Text("3h max", style = MaterialTheme.typography.labelSmall, color = MeritColors.OnSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Preset Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(60 to "1h 00m", 90 to "1h 30m", 120 to "2h 00m").forEach { (mins, label) ->
                    val isSelected = budgetMinutes == mins
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MeritColors.Primary else MeritColors.SurfaceContainerLow,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onBudgetChanged(mins) },
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSelected) Color.White else MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizFrequencyCard(
    selectedFreq: Int,
    onFreqSelected: (Int) -> Unit,
) {
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
                Column {
                    Text(
                        text = "MICRO-LEARNING PACE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Text(
                        text = "Quiz Prompt Frequency",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MeritColors.SecondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MeritColors.OnSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Segmented Control
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(20 to "Every 20m", 30 to "Every 30m", 45 to "Every 45m").forEach { (freq, label) ->
                        val isSelected = selectedFreq == freq
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MeritColors.SurfaceContainerLowest else Color.Transparent,
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onFreqSelected(freq) },
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (isSelected) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Child must answer 3 questions to extend session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CooldownCard(
    selectedCooldown: Int,
    onCooldownSelected: (Int) -> Unit,
) {
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeritColors.SecondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MeritColors.OnSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = "Cooldown on 3 Failed Questions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = "Prevent rapid guessing fatigue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5 to "5 min", 10 to "10 min", 15 to "15 min").forEach { (minutes, label) ->
                    val isSelected = selectedCooldown == minutes
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MeritColors.Primary else MeritColors.SurfaceContainerLow,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCooldownSelected(minutes) },
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
