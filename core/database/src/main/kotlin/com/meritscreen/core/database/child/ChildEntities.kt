package com.meritscreen.core.database.child

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "child_policy")
data class ChildPolicyEntity(
    @PrimaryKey val childId: String,
    val familyId: String,
    val quizMode: String,
    val allowRetryDuringCooldown: Boolean,
    val dailyCeilingMinutes: Int?,
    val questionsPerQuiz: Int,
    val passScorePercent: Int,
    val rewardsEnabled: Boolean,
    val weekendBonusEnabled: Boolean,
    val extraMinutesOnPass: Int,
    val aiQuizzesEnabled: Boolean,
    val adaptiveDifficultyEnabled: Boolean,
    val showExplanations: Boolean,
    val defaultBlockMinutes: Int,
    val defaultCooldownMinutes: Int,
    val emergencyAppsCsv: String,
    val updatedAtEpochMs: Long,
    val gradeStandard: String = "",
    val region: String = "IN",
    val customPromptGuidelines: String = "",
    /** Comma-separated [CurriculumFocusTopic.id] values. */
    val curriculumFocusIdsCsv: String = "",
    /** Parent freeze / pause — device-wide until cleared (docs/07). */
    val paused: Boolean = false,
    val bonusMinutesToday: Int = 0,
)

@Entity(tableName = "child_app_rule")
data class ChildAppRuleEntity(
    @PrimaryKey val appId: String,
    val childId: String,
    val packageOrBundleId: String,
    val displayName: String,
    val allowed: Boolean,
    val blockMinutes: Int,
    val grantOnPassMinutes: Int,
    val cooldownMinutes: Int,
    val isEmergency: Boolean,
)

@Entity(tableName = "child_profile_cache")
data class ChildProfileCacheEntity(
    @PrimaryKey val childId: String,
    val familyId: String,
    val displayName: String,
    val ageBand: String,
    val avatarId: String,
    val language: String,
)

@Entity(tableName = "session_state")
data class SessionStateEntity(
    /** One session row per child profile on a shared device. */
    @PrimaryKey val childId: String,
    val phase: String,
    val activePackage: String?,
    val activeAppId: String?,
    val blockStartedElapsedMs: Long?,
    val blockDurationMinutes: Int,
    val minutesAccruedInBlock: Float,
    val deviceShieldedUntilElapsedMs: Long?,
    val cooldownMinutes: Int,
    val dayKey: String,
    val minutesUsedToday: Int,
    val lastTickElapsedMs: Long?,
    val quizLockEndsAtElapsedMs: Long? = null,
    val quizLockQuestionId: String? = null,
    val quizGraceUntilElapsedMs: Long? = null,
)

@Entity(tableName = "quiz_item")
data class QuizItemEntity(
    @PrimaryKey val id: String,
    val ageBand: String,
    val topic: String,
    val conceptId: String,
    val conceptTitle: String,
    val difficulty: Int,
    val prompt: String,
    val choicesJson: String,
    val whyCorrect: String,
    val whyWrongByChoiceJson: String,
    val conceptExplainer: String,
    val language: String = "en",
    val source: String = "builtin",
    val interactionType: String = "TAP_TEXT",
    val promptTag: String? = null,
    val promptCount: Int? = null,
    val promptAudioKey: String? = null,
    val miniLessonJson: String? = null,
    val packId: String? = null,
)

@Entity(tableName = "skill_state")
data class SkillStateEntity(
    @PrimaryKey val key: String,
    val childId: String,
    val topic: String,
    val conceptId: String?,
    val level: Int,
    val streakCorrect: Int,
    val weak: Boolean,
    /** Comma-separated concept ids currently marked weak for this topic. */
    val weakConceptsCsv: String = "",
    /** JSON map of conceptId → parent-friendly title from the quiz bank. */
    val weakConceptTitlesJson: String = "{}",
    /** Comma-separated concept ids marked mastered. */
    val masteredConceptsCsv: String = "",
    /** Tier classification: basic, intermediate, advanced, mastery. */
    val tierLabel: String = "basic",
    val totalAttempts: Int = 0,
    val totalCorrect: Int = 0,
    val avgResponseTimeMs: Long = 0,
    val lastPackGeneratedAtEpochMs: Long? = null,
    /** Needs upload to `skillState/current` — see `UsageSyncCoordinator` (Phase 7). */
    val dirty: Boolean = true,
    val syncedAtEpochMs: Long? = null,
)

@Entity(tableName = "recent_question", indices = [Index(value = ["childId", "answeredAtEpochMs"])])
data class RecentQuestionEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val childId: String,
    val questionId: String,
    val answeredAtEpochMs: Long,
)
