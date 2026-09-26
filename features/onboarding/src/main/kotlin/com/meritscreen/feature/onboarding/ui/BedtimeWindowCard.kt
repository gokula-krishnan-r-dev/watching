package com.meritscreen.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.feature.onboarding.domain.BedtimeSchedule
import com.meritscreen.feature.onboarding.domain.BedtimeScheduleMode
import com.meritscreen.feature.onboarding.domain.BedtimeWindow
import com.meritscreen.feature.onboarding.domain.WeekDay
import com.meritscreen.feature.onboarding.domain.formatMinutesToTimeLabel

private enum class BedtimeEditField { Start, End }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BedtimeWindowCard(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    schedule: Map<WeekDay, BedtimeWindow>,
    onScheduleChanged: (Map<WeekDay, BedtimeWindow>) -> Unit,
    validationError: String?,
) {
    var selectedDay by remember { mutableStateOf(WeekDay.MON) }
    var editingField by remember { mutableStateOf<BedtimeEditField?>(null) }
    val selectedWindow = schedule.getValue(selectedDay)
    val mode = remember(schedule) { BedtimeSchedule.detectMode(schedule) }

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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MeritColors.InverseSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = MeritColors.InverseOnSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Bedtime Window",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "Set start & end times by day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MeritColors.Primary,
                    ),
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Schedule mode",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScheduleModeChip(
                        label = "Every day",
                        selected = mode == BedtimeScheduleMode.EVERY_DAY,
                        onClick = {
                            onScheduleChanged(BedtimeSchedule.applyToAll(schedule, selectedWindow))
                        },
                    )
                    ScheduleModeChip(
                        label = "Weekdays / Weekend",
                        selected = mode == BedtimeScheduleMode.WEEKDAYS_WEEKEND,
                        onClick = {
                            val weekday = schedule.getValue(WeekDay.MON)
                            val weekend = schedule.getValue(WeekDay.SAT).let { sat ->
                                if (sat == weekday) BedtimeWindow("9:30 PM", "8:00 AM") else sat
                            }
                            onScheduleChanged(
                                BedtimeSchedule.applyToWeekend(
                                    BedtimeSchedule.applyToWeekdays(schedule, weekday),
                                    weekend,
                                ),
                            )
                            selectedDay = WeekDay.MON
                        },
                    )
                    ScheduleModeChip(
                        label = "Custom days",
                        selected = mode == BedtimeScheduleMode.CUSTOM,
                        onClick = { /* editing individual days enters custom mode */ },
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Day",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WeekDay.entries.forEach { day ->
                        val dayWindow = schedule.getValue(day)
                        val differsFromMon = dayWindow != schedule.getValue(WeekDay.MON)
                        DayChip(
                            day = day,
                            selected = selectedDay == day,
                            highlighted = differsFromMon && mode != BedtimeScheduleMode.EVERY_DAY,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedDay = day },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = selectedDay.fullLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EditableTimeBox(
                        label = "Starts (Night)",
                        time = selectedWindow.startLabel,
                        modifier = Modifier.weight(1f),
                        onClick = { editingField = BedtimeEditField.Start },
                    )
                    EditableTimeBox(
                        label = "Ends (Morning)",
                        time = selectedWindow.endLabel,
                        modifier = Modifier.weight(1f),
                        onClick = { editingField = BedtimeEditField.End },
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ApplyChip(
                        label = "Apply to all days",
                        onClick = {
                            onScheduleChanged(BedtimeSchedule.applyToAll(schedule, selectedWindow))
                        },
                    )
                    ApplyChip(
                        label = "Apply to weekdays",
                        onClick = {
                            onScheduleChanged(BedtimeSchedule.applyToWeekdays(schedule, selectedWindow))
                        },
                    )
                    ApplyChip(
                        label = "Apply to weekend",
                        onClick = {
                            onScheduleChanged(BedtimeSchedule.applyToWeekend(schedule, selectedWindow))
                        },
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (validationError == null) MeritColors.Primary
                                    else MaterialTheme.colorScheme.error,
                                ),
                        )
                        Text(
                            text = if (validationError == null) {
                                "Bedtime lock • ${selectedWindow.restLabel()} rest"
                            } else {
                                "Fix bedtime times to continue"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (validationError == null) {
                                MeritColors.OnSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    Text(
                        text = modeSummary(mode, schedule),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.Primary,
                    )
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = validationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    val field = editingField
    if (field != null) {
        val initialMinutes = when (field) {
            BedtimeEditField.Start -> selectedWindow.startMinutes
            BedtimeEditField.End -> selectedWindow.endMinutes
        }
        BedtimeTimePickerDialog(
            title = when (field) {
                BedtimeEditField.Start -> "Bedtime starts"
                BedtimeEditField.End -> "Bedtime ends"
            },
            initialHour = (initialMinutes / 60) % 24,
            initialMinute = initialMinutes % 60,
            onDismiss = { editingField = null },
            onConfirm = { hour, minute ->
                val label = formatMinutesToTimeLabel(hour * 60 + minute)
                val updated = when (field) {
                    BedtimeEditField.Start -> selectedWindow.copy(startLabel = label)
                    BedtimeEditField.End -> selectedWindow.copy(endLabel = label)
                }
                onScheduleChanged(schedule + (selectedDay to updated))
                editingField = null
            },
        )
    }
}

private fun modeSummary(
    mode: BedtimeScheduleMode,
    schedule: Map<WeekDay, BedtimeWindow>,
): String = when (mode) {
    BedtimeScheduleMode.EVERY_DAY -> "Same all week"
    BedtimeScheduleMode.WEEKDAYS_WEEKEND -> {
        val weekday = schedule.getValue(WeekDay.MON).restLabel()
        val weekend = schedule.getValue(WeekDay.SAT).restLabel()
        "Weekday $weekday · Weekend $weekend"
    }
    BedtimeScheduleMode.CUSTOM -> "Custom by day"
}

@Composable
private fun ScheduleModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MeritColors.Primary else MeritColors.SurfaceContainerLow,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Color.White else MeritColors.OnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DayChip(
    day: WeekDay,
    selected: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        selected -> MeritColors.Primary
        highlighted -> MeritColors.Tertiary
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> MeritColors.Primary
                    highlighted -> MeritColors.Tertiary.copy(alpha = 0.15f)
                    else -> MeritColors.SurfaceContainerLow
                },
            )
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.shortLabel,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (selected) Color.White else MeritColors.OnSurface,
        )
    }
}

@Composable
private fun ApplyChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MeritColors.SecondaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MeritColors.OnSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedtimeTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EditableTimeBox(
    label: String,
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLow,
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MeritColors.OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                )
                Icon(
                    imageVector = Icons.Default.EditCalendar,
                    contentDescription = "Edit time",
                    tint = MeritColors.OnSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
