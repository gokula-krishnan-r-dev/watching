package com.meritscreen.core.common.domain

import com.meritscreen.core.common.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildPolicyMapperTest {

    @Test
    fun roundTrip_preservesCoreFields() {
        val original = ChildPolicy(
            quizMode = QuizMode.EVERY_SESSION,
            allowRetryDuringCooldown = false,
            dailyCeilingMinutes = 90,
            questionsPerQuiz = 5,
            passScorePercent = 80,
            rewardsEnabled = false,
            weekendBonusEnabled = true,
            extraMinutesOnPass = 10,
            aiQuizzesEnabled = false,
            adaptiveDifficultyEnabled = false,
            showExplanations = false,
            defaultBlockMinutes = 45,
            defaultCooldownMinutes = 20,
            emergencyApps = listOf("com.android.dialer"),
            paused = true,
            bonusMinutesToday = 15,
        )
        val mapped = ChildPolicyMapper.fromMap(ChildPolicyMapper.toMap(original))
        assertEquals(original, mapped)
    }

    @Test
    fun fromMap_defaultsWhenSparse() {
        val policy = ChildPolicyMapper.fromMap(emptyMap())
        assertEquals(QuizMode.APP_BLOCK, policy.quizMode)
        assertEquals(AppConfig.DEFAULT_QUESTIONS_PER_QUIZ, policy.questionsPerQuiz)
        assertEquals(AppConfig.DEFAULT_PASS_SCORE_PERCENT, policy.passScorePercent)
        assertNull(policy.dailyCeilingMinutes)
        assertTrue(policy.emergencyApps.isNotEmpty())
    }

    @Test
    fun toMap_alwaysWritesDeviceWideFailLock() {
        val map = ChildPolicyMapper.toMap(ChildPolicy())
        assertEquals("all_non_emergency", map["failLockScope"])
        assertEquals("app_block", map["quizMode"])
    }

    @Test
    fun toMap_clampsOutOfRangeValues() {
        val map = ChildPolicyMapper.toMap(
            ChildPolicy(questionsPerQuiz = 99, passScorePercent = 5, extraMinutesOnPass = 500),
        )
        assertEquals(5, map["questionsPerQuiz"])
        assertEquals(50, map["passScorePercent"])
        assertEquals(60, map["extraMinutesOnPass"])
    }

    @Test
    fun toMap_allowsQuickCushionChunks() {
        val map = ChildPolicyMapper.toMap(ChildPolicy(defaultBlockMinutes = 2))
        assertEquals(2, map["defaultBlockMinutes"])
        val mapped = ChildPolicyMapper.fromMap(mapOf("defaultBlockMinutes" to 3))
        assertEquals(3, mapped.defaultBlockMinutes)
    }

    @Test
    fun toMap_clampsSessionChunkBounds() {
        assertEquals(
            AppConfig.SESSION_CHUNK_MIN_MINUTES,
            ChildPolicyMapper.toMap(ChildPolicy(defaultBlockMinutes = 1))["defaultBlockMinutes"],
        )
        assertEquals(
            AppConfig.SESSION_CHUNK_MAX_MINUTES,
            ChildPolicyMapper.toMap(ChildPolicy(defaultBlockMinutes = 999))["defaultBlockMinutes"],
        )
    }
}

class QuizModeTest {
    @Test
    fun fromStorage_acceptsKeysAndEnumNames() {
        assertEquals(QuizMode.APP_BLOCK, QuizMode.fromStorage("app_block"))
        assertEquals(QuizMode.EVERY_SESSION, QuizMode.fromStorage("EVERY_SESSION"))
        assertEquals(QuizMode.DAILY_CEILING, QuizMode.fromStorage("daily_ceiling"))
        assertEquals(QuizMode.APP_BLOCK, QuizMode.fromStorage(null))
        assertEquals(QuizMode.APP_BLOCK, QuizMode.fromStorage("unknown"))
    }
}
