package com.meritscreen.core.common.domain

/**
 * One day's local-absolute usage snapshot for this device, ready to upload to
 * `usageDays/{day}`. Child devices always upload their own **absolute** running total for
 * the day (never a delta) so a retried upload after a dropped ack is naturally idempotent —
 * see ARCHITECTURE.md "Real-time synchronization (Phase 7)".
 */
data class UsageDayUpload(
    val day: String,
    val minutesUsed: Int,
    val minutesByApp: Map<String, Int>,
)

/** One completed quiz session, uploaded once to the append-only `quizAttempts/{attemptId}`. */
data class QuizAttemptUpload(
    val attemptId: String,
    val createdAtEpochMs: Long,
    val topics: List<String>,
    val score: Int,
    val total: Int,
    val passed: Boolean,
    val extraMinutesGranted: Int,
)

/** One topic's adaptive-quiz skill snapshot, uploaded as part of `skillState/current`. */
data class SkillStateUpload(
    val topic: String,
    val level: Int,
    val streakCorrect: Int,
    val weak: Boolean,
    /** Concept ids the adaptive engine currently marks as needing practice. */
    val weakConcepts: List<String> = emptyList(),
    /** Parent-friendly titles keyed by concept id (from the on-device quiz bank). */
    val weakConceptTitles: Map<String, String> = emptyMap(),
    /** Concepts marked as successfully mastered by the child. */
    val masteredConcepts: List<String> = emptyList(),
    /** Tier classification: basic, intermediate, advanced, mastery. */
    val tierLabel: String = "basic",
    val totalAttempts: Int = 0,
    val totalCorrect: Int = 0,
    val avgResponseTimeMs: Long = 0,
)

