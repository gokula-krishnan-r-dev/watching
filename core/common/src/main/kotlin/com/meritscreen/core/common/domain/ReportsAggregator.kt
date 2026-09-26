package com.meritscreen.core.common.domain

import com.meritscreen.core.common.config.AppConfig
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure aggregation for parent reports (P17). No Android / Firebase dependencies —
 * unit-tested in isolation so ViewModels stay thin.
 */
object ReportsAggregator {

    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun build(
        days: Int,
        usage: List<UsageDaySummary>,
        attempts: List<QuizAttemptSummary>,
        skills: List<TopicSkillSummary>,
        appLabels: Map<String, String> = emptyMap(),
        today: LocalDate = LocalDate.now(ZoneOffset.UTC),
        recentAttemptLimit: Int = AppConfig.REPORTS_RECENT_ATTEMPTS,
    ): ChildReportsSnapshot {
        val windowDays = days.coerceIn(1, AppConfig.REPORTS_MAX_DAYS)
        val windowStart = today.minusDays((windowDays - 1).toLong())
        val usageInWindow = usage.filter { day ->
            parseDay(day.day)?.let { !it.isBefore(windowStart) && !it.isAfter(today) } == true
        }
        val attemptsInWindow = attempts.filter { attempt ->
            val attemptDay = Instant.ofEpochMilli(attempt.createdAtEpochMs)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            !attemptDay.isBefore(windowStart) && !attemptDay.isAfter(today)
        }

        val byDayRaw = usageInWindow.associateBy { it.day }
        val daily = (0 until windowDays).map { offset ->
            val day = windowStart.plusDays(offset.toLong())
            val key = day.format(dayFormatter)
            val summary = byDayRaw[key]
            val dayApps = rollupDayApps(summary?.minutesByApp.orEmpty(), appLabels)
            DailyMinutesPoint(
                day = key,
                minutes = summary?.minutesUsed ?: 0,
                byApp = dayApps,
            )
        }
        val totalMinutes = daily.sumOf { it.minutes }
        val average = calendarAverageMinutes(totalMinutes, windowDays)
        val busiest = daily.filter { it.minutes > 0 }.maxByOrNull { it.minutes }

        val byAppMinutes = linkedMapOf<String, Int>()
        usageInWindow.forEach { day ->
            day.minutesByApp.forEach { (pkg, minutes) ->
                if (minutes > 0 && pkg.isNotBlank()) {
                    byAppMinutes[pkg] = (byAppMinutes[pkg] ?: 0) + minutes
                }
            }
        }
        var byApp = byAppMinutes.entries
            .sortedByDescending { it.value }
            .map { (pkg, minutes) ->
                AppMinutesRollup(
                    packageName = pkg,
                    displayName = resolveAppLabel(pkg, appLabels),
                    minutes = minutes,
                )
            }
        val byAppSum = byApp.sumOf { it.minutes }
        val unattributed = (totalMinutes - byAppSum).coerceAtLeast(0)
        if (unattributed > 0) {
            byApp = byApp + AppMinutesRollup(
                packageName = "_other",
                displayName = "Other / unattributed",
                minutes = unattributed,
            )
        }

        val passed = attemptsInWindow.count { it.passed }
        val failed = attemptsInWindow.size - passed
        val questionsCorrect = attemptsInWindow.sumOf { it.score }
        val questionsTotal = attemptsInWindow.sumOf { it.total }
        val accuracy = when {
            attemptsInWindow.isEmpty() -> null
            else -> (passed * 100 + attemptsInWindow.size / 2) / attemptsInWindow.size
        }
        val quiz = QuizWindowStats(
            attemptCount = attemptsInWindow.size,
            passedCount = passed,
            failedCount = failed,
            accuracyPercent = accuracy,
            questionsCorrect = questionsCorrect,
            questionsTotal = questionsTotal,
            extraMinutesEarned = attemptsInWindow.filter { it.passed }.sumOf { it.extraMinutesGranted },
        )

        val topics = skills.sortedWith(
            compareByDescending<TopicSkillSummary> { it.weak }
                .thenByDescending { it.level }
                .thenBy { it.topic },
        )
        val practiceHints = topics
            .flatMap { it.weakConcepts }
            .distinctBy { it.conceptId }
            .take(8)

        return ChildReportsSnapshot(
            days = windowDays,
            totalMinutes = totalMinutes,
            averageMinutesPerDay = average,
            trendPercent = trendPercent(daily),
            educationalPercent = educationalPercent(byApp.filter { it.packageName != "_other" }),
            busiestDay = busiest,
            daily = daily,
            byApp = byApp,
            quiz = quiz,
            recentAttempts = attemptsInWindow
                .sortedByDescending { it.createdAtEpochMs }
                .take(recentAttemptLimit.coerceAtLeast(0)),
            topics = topics,
            practiceHints = practiceHints,
            hasAnyData = totalMinutes > 0 || attemptsInWindow.isNotEmpty() || skills.isNotEmpty(),
            streakDays = calculateStreakDays(daily, attemptsInWindow),
            masteryRatePercent = calculateMasteryRate(skills, quiz),
        )
    }

