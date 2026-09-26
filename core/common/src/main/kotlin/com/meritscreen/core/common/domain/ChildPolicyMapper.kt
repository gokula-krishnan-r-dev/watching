package com.meritscreen.core.common.domain

import com.meritscreen.core.common.config.AppConfig

/** Maps `ChildPolicy` ↔ Firestore `policy/current` field maps (no Firebase types). */
object ChildPolicyMapper {
    fun toMap(policy: ChildPolicy): Map<String, Any?> = mapOf(
        "quizMode" to policy.quizMode.storageKey,
        "failLockScope" to "all_non_emergency",
        "allowRetryDuringCooldown" to policy.allowRetryDuringCooldown,
        "dailyCeilingMinutes" to policy.dailyCeilingMinutes,
        "questionsPerQuiz" to policy.questionsPerQuiz.coerceIn(3, 5),
        "passScorePercent" to policy.passScorePercent.coerceIn(50, 100),
        "rewardsEnabled" to policy.rewardsEnabled,
        "weekendBonusEnabled" to policy.weekendBonusEnabled,
        "extraMinutesOnPass" to policy.extraMinutesOnPass.coerceIn(0, 60),
        "aiQuizzesEnabled" to policy.aiQuizzesEnabled,
        "adaptiveDifficultyEnabled" to policy.adaptiveDifficultyEnabled,
        "showExplanations" to policy.showExplanations,
        "defaultBlockMinutes" to policy.defaultBlockMinutes.coerceIn(
            AppConfig.SESSION_CHUNK_MIN_MINUTES,
            AppConfig.SESSION_CHUNK_MAX_MINUTES,
        ),
        "defaultCooldownMinutes" to policy.defaultCooldownMinutes.coerceIn(1, 180),
        "emergencyApps" to policy.emergencyApps,
        "paused" to policy.paused,
        "bonusMinutesToday" to policy.bonusMinutesToday,
        "gradeStandard" to policy.gradeStandard,
        "region" to policy.region.storageKey,
        "customPromptGuidelines" to policy.customPromptGuidelines,
        "curriculumFocusIds" to policy.curriculumFocusIds,
        "nurseryTeachMode" to policy.nurseryTeachMode,
        "nurseryVideoEnabled" to policy.nurseryVideoEnabled,
        "nurseryVideoCadenceMinutes" to policy.nurseryVideoCadenceMinutes,
        "nurseryVideoPlayWithSound" to policy.nurseryVideoPlayWithSound,
        "nurseryPlaylistId" to policy.nurseryPlaylistId,
        "nurseryCheckEnabled" to policy.nurseryCheckEnabled,
    )

    fun fromMap(data: Map<String, Any?>): ChildPolicy {
        fun intOr(key: String, default: Int): Int =
            (data[key] as? Number)?.toInt() ?: default

        fun boolOr(key: String, default: Boolean): Boolean =
            data[key] as? Boolean ?: default

        fun stringOr(key: String, default: String): String =
            (data[key] as? String)?.trim() ?: default

        @Suppress("UNCHECKED_CAST")
        val emergency = (data["emergencyApps"] as? List<*>)?.mapNotNull { it as? String }
            ?: ChildPolicy().emergencyApps

        val region = (data["region"] as? String)?.let { LearningRegion.fromStorage(it) }
            ?: LearningRegion.IN

        @Suppress("UNCHECKED_CAST")
        val focusIds = (data["curriculumFocusIds"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.takeIf { id -> id.isNotEmpty() } }
            .orEmpty()

        return ChildPolicy(
            quizMode = QuizMode.fromStorage(data["quizMode"] as? String),
            allowRetryDuringCooldown = boolOr("allowRetryDuringCooldown", false),
            dailyCeilingMinutes = (data["dailyCeilingMinutes"] as? Number)?.toInt(),
            questionsPerQuiz = intOr("questionsPerQuiz", AppConfig.DEFAULT_QUESTIONS_PER_QUIZ),
            passScorePercent = intOr("passScorePercent", AppConfig.DEFAULT_PASS_SCORE_PERCENT),
            rewardsEnabled = boolOr("rewardsEnabled", true),
            weekendBonusEnabled = boolOr("weekendBonusEnabled", false),
            extraMinutesOnPass = intOr("extraMinutesOnPass", 0),
            aiQuizzesEnabled = boolOr("aiQuizzesEnabled", true),
            adaptiveDifficultyEnabled = boolOr("adaptiveDifficultyEnabled", true),
            showExplanations = boolOr("showExplanations", true),
            defaultBlockMinutes = intOr("defaultBlockMinutes", AppConfig.DEFAULT_BLOCK_MINUTES)
                .coerceIn(AppConfig.SESSION_CHUNK_MIN_MINUTES, AppConfig.SESSION_CHUNK_MAX_MINUTES),
            defaultCooldownMinutes = intOr("defaultCooldownMinutes", AppConfig.DEFAULT_COOLDOWN_MINUTES),
            emergencyApps = emergency,
            paused = boolOr("paused", false),
            bonusMinutesToday = intOr("bonusMinutesToday", 0),
            gradeStandard = stringOr("gradeStandard", ""),
            region = region,
            customPromptGuidelines = stringOr("customPromptGuidelines", ""),
            curriculumFocusIds = focusIds,
            nurseryTeachMode = stringOr("nurseryTeachMode", "TEACH_ONLY"),
            nurseryVideoEnabled = boolOr("nurseryVideoEnabled", true),
            nurseryVideoCadenceMinutes = intOr("nurseryVideoCadenceMinutes", 30),
            nurseryVideoPlayWithSound = boolOr("nurseryVideoPlayWithSound", true),
            nurseryPlaylistId = stringOr("nurseryPlaylistId", "all"),
            nurseryCheckEnabled = boolOr("nurseryCheckEnabled", false),
        )
    }
}
