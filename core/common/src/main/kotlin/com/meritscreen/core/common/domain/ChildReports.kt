package com.meritscreen.core.common.domain

/**
 * Parent-facing report models (P17). Built from one-shot Firestore reads of
 * `usageDays`, `quizAttempts`, and `skillState/current` — never from a live listener.
 */

data class QuizAttemptSummary(
    val attemptId: String,
    val createdAtEpochMs: Long,
    val topics: List<String>,
    val score: Int,
    val total: Int,
    val passed: Boolean,
    val extraMinutesGranted: Int,
)

data class WeakConceptHint(
    val conceptId: String,
    val title: String,
)

data class TopicSkillSummary(
    val topic: String,
    val level: Int,
    val streakCorrect: Int,
    val weak: Boolean,
    val weakConcepts: List<WeakConceptHint> = emptyList(),
    /** Concepts the adaptive engine has marked mastered (from skillState upload). */
    val masteredConcepts: List<String> = emptyList(),
    /** Tier label from the child engine: basic / intermediate / advanced / mastery. */
    val tierLabel: String = "basic",
)

data class DailyMinutesPoint(
    val day: String,
    val minutes: Int,
    /** Per-app minutes for this calendar day (empty when the day had no usage upload). */
    val byApp: List<AppMinutesRollup> = emptyList(),
)

data class AppMinutesRollup(
    val packageName: String,
    val displayName: String,
    val minutes: Int,
)

data class QuizWindowStats(
    val attemptCount: Int,
    val passedCount: Int,
    val failedCount: Int,
    val accuracyPercent: Int?,
    val questionsCorrect: Int,
    val questionsTotal: Int,
    /** Sum of `extraMinutesGranted` across passed attempts in the window. */
    val extraMinutesEarned: Int,
)

/**
 * Fully aggregated, parent-ready snapshot for one child and one window (Today / 7 / 30 days).
 * The UI renders this without further Firestore awareness.
 */
data class ChildReportsSnapshot(
    val days: Int,
    val totalMinutes: Int,
    /** Calendar average: total ÷ window days (includes zero-usage days). */
    val averageMinutesPerDay: Int,
    /**
     * Half-window screen-time trend as a percent change (second half vs first).
     * Null when the window is too short or both halves are empty.
     */
    val trendPercent: Int?,
    /** Share of app minutes classified educational; null when there is no per-app usage. */
    val educationalPercent: Int?,
    val busiestDay: DailyMinutesPoint?,
    val daily: List<DailyMinutesPoint>,
    val byApp: List<AppMinutesRollup>,
    val quiz: QuizWindowStats,
    val recentAttempts: List<QuizAttemptSummary>,
    val topics: List<TopicSkillSummary>,
    val practiceHints: List<WeakConceptHint>,
    val hasAnyData: Boolean,
    val streakDays: Int = 0,
    val masteryRatePercent: Int? = null,
)