    fun calculateStreakDays(daily: List<DailyMinutesPoint>, attempts: List<QuizAttemptSummary>): Int {
        if (daily.isEmpty()) return 0
        val activeDays = daily.filter { it.minutes > 0 }.map { it.day }.toSet()
        val quizDays = attempts.filter { it.passed }.map {
            Instant.ofEpochMilli(it.createdAtEpochMs).atZone(ZoneOffset.UTC).toLocalDate().format(dayFormatter)
        }.toSet()
        val allActiveDays = activeDays + quizDays
        val daysReversed = daily.map { it.day }.reversed()
        if (daysReversed.isEmpty()) return 0

        val todayStr = daysReversed[0]
        val todayActive = todayStr in allActiveDays
        val startIndex = if (todayActive) 0 else 1
        var streak = 0
        for (i in startIndex until daysReversed.size) {
            if (daysReversed[i] in allActiveDays) {
                streak++
            } else {
                break
            }
        }
        return streak
    }

    fun calculateMasteryRate(skills: List<TopicSkillSummary>, quiz: QuizWindowStats): Int? {
        val totalMastered = skills.sumOf { it.masteredConcepts.size }
        val totalWeak = skills.sumOf { it.weakConcepts.size }
        val totalConcepts = totalMastered + totalWeak
        if (totalConcepts > 0) {
            return ((totalMastered * 100) / totalConcepts).coerceIn(0, 100)
        }
        if (skills.isNotEmpty()) {
            val avgLevel = skills.map { it.level.coerceIn(1, 5) }.average()
            return ((avgLevel / 5.0) * 100).toInt().coerceIn(0, 100)
        }
        return quiz.accuracyPercent
    }

    fun calendarAverageMinutes(totalMinutes: Int, days: Int): Int {
        if (days <= 0) return 0
        return (totalMinutes + days / 2) / days
    }

    /**
     * Percent change of screen time in the second half of [daily] vs the first half.
     * Null when the series is too short or both halves are empty.
     */
    fun trendPercent(daily: List<DailyMinutesPoint>): Int? {
        if (daily.size < 4) return null
        val half = daily.size / 2
        val firstHalf = daily.take(half).sumOf { it.minutes }
        val secondHalf = daily.takeLast(half).sumOf { it.minutes }
        if (firstHalf == 0 && secondHalf == 0) return null
        if (firstHalf == 0) return null
        return ((secondHalf - firstHalf) * 100) / firstHalf
    }

    /** Educational share of attributed app minutes; null when there is nothing to classify. */
    fun educationalPercent(byApp: List<AppMinutesRollup>): Int? {
        val attributable = byApp.filter { it.packageName != "_other" && it.minutes > 0 }
        val total = attributable.sumOf { it.minutes }
        if (total <= 0) return null
        val edu = attributable
            .filter {
                AppInventoryCategorizer.classify(it.packageName, it.displayName).category ==
                    AppInventoryCategorizer.Category.EDUCATIONAL
            }
            .sumOf { it.minutes }
        return (edu * 100 + total / 2) / total
    }

    fun resolveAppLabel(packageName: String, labels: Map<String, String>): String {
        labels[packageName]?.takeIf { it.isNotBlank() }?.let { return it }
        val leaf = packageName.substringAfterLast('.').ifBlank { packageName }
        return humanizeToken(leaf)
    }

    /** `add-within-20` → "Add within 20"; `math` → "Math". */
    fun humanizeToken(raw: String): String {
        if (raw.isBlank()) return raw
        return raw
            .replace('_', ' ')
            .replace('-', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
    }

    fun topicLabel(topic: String): String = humanizeToken(topic)

    fun levelLabel(level: Int): String {
        val clamped = level.coerceIn(1, 5)
        val tone = when (clamped) {
            1 -> "Getting started"
            2 -> "Building"
            3 -> "On track"
            4 -> "Strong"
            else -> "Advanced"
        }
        return "Level $clamped of 5 · $tone"
    }

    fun shortDayLabel(day: String): String {
        val parsed = parseDay(day) ?: return day.takeLast(5)
        return parsed.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    }

    fun friendlyDayLabel(day: String): String {
        val parsed = parseDay(day) ?: return day
        val month = parsed.month.getDisplayName(TextStyle.SHORT, Locale.US)
        return "$month ${parsed.dayOfMonth}"
    }

    private fun rollupDayApps(
        minutesByApp: Map<String, Int>,
        appLabels: Map<String, String>,
    ): List<AppMinutesRollup> =
        minutesByApp.entries
            .filter { it.key.isNotBlank() && it.value > 0 }
            .sortedByDescending { it.value }
            .map { (pkg, minutes) ->
                AppMinutesRollup(
                    packageName = pkg,
                    displayName = resolveAppLabel(pkg, appLabels),
                    minutes = minutes,
                )
            }

    private fun parseDay(day: String): LocalDate? =
        runCatching { LocalDate.parse(day, dayFormatter) }.getOrNull()
}
