package com.meritscreen.feature.child.domain

import com.meritscreen.core.common.domain.AgeBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveQuizEngineTest {

    private val sampleQuestion = QuizQuestion(
        id = "test_q1",
        ageBand = AgeBand.AGE_7_TO_9,
        topic = "Math",
        conceptId = "div_facts",
        conceptTitle = "Division Facts",
        difficulty = 2,
        prompt = "What is 48 ÷ 6?",
        choices = listOf(
            QuizChoice(id = "c1", text = "6", correct = false),
            QuizChoice(id = "c2", text = "8", correct = true),
        ),
        whyCorrect = "48 / 6 = 8",
        whyWrongByChoice = mapOf("c1" to "6 x 6 = 36"),
        conceptExplainer = "Division divides into equal groups.",
        interactionType = "TAP_TEXT",
    )

    @Test
    fun `tierLabel calculates appropriate mastery tier based on level and streak`() {
        assertEquals("basic", AdaptiveQuizEngine.tierLabel(level = 1, streakCorrect = 0))
        assertEquals("basic", AdaptiveQuizEngine.tierLabel(level = 2, streakCorrect = 5))
        assertEquals("intermediate", AdaptiveQuizEngine.tierLabel(level = 3, streakCorrect = 1))
        assertEquals("advanced", AdaptiveQuizEngine.tierLabel(level = 4, streakCorrect = 0))
        assertEquals("advanced", AdaptiveQuizEngine.tierLabel(level = 5, streakCorrect = 1))
        assertEquals("mastery", AdaptiveQuizEngine.tierLabel(level = 5, streakCorrect = 2))
        assertEquals("mastery", AdaptiveQuizEngine.tierLabel(level = 5, streakCorrect = 10))
    }

    @Test
    fun `pickNext selects question matching target level and not recently seen`() {
        val q1 = sampleQuestion.copy(id = "q1", difficulty = 1)
        val q2 = sampleQuestion.copy(id = "q2", difficulty = 2)
        val q3 = sampleQuestion.copy(id = "q3", difficulty = 3)
        val bank = listOf(q1, q2, q3)

        val skills = mapOf("Math" to TopicSkill(topic = "Math", level = 2))

        // When q2 was recently seen, it should pick another available question in the bank
        val picked = AdaptiveQuizEngine.pickNext(
            bank = bank,
            skills = skills,
            recentIds = setOf("q2"),
            usedInSession = emptySet(),
            lastWrongConceptId = null,
            preferredLevel = 2,
        )

        assertNotNull(picked)
        assertTrue(picked!!.id != "q2")
    }

    @Test
    fun `pickNext excludes same wording even when regenerated with a different id`() {
        val repeated = sampleQuestion.copy(id = "new-ai-id", prompt = "What is 48 ÷ 6?")
        val fresh = sampleQuestion.copy(id = "fresh", prompt = "How many groups are in 48 divided by 6?")
        val picked = AdaptiveQuizEngine.pickNext(
            bank = listOf(repeated, fresh),
            skills = emptyMap(),
            recentIds = setOf(AdaptiveQuizEngine.promptHistoryKey(sampleQuestion.prompt)),
            usedInSession = emptySet(),
            lastWrongConceptId = null,
            preferredLevel = 2,
        )

        assertEquals("fresh", picked?.id)
    }

    @Test
    fun `pickNext never repeats a question inside the same quiz`() {
        val onlyQuestion = sampleQuestion.copy(id = "only")

        val picked = AdaptiveQuizEngine.pickNext(
            bank = listOf(onlyQuestion),
            skills = emptyMap(),
            recentIds = emptySet(),
            usedInSession = setOf("only"),
            lastWrongConceptId = null,
            preferredLevel = 2,
        )

        assertEquals(null, picked)
    }

    @Test
    fun `gradeAnswer correctly awards seeds, increments streak, and updates mastered concepts`() {
        val initialSkill = TopicSkill(
            topic = "Math",
            level = 4,
            streakCorrect = 2,
            totalAttempts = 5,
            totalCorrect = 4,
            avgResponseTimeMs = 4000,
            weakConcepts = setOf("div_facts"),
            masteredConcepts = emptySet(),
        )

        val (feedback, updatedSkill) = AdaptiveQuizEngine.gradeAnswer(
            question = sampleQuestion,
            choiceId = "c2", // Correct choice
            skill = initialSkill,
            responseTimeMs = 3000,
        )

        assertTrue(feedback.correct)
        assertEquals(0, updatedSkill.streakCorrect) // Streak resets on level up
        assertEquals(6, updatedSkill.totalAttempts)
        assertEquals(5, updatedSkill.totalCorrect)
        // Check that weak concept was cleared and added to mastered concepts since level >= 4
        assertFalse(updatedSkill.weakConcepts.contains("div_facts"))
        assertTrue(updatedSkill.masteredConcepts.contains("div_facts"))
        // Level incremented
        assertEquals(5, updatedSkill.level)
        assertEquals("advanced", updatedSkill.tierLabel)
    }

    @Test
    fun `gradeAnswer on wrong answer resets streak and adds to weak concepts`() {
        val initialSkill = TopicSkill(
            topic = "Math",
            level = 3,
            streakCorrect = 4,
            totalAttempts = 10,
            totalCorrect = 8,
            tierLabel = "intermediate",
        )

        val (feedback, updatedSkill) = AdaptiveQuizEngine.gradeAnswer(
            question = sampleQuestion,
            choiceId = "c1", // Incorrect choice
            skill = initialSkill,
            responseTimeMs = 5000,
        )

        assertFalse(feedback.correct)
        assertEquals(0, updatedSkill.streakCorrect)
        assertEquals(11, updatedSkill.totalAttempts)
        assertEquals(8, updatedSkill.totalCorrect)
        assertTrue(updatedSkill.weakConcepts.contains("div_facts"))
        assertEquals("Division Facts", updatedSkill.conceptTitles["div_facts"])
        // Level drops by 1
        assertEquals(2, updatedSkill.level)
        assertEquals("basic", updatedSkill.tierLabel)
    }

    @Test
    fun `pickNext prefers AI-sourced items when available on the same rung`() {
        val builtin = sampleQuestion.copy(id = "builtin_1", source = "builtin")
        val ai = sampleQuestion.copy(id = "ai_1", source = "ai", prompt = "AI: What is 48 ÷ 6?")
        val picked = AdaptiveQuizEngine.pickNext(
            bank = listOf(builtin, ai),
            skills = emptyMap(),
            recentIds = emptySet(),
            usedInSession = emptySet(),
            lastWrongConceptId = null,
            preferredLevel = 2,
            random = kotlin.random.Random(0),
        )
        assertNotNull(picked)
        assertEquals("ai", picked!!.source)
    }

    @Test
    fun `withShuffledChoices moves correct answer off first slot for biased AI order`() {
        val biased = sampleQuestion.copy(
            choices = listOf(
                QuizChoice(id = "a", text = "end", correct = true),
                QuizChoice(id = "b", text = "begin", correct = false),
                QuizChoice(id = "c", text = "open", correct = false),
                QuizChoice(id = "d", text = "close", correct = false),
            ),
        )
        assertTrue(biased.choices.first().correct)

        val positions = (0 until 64).map { seed ->
            AdaptiveQuizEngine.withShuffledChoices(biased, kotlin.random.Random(seed))
                .choices.indexOfFirst { it.correct }
        }.toSet()

        assertEquals(setOf(0, 1, 2, 3), positions)
        assertTrue(positions.any { it != 0 })
    }

    @Test
    fun `pickNext shuffles choice order so correct is not always index 0`() {
        val biased = sampleQuestion.copy(
            id = "ai_biased",
            source = "ai",
            choices = listOf(
                QuizChoice(id = "a", text = "end", correct = true),
                QuizChoice(id = "b", text = "begin", correct = false),
                QuizChoice(id = "c", text = "open", correct = false),
            ),
        )
        val correctAtZero = (0 until 40).count { seed ->
            val picked = AdaptiveQuizEngine.pickNext(
                bank = listOf(biased),
                skills = emptyMap(),
                recentIds = emptySet(),
                usedInSession = emptySet(),
                lastWrongConceptId = null,
                preferredLevel = 2,
                random = kotlin.random.Random(seed),
            )
            requireNotNull(picked).choices.first().correct
        }
        // With 3 slots, ~33% land at index 0; require clear evidence of shuffling.
        assertTrue(
            "Expected correct answer in non-A positions often, got $correctAtZero/40 at A",
            correctAtZero in 5..30,
        )
    }
}
