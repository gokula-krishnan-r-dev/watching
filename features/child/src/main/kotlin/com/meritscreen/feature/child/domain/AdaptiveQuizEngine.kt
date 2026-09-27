package com.meritscreen.feature.child.domain

import com.meritscreen.core.common.domain.AgeBand
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import java.util.Locale

@Serializable
data class QuizChoice(
    val id: String,
    val text: String = "",
    val correct: Boolean,
    val imageTag: String? = null,
    val audioKey: String? = null,
)

@Serializable
data class MiniLesson(
    val title: String,
    val bodyLines: List<String>,
    val illustrationAssetId: String? = null,
)

@Serializable
data class QuizQuestion(
    val id: String,
    val ageBand: AgeBand,
    val topic: String,
    val conceptId: String,
    val conceptTitle: String,
    val difficulty: Int,
    val prompt: String,
    val choices: List<QuizChoice>,
    val whyCorrect: String,
    val whyWrongByChoice: Map<String, String>,
    val conceptExplainer: String,
    val language: String = "en",
    val interactionType: String = "TAP_TEXT", // TAP_TEXT, TAP_IMAGE, COUNT_AND_TAP, MATCH_COLOR_SHAPE
    val promptTag: String? = null,
    val promptCount: Int? = null,
    val promptAudioKey: String? = null,
    val miniLesson: MiniLesson? = null,
    /** `builtin` from assets or `ai` from Firebase AI Logic packs. */
    val source: String = "builtin",
)

data class TopicSkill(
    val topic: String,
    val level: Int = 2,
    val streakCorrect: Int = 0,
    val weakConcepts: Set<String> = emptySet(),
    /** Parent-friendly titles for [weakConcepts], keyed by concept id. */
    val conceptTitles: Map<String, String> = emptyMap(),
    val masteredConcepts: Set<String> = emptySet(),
    val tierLabel: String = "basic",
    val totalAttempts: Int = 0,
    val totalCorrect: Int = 0,
    val avgResponseTimeMs: Long = 0,
)

data class QuizAnswerFeedback(
    val correct: Boolean,
    val resultLine: String,
    val whyLine: String,
    val conceptLine: String,
    val nextLevel: Int,
)

data class QuizSessionResult(
    val passed: Boolean,
    val correctCount: Int,
    val total: Int,
    val percent: Int,
)

/**
 * Local adaptive picker + grader. No network. Levels stay inside the child's age band (L1–L5).
 */
object AdaptiveQuizEngine {

    /**
     * Fisher–Yates shuffle of answer options so the correct choice is not stuck in slot A.
     * Grading keys off [QuizChoice.id], so order is presentation-only.
     * Single-option lists are returned unchanged.
     */
    fun withShuffledChoices(
        question: QuizQuestion,
        random: Random = Random.Default,
    ): QuizQuestion {
        if (question.choices.size <= 1) return question
        return question.copy(choices = question.choices.shuffled(random))
    }

    fun tierLabel(level: Int, streakCorrect: Int): String = when {
        level >= 5 && streakCorrect >= 2 -> "mastery"
        level >= 5 || level == 4 -> "advanced"
        level == 3 -> "intermediate"
        else -> "basic"
    }

    fun pickNext(
        bank: List<QuizQuestion>,
        skills: Map<String, TopicSkill>,
        recentIds: Set<String>,
        usedInSession: Set<String>,
        lastWrongConceptId: String?,
        preferredLevel: Int,
        random: Random = Random.Default,
    ): QuizQuestion? {
        if (bank.isEmpty()) return null
        val freshUnused = bank.filter {
            it.id !in usedInSession && it.id !in recentIds && promptHistoryKey(it.prompt) !in recentIds
        }
        // If the history window covers the entire bank, allow older questions again, but
        // never repeat one inside the same quiz. A tiny bank returns no candidate so the
        // caller can show a content-sync error instead of repeating the same question.
        val pool = freshUnused.ifEmpty { bank.filter { it.id !in usedInSession } }
        if (pool.isEmpty()) return null

        if (lastWrongConceptId != null) {
            val easier = pool.filter {
                it.conceptId == lastWrongConceptId && it.difficulty <= preferredLevel
            }.sortedBy { it.difficulty }
            if (easier.isNotEmpty()) {
                return withShuffledChoices(easier.first(), random)
            }
        }

        val near = pool.filter { it.difficulty in (preferredLevel - 1)..(preferredLevel + 1) }
        val candidates = near.ifEmpty { pool }
        // Prefer freshly generated AI items when they match the rung so parents see
        // adaptive content instead of cycling the small builtin bank.
        val preferredSource = candidates.filter { it.source == "ai" }.ifEmpty { candidates }
        val byTopicMix = preferredSource.groupBy { it.topic }.values.flatMap { list ->
            list.shuffled(random).take(2)
        }
        val picked = (byTopicMix.ifEmpty { preferredSource }).random(random)
        return withShuffledChoices(picked, random)
    }

