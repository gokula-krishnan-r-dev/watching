package com.meritscreen.feature.parent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.components.MeritPrimaryButton
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import kotlin.math.roundToInt

@Composable
fun TimeLimitsScreen(
    onBack: () -> Unit,
    viewModel: PolicyEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.policy.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }
    when (val current = state) {
        UiState.Loading -> LoadingState()
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(message = current.error.userMessage)
        is UiState.Success -> {
            TimeLimitsContent(
                policy = current.data,
                saving = saving,
                saved = saved,
                error = error,
                onUpdatePolicy = viewModel::update,
                onSave = viewModel::save,
                onBack = onBack,
            )
        }
    }
}

@Composable
fun TimeLimitsContent(
    modifier: Modifier = Modifier,
    policy: ChildPolicy,
    saving: Boolean = false,
    saved: Boolean = false,
    error: String? = null,
    childName: String = "Leo",
    onUpdatePolicy: ((ChildPolicy) -> ChildPolicy) -> Unit = {},
    onSave: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MeritColors.SurfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MeritSpacing.lg, vertical = MeritSpacing.xs),
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MeritColors.OnSurface,
                        )
                    }
                    Column {
                        Text(
                            text = "Time Limits & Fail Lock",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "Configure mindful pacing & boundaries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MeritColors.PrimaryFixed)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(MeritSpacing.sm))

            // 1. Gentle Boundaries Explanation Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(MeritSpacing.md),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MeritColors.PrimaryFixed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Spa,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gentle Boundaries",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Device-wide Fail Lock shields all non-emergency apps if a quiz is failed, encouraging mindful breaks without shame.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.md))

            // 2. Daily Screen Time Ceiling Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerLowest,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(MeritSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MeritColors.SecondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = MeritColors.OnSecondaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Daily Screen Ceiling",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurface,
                                )
                                Text(
                                    text = "System-wide app usage cap",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            }
                        }

                        val hasCeiling = policy.dailyCeilingMinutes != null
                        Switch(
                            checked = hasCeiling,
                            onCheckedChange = { enabled ->
                                onUpdatePolicy { it.copy(dailyCeilingMinutes = if (enabled) 120 else null) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MeritColors.OnPrimary,
                                checkedTrackColor = MeritColors.PrimaryContainer,
                            ),
                        )
                    }

                    val ceilingMinutes = policy.dailyCeilingMinutes ?: 120
                    val hours = ceilingMinutes / 60
                    val mins = ceilingMinutes % 60
                    val ceilingText = if (hours > 0) "${hours}h ${if (mins > 0) "${mins}m" else "00m"}" else "${mins}m"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MeritSpacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (policy.dailyCeilingMinutes != null) ceilingText else "No Cap",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (policy.dailyCeilingMinutes != null) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                            )
                            if (policy.dailyCeilingMinutes != null) {
                                Text(
                                    text = " / day",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MeritColors.OnSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp),
                                )
                            }
                        }

                        if (policy.dailyCeilingMinutes != null) {
                            Slider(
                                value = ceilingMinutes.toFloat(),
                                onValueChange = { onUpdatePolicy { p -> p.copy(dailyCeilingMinutes = it.roundToInt()) } },
                                valueRange = 30f..240f,
                                steps = 13,
                                colors = SliderDefaults.colors(
                                    thumbColor = MeritColors.Primary,
                                    activeTrackColor = MeritColors.Primary,
                                    inactiveTrackColor = MeritColors.SurfaceContainerHigh,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("30m", style = MaterialTheme.typography.labelSmall, color = MeritColors.OnSurfaceVariant)
                                Text("2h", style = MaterialTheme.typography.labelSmall, color = MeritColors.OnSurfaceVariant)
                                Text("4h Max", style = MaterialTheme.typography.labelSmall, color = MeritColors.OnSurfaceVariant)
                            }
                        }
                    }

                    // Schedule Breakdown Tile
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MeritColors.SurfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MeritColors.Secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Split Schedule",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MeritColors.OnSurface,
                                )
                            }
                            Text(
                                text = "Weekdays: 1h 30m • Weekends: 2h 30m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.md))

            // 3. Session & Pacing Rules
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerLowest,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(MeritSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MeritColors.SecondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timelapse,
                                contentDescription = null,
                                tint = MeritColors.OnSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "Session & Pacing Rules",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MeritColors.OnSurface,
                            )
                            Text(
                                text = "Prevent passive binge consumption",
                                style = MaterialTheme.typography.labelSmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                    }

                    SessionChunkPicker(
                        selectedMinutes = policy.defaultBlockMinutes,
                        onSelectMinutes = { minutes ->
                            onUpdatePolicy { it.copy(defaultBlockMinutes = minutes) }
                        },
                    )

                    // Prerequisite Study Time
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MeritColors.SurfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Prerequisite Study Time",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MeritColors.OnSurface,
                                )
                                Text(
                                    text = "Play apps locked until completed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MeritColors.SurfaceContainerLowest,
                                shadowElevation = 1.dp,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = null,
                                        tint = MeritColors.Primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "15 Min",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MeritColors.Primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.md))

            // 4. Fail Lock Shield (Mindful Cooldown)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerLowest,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(MeritSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MeritColors.SecondaryFixed),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = MeritColors.OnSecondaryFixed,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Fail Lock Shield",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurface,
                                )
                                Text(
                                    text = "Post-quiz mindfulness cool-off",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MeritColors.TertiaryFixed)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Mindful Mode",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MeritColors.OnTertiaryFixedVariant,
                            )
                        }
                    }

                    Text(
                        text = "Fail Cooldown Duration",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MeritColors.OnSurface,
                    )

                    // Cooldown Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(10, 15, 20, 30).forEach { mins ->
                            val isSelected = policy.defaultCooldownMinutes == mins
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) MeritColors.Primary else MeritColors.SurfaceContainerLow,
                                shadowElevation = if (isSelected) 1.dp else 0.dp,
                                modifier = Modifier.clickable { onUpdatePolicy { it.copy(defaultCooldownMinutes = mins) } },
                            ) {
                                Text(
                                    text = "${mins}m",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) MeritColors.OnPrimary else MeritColors.OnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    // Emergency Pass-through Guarantee
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MeritColors.SurfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Column {
                                Text(
                                    text = "Emergency Pass-through",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurface,
                                )
                                Text(
                                    text = "Phone app and pre-approved family emergency contacts always remain accessible during Fail Lock.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.md))

            // 5. Bedtime Downtime Schedule
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerLowest,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(MeritSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MeritColors.SecondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = MeritColors.OnSecondaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Bedtime Downtime",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurface,
                                )
                                Text(
                                    text = "Nightly wind-down schedule",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MeritColors.SurfaceContainerLow,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "STARTS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurfaceVariant,
                                )
                                Text(
                                    text = "8:00 PM",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurface,
                                )
                                Text(
                                    text = "Soft dimming begins",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MeritColors.SurfaceContainerLow,
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "ENDS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurfaceVariant,
                                )
                                Text(
                                    text = "7:00 AM",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurface,
                                )
                                Text(
                                    text = "Morning unlock",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Weekend Shift Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Weekend shift (+1 hr bedtime on Fri & Sat)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                        Switch(
                            checked = policy.weekendBonusEnabled,
                            onCheckedChange = { onUpdatePolicy { p -> p.copy(weekendBonusEnabled = it) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MeritColors.OnPrimary,
                                checkedTrackColor = MeritColors.PrimaryContainer,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.lg))

            // Error / Saved feedback
            AnimatedVisibility(visible = error != null) {
                Text(
                    text = error.orEmpty(),
                    color = MeritColors.Error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = MeritSpacing.sm),
                )
            }
            AnimatedVisibility(visible = saved) {
                Text(
                    text = "Rules safely synced with $childName's device",
                    color = MeritColors.Primary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(bottom = MeritSpacing.sm),
                )
            }

            // 6. Save Time Rules Button
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MeritColors.Primary,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable(enabled = !saving, onClick = onSave),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            color = MeritColors.OnPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Saving Rules...",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MeritColors.OnPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Save Time Rules",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.xl))
        }
    }
}

