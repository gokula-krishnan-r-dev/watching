package com.meritscreen.core.common.domain

import com.meritscreen.core.common.config.AppConfig
import kotlinx.serialization.Serializable

/** When a quiz interrupt fires. Matches `policy/current.quizMode` in Firestore. */
@Serializable
enum class QuizMode(val displayLabel: String, val description: String) {
    APP_BLOCK(
        displayLabel = "After each app block",
        description = "Recommended. When an app's 15 minutes end, a short quiz unlocks another block.",
    ),
    EVERY_SESSION(
        displayLabel = "Every session",
        description = "A quiz when the child opens MeritScreen for a new session.",
    ),
    DAILY_CEILING(
        displayLabel = "At the daily ceiling",
        description = "Quiz only when the optional daily minute cap is reached.",
    ),
    ;

    companion object {
        fun fromStorage(raw: String?): QuizMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.storageKey == raw }
                ?: APP_BLOCK
    }

    val storageKey: String
        get() = when (this) {
            APP_BLOCK -> "app_block"
            EVERY_SESSION -> "every_session"
            DAILY_CEILING -> "daily_ceiling"
        }
}

@Serializable
enum class LearningRegion(val storageKey: String, val displayLabel: String) {
    IN("IN", "India"),
    US("US", "United States"),
    UK("UK", "United Kingdom"),
    AU("AU", "Australia"),
    CA("CA", "Canada"),
    OTHER("OTHER", "Other"),
    ;

    companion object {
        fun fromStorage(raw: String?): LearningRegion =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.storageKey.equals(raw, ignoreCase = true) }
                ?: IN
    }
}

@Serializable
data class LearningProfile(
    val gradeStandard: String = "",
    val region: LearningRegion = LearningRegion.IN,
    val customPromptGuidelines: String = "",
)

/**
 * Child-level policy document (`families/.../policy/current`). Fail lock is always
 * device-wide for all non-emergency apps — that is not configurable.
 */
@Serializable
data class ChildPolicy(
    val quizMode: QuizMode = QuizMode.APP_BLOCK,
    val allowRetryDuringCooldown: Boolean = false,
    val dailyCeilingMinutes: Int? = null,
    val questionsPerQuiz: Int = 3,
    val passScorePercent: Int = 70,
    val rewardsEnabled: Boolean = true,
    val weekendBonusEnabled: Boolean = false,
    val extraMinutesOnPass: Int = 0,
    val aiQuizzesEnabled: Boolean = true,
    val adaptiveDifficultyEnabled: Boolean = true,
    val showExplanations: Boolean = true,
    /** Play-block length before the cushion / quiz interrupt (minutes). Supports quick cushions (2–5). */
    val defaultBlockMinutes: Int = AppConfig.DEFAULT_BLOCK_MINUTES,
    /** Fail-lock / rest duration after a failed quiz (minutes). */
    val defaultCooldownMinutes: Int = 15,
    val emergencyApps: List<String> = listOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.server.telecom",
    ),
    val paused: Boolean = false,
    val bonusMinutesToday: Int = 0,
    val gradeStandard: String = "",
    val region: LearningRegion = LearningRegion.IN,
    val customPromptGuidelines: String = "",
    /** Stable [CurriculumFocusTopic.id] values for the child's age band. */
    val curriculumFocusIds: List<String> = emptyList(),
    // Nursery early-learner curriculum (docs/09)
    val nurseryTeachMode: String = "TEACH_ONLY",
    val nurseryVideoEnabled: Boolean = true,
    val nurseryVideoCadenceMinutes: Int = 30,
    val nurseryVideoPlayWithSound: Boolean = true,
    val nurseryPlaylistId: String = "all",
    val nurseryCheckEnabled: Boolean = false,
)

/** Per-app allowlist rule (`appRules/{appId}`). */
@Serializable
data class AppRule(
    val appId: String,
    val packageOrBundleId: String,
    val displayName: String = "",
    val allowed: Boolean = true,
    val blockMinutes: Int = AppConfig.DEFAULT_BLOCK_MINUTES,
    val grantOnPassMinutes: Int = AppConfig.DEFAULT_BLOCK_MINUTES,
    val cooldownMinutes: Int = 15,
    val isEmergency: Boolean = false,
)

@Serializable
data class UsageDaySummary(
    val day: String,
    val minutesUsed: Int,
    val minutesByApp: Map<String, Int> = emptyMap(),
)

@Serializable
data class DeviceSummary(
    val deviceId: String,
    val platform: String = "android",
    val revoked: Boolean = false,
    val lastSeenAtEpochMs: Long? = null,
    val launcherDefault: Boolean? = null,
    val model: String? = null,
    /** Last reported battery 0–100 from the child heartbeat; null when never reported. */
    val batteryPercent: Int? = null,
    /** e.g. "Android 14" from the child heartbeat. */
    val osVersion: String? = null,
    /** MeritScreen app versionName from the child heartbeat; null until first report. */
    val appVersion: String? = null,
)
