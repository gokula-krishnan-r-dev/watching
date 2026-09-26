package com.meritscreen.feature.onboarding.domain

/**
 * Per-day bedtime window. Overnight windows (e.g. 8:30 PM → 7:00 AM) are valid:
 * when [endMinutes] <= [startMinutes], the window crosses midnight.
 */
data class BedtimeWindow(
    val startLabel: String,
    val endLabel: String,
) {
    val startMinutes: Int get() = parseTimeLabelToMinutes(startLabel)
    val endMinutes: Int get() = parseTimeLabelToMinutes(endLabel)

    /** Rest duration in minutes, supporting overnight windows. */
    val restMinutes: Int
        get() {
            val start = startMinutes
            val end = endMinutes
            return if (end > start) end - start else (MINUTES_PER_DAY - start) + end
        }

    fun restLabel(): String = formatDuration(restMinutes)
}

enum class WeekDay(val key: String, val shortLabel: String, val fullLabel: String) {
    MON("mon", "Mo", "Monday"),
    TUE("tue", "Tu", "Tuesday"),
    WED("wed", "We", "Wednesday"),
    THU("thu", "Th", "Thursday"),
    FRI("fri", "Fr", "Friday"),
    SAT("sat", "Sa", "Saturday"),
    SUN("sun", "Su", "Sunday"),
    ;

    val isWeekend: Boolean get() = this == SAT || this == SUN
}

enum class BedtimeScheduleMode {
    EVERY_DAY,
    WEEKDAYS_WEEKEND,
    CUSTOM,
}

object BedtimeSchedule {
    val DEFAULT_START = "8:30 PM"
    val DEFAULT_END = "7:00 AM"
    const val MIN_REST_MINUTES = 6 * 60
    const val MAX_REST_MINUTES = 14 * 60

    fun defaultWindow(): BedtimeWindow = BedtimeWindow(DEFAULT_START, DEFAULT_END)

    fun defaultSchedule(): Map<WeekDay, BedtimeWindow> =
        WeekDay.entries.associateWith { defaultWindow() }

    fun applyToAll(schedule: Map<WeekDay, BedtimeWindow>, window: BedtimeWindow): Map<WeekDay, BedtimeWindow> =
        WeekDay.entries.associateWith { window }

    fun applyToWeekdays(schedule: Map<WeekDay, BedtimeWindow>, window: BedtimeWindow): Map<WeekDay, BedtimeWindow> =
        schedule.mapValues { (day, current) -> if (day.isWeekend) current else window }

    fun applyToWeekend(schedule: Map<WeekDay, BedtimeWindow>, window: BedtimeWindow): Map<WeekDay, BedtimeWindow> =
        schedule.mapValues { (day, current) -> if (day.isWeekend) window else current }

    /**
     * Detects whether the schedule is uniform, weekday/weekend split, or fully custom.
     */
    fun detectMode(schedule: Map<WeekDay, BedtimeWindow>): BedtimeScheduleMode {
        val windows = WeekDay.entries.map { schedule.getValue(it) }
        if (windows.all { it.startLabel == windows.first().startLabel && it.endLabel == windows.first().endLabel }) {
            return BedtimeScheduleMode.EVERY_DAY
        }
        val weekday = WeekDay.entries.filterNot { it.isWeekend }.map { schedule.getValue(it) }
        val weekend = WeekDay.entries.filter { it.isWeekend }.map { schedule.getValue(it) }
        val weekdaysSame = weekday.all {
            it.startLabel == weekday.first().startLabel && it.endLabel == weekday.first().endLabel
        }
        val weekendSame = weekend.all {
            it.startLabel == weekend.first().startLabel && it.endLabel == weekend.first().endLabel
        }
        if (weekdaysSame && weekendSame) return BedtimeScheduleMode.WEEKDAYS_WEEKEND
        return BedtimeScheduleMode.CUSTOM
    }

    fun validate(window: BedtimeWindow): String? {
        if (window.startMinutes == window.endMinutes) {
            return "Start and end times must be different."
        }
        val rest = window.restMinutes
        if (rest < MIN_REST_MINUTES) {
            return "Rest window must be at least 6 hours."
        }
        if (rest > MAX_REST_MINUTES) {
            return "Rest window must be 14 hours or less."
        }
        return null
    }

    fun validateSchedule(schedule: Map<WeekDay, BedtimeWindow>): String? {
        for (day in WeekDay.entries) {
            val error = validate(schedule.getValue(day))
            if (error != null) return "${day.fullLabel}: $error"
        }
        return null
    }
}

private const val MINUTES_PER_DAY = 24 * 60

fun parseTimeLabelToMinutes(label: String): Int {
    val cleaned = label.trim().uppercase()
    val match = Regex("""^(\d{1,2}):(\d{2})\s*(AM|PM)$""").matchEntire(cleaned)
        ?: return 0
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt().coerceIn(0, 59)
    val meridiem = match.groupValues[3]
    hour = hour.coerceIn(1, 12)
    val hour24 = when {
        meridiem == "AM" && hour == 12 -> 0
        meridiem == "PM" && hour != 12 -> hour + 12
        else -> hour
    }
    return hour24 * 60 + minute
}

fun formatMinutesToTimeLabel(totalMinutes: Int): String {
    val normalized = ((totalMinutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    val hour24 = normalized / 60
    val minute = normalized % 60
    val meridiem = if (hour24 < 12) "AM" else "PM"
    val hour12 = when (val h = hour24 % 12) {
        0 -> 12
        else -> h
    }
    return "%d:%02d %s".format(hour12, minute, meridiem)
}

fun formatDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