@Composable
fun RewardsScreen(
    onBack: () -> Unit,
    viewModel: PolicyEditorViewModel = hiltViewModel(),
) {
    PolicyEditorScaffold(title = "Rewards", onBack = onBack, viewModel = viewModel) { policy, update ->
        SwitchRow("Celebrate passes with stickers / cheer", policy.rewardsEnabled) { enabled ->
            update { it.copy(rewardsEnabled = enabled) }
        }
        SwitchRow("Weekend bonus", policy.weekendBonusEnabled) { enabled ->
            update { it.copy(weekendBonusEnabled = enabled) }
        }
        Spacer(Modifier.height(MeritSpacing.md))
        ParentSectionLabel("Extra minutes on pass")
        MinuteSlider(
            label = if (policy.extraMinutesOnPass == 0) {
                "No extra minutes"
            } else {
                "+${policy.extraMinutesOnPass} min next block"
            },
            value = policy.extraMinutesOnPass,
            range = 0f..30f,
            steps = 5,
            onChange = { n -> update { it.copy(extraMinutesOnPass = n) } },
        )
        ParentHelperText("Reward your child with bonus app time whenever they successfully complete an educational quiz.")
        Spacer(Modifier.height(MeritSpacing.md))
        ParentSectionLabel("Sticker stages")
        ParentHelperText(
            "Stickers unlock in five stages as Explorer level grows: " +
                "Sprout (1–2), Explorer (3–4), Trailblazer (5–6), Champion (7–8), Legend (9–10). " +
                "Children collect them in their Sticker Book after quiz passes. " +
                "Weekend bonus adds cheer XP and minutes when enabled.",
        )
    }
}