    fun sessionStartLevel(skills: Map<String, TopicSkill>): Int {
        if (skills.isEmpty()) return 2
        val levels = skills.values.map { it.level }.sorted()
        return levels[levels.size / 2].coerceIn(1, 5)
    }

    fun gradeAnswer(
        question: QuizQuestion,
        choiceId: String,
        skill: TopicSkill,
        responseTimeMs: Long = 0L,
    ): Pair<QuizAnswerFeedback, TopicSkill> {
        val choice = question.choices.firstOrNull { it.id == choiceId }
        val correct = choice?.correct == true
        val nextLevel = when {
            correct && skill.streakCorrect + 1 >= 2 -> min(5, skill.level + 1)
            correct -> skill.level
            else -> max(1, skill.level - 1)
        }
        val nextStreak = if (correct) skill.streakCorrect + 1 else 0
        val weak = if (correct) {
            skill.weakConcepts - question.conceptId
        } else {
            skill.weakConcepts + question.conceptId
        }
        val titles = if (correct) {
            skill.conceptTitles - question.conceptId
        } else {
            skill.conceptTitles + (question.conceptId to question.conceptTitle)
        }
        val newAttempts = skill.totalAttempts + 1
        val newCorrect = if (correct) skill.totalCorrect + 1 else skill.totalCorrect
        val newAvgTime = if (skill.totalAttempts == 0) {
            responseTimeMs
        } else if (responseTimeMs > 0L) {
            (skill.avgResponseTimeMs * skill.totalAttempts + responseTimeMs) / newAttempts
        } else {
            skill.avgResponseTimeMs
        }
        val effectiveStreak = if (correct && nextLevel > skill.level) 0 else nextStreak
        val nextTier = tierLabel(nextLevel, effectiveStreak)
        val mastered = if (correct && (nextLevel >= 4 || effectiveStreak >= 2)) {
            skill.masteredConcepts + question.conceptId
        } else {
            skill.masteredConcepts
        }

        val feedback = QuizAnswerFeedback(
            correct = correct,
            resultLine = if (correct) "That’s it" else "Not this one",
            whyLine = if (correct) {
                question.whyCorrect
            } else {
                question.whyWrongByChoice[choiceId]
                    ?: "That choice doesn’t match. Let’s look at the idea."
            },
            conceptLine = question.conceptExplainer,
            nextLevel = nextLevel,
        )
        val updated = skill.copy(
            level = nextLevel,
            streakCorrect = effectiveStreak,
            weakConcepts = weak,
            conceptTitles = titles.filterKeys { it in weak },
            masteredConcepts = mastered,
            tierLabel = nextTier,
            totalAttempts = newAttempts,
            totalCorrect = newCorrect,
            avgResponseTimeMs = newAvgTime,
        )
        return feedback to updated
    }

    fun finalize(correctCount: Int, total: Int, passScorePercent: Int): QuizSessionResult {
        val safeTotal = total.coerceAtLeast(1)
        val percent = (correctCount * 100) / safeTotal
        return QuizSessionResult(
            passed = percent >= passScorePercent,
            correctCount = correctCount,
            total = safeTotal,
            percent = percent,
        )
    }

    fun questionsPerQuiz(configured: Int): Int = configured.coerceIn(3, 5)

    /** Stable history key so identical prompts from regenerated packs are still excluded. */
    fun promptHistoryKey(prompt: String): String =
        "prompt:${prompt.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)}"
}