@Composable
private fun PolicyEditorScaffold(
    title: String,
    onBack: () -> Unit,
    viewModel: PolicyEditorViewModel,
    content: @Composable (ChildPolicy, (transform: (ChildPolicy) -> ChildPolicy) -> Unit) -> Unit,
) {
    val state by viewModel.policy.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }
    when (val current = state) {
        UiState.Loading -> LoadingState()
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(message = current.error.userMessage)
        is UiState.Success -> ParentScreenScaffold(title = title, onBack = onBack) {
            content(current.data, viewModel::update)
            Spacer(Modifier.height(MeritSpacing.lg))
            if (error != null) {
                Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(MeritSpacing.sm))
            }
            MeritPrimaryButton(
                text = "Save",
                onClick = viewModel::save,
                loading = saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SessionChunkPicker(
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit,
) {
    val clamped = selectedMinutes.coerceIn(
        AppConfig.SESSION_CHUNK_MIN_MINUTES,
        AppConfig.SESSION_CHUNK_MAX_MINUTES,
    )
    val allKnown = (
        AppConfig.SESSION_CHUNK_STANDARD_MINUTES +
            AppConfig.SESSION_CHUNK_CUSHION_MINUTES +
            AppConfig.SESSION_CHUNK_EXTENDED_MINUTES
        ).toSet()

    Column(verticalArrangement = Arrangement.spacedBy(MeritSpacing.sm)) {
        Column {
            Text(
                text = "Default Session Chunk",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MeritColors.OnSurface,
            )
            Text(
                text = "Shows a cushion pop-up when the timer ends — stretch, look away, then continue or quiz.",
                style = MaterialTheme.typography.labelSmall,
                color = MeritColors.OnSurfaceVariant,
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MeritColors.Primary.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Spa,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Active cushion: every $clamped min",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.Primary,
                )
            }
        }

        SessionChunkChipGroup(
            title = "Standard sessions",
            subtitle = "Typical play blocks",
            options = AppConfig.SESSION_CHUNK_STANDARD_MINUTES,
            selectedMinutes = clamped,
            equalWeight = true,
            onSelect = onSelectMinutes,
        )

        SessionChunkChipGroup(
            title = "Quick cushion",
            subtitle = "Short posture check — 2, 3, or 5 minutes",
            options = AppConfig.SESSION_CHUNK_CUSHION_MINUTES,
            selectedMinutes = clamped,
            equalWeight = true,
            onSelect = onSelectMinutes,
        )

        SessionChunkChipGroup(
            title = "Custom length",
            subtitle = "Extra timers including 50 min sessions",
            options = AppConfig.SESSION_CHUNK_EXTENDED_MINUTES,
            selectedMinutes = clamped,
            equalWeight = true,
            onSelect = onSelectMinutes,
        )

        if (clamped !in allKnown) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Saved custom value",
                        style = MaterialTheme.typography.labelMedium,
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MeritColors.SurfaceContainerLowest,
                        shadowElevation = 1.dp,
                    ) {
                        Text(
                            text = "$clamped min",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.Primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionChunkChipGroup(
    title: String,
    subtitle: String,
    options: List<Int>,
    selectedMinutes: Int,
    equalWeight: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MeritColors.OnSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MeritColors.SurfaceContainerLow, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { minutes ->
                val isSelected = selectedMinutes == minutes
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MeritColors.SurfaceContainerLowest else Color.Transparent,
                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                    modifier = Modifier
                        .then(if (equalWeight) Modifier.weight(1f) else Modifier)
                        .clickable { onSelect(minutes) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$minutes min",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (isSelected) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    Spacer(Modifier.height(MeritSpacing.sm))
}

@Composable
private fun MinuteSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Int) -> Unit,
) {
    Text(text = label, style = MaterialTheme.typography.bodyLarge)
    Slider(
        value = value.toFloat().coerceIn(range.start, range.endInclusive),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = range,
        steps = steps,
    )
}
