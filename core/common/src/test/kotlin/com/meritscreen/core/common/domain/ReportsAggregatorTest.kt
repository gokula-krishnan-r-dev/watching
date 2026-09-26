package com.meritscreen.core.common.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ReportsAggregatorTest {

    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun `fills missing days with zero so charts stay continuous`() {
        val snapshot = ReportsAggregator.build(
            days = 7,
            usage = listOf(
                UsageDaySummary("2026-09-16", 40, mapOf("com.a" to 40)),
                UsageDaySummary("2026-09-14", 10, mapOf("com.b" to 10)),
            ),
            attempts = emptyList(),
            skills = emptyList(),
            today = today,
        )
        assertEquals(7, snapshot.daily.size)
        assertEquals("2026-09-10", snapshot.daily.first().day)
        assertEquals("2026-09-16", snapshot.daily.last().day)
        assertEquals(40, snapshot.daily.last().minutes)
        assertEquals(0, snapshot.daily[snapshot.daily.indexOfFirst { it.day == "2026-09-15" }].minutes)
        assertEquals(50, snapshot.totalMinutes)
        // Calendar average over the full 7-day window: round(50/7) = 7
        assertEquals(7, snapshot.averageMinutesPerDay)
        assertEquals(1, snapshot.daily.last().byApp.size)
        assertEquals("A", snapshot.daily.last().byApp.first().displayName)
    }

    @Test
    fun `rolls up per-app minutes across the window with friendly labels`() {
        val snapshot = ReportsAggregator.build(
            days = 7,
            usage = listOf(
                UsageDaySummary("2026-09-16", 45, mapOf("com.google.youtube" to 30, "com.chrome" to 15)),
                UsageDaySummary("2026-09-15", 20, mapOf("com.google.youtube" to 20)),
            ),
            attempts = emptyList(),
            skills = emptyList(),
            appLabels = mapOf("com.google.youtube" to "YouTube"),
            today = today,
        )
        assertEquals(listOf("YouTube", "Chrome"), snapshot.byApp.map { it.displayName })
        assertEquals(50, snapshot.byApp.first().minutes)
        assertEquals(15, snapshot.byApp[1].minutes)
        assertEquals(0, snapshot.educationalPercent) // YouTube + Chrome are not educational
    }

    @Test
    fun `adds unattributed bucket when minutesUsed exceeds minutesByApp`() {
        val snapshot = ReportsAggregator.build(
            days = 1,
            usage = listOf(
                UsageDaySummary("2026-09-16", 100, mapOf("org.khankids.android" to 60)),
            ),
            attempts = emptyList(),
            skills = emptyList(),
            appLabels = mapOf("org.khankids.android" to "Khan Academy Kids"),
            today = today,
        )
        assertEquals(100, snapshot.totalMinutes)
        assertEquals(2, snapshot.byApp.size)
        assertEquals(40, snapshot.byApp.last().minutes)
        assertEquals("_other", snapshot.byApp.last().packageName)
        assertEquals(100, snapshot.educationalPercent) // only attributable classified apps
    }

    @Test
    fun `computes quiz pass rate and ignores attempts outside the window`() {
        val inWindow = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val old = today.minusDays(20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val snapshot = ReportsAggregator.build(
            days = 7,
            usage = emptyList(),
            attempts = listOf(
                QuizAttemptSummary("a1", inWindow, listOf("math"), 3, 3, true, 5),
                QuizAttemptSummary("a2", inWindow + 1, listOf("spelling"), 1, 3, false, 0),
                QuizAttemptSummary("old", old, listOf("math"), 3, 3, true, 5),
            ),
            skills = emptyList(),
            today = today,
        )
        assertEquals(2, snapshot.quiz.attemptCount)
        assertEquals(1, snapshot.quiz.passedCount)
        assertEquals(1, snapshot.quiz.failedCount)
        assertEquals(50, snapshot.quiz.accuracyPercent)
        assertEquals(4, snapshot.quiz.questionsCorrect)
        assertEquals(6, snapshot.quiz.questionsTotal)
        assertEquals(5, snapshot.quiz.extraMinutesEarned)
        assertEquals(2, snapshot.recentAttempts.size)
        assertTrue(snapshot.hasAnyData)
    }

    @Test
    fun `surfaces weak concepts as practice hints and sorts topics needing practice first`() {
        val snapshot = ReportsAggregator.build(
            days = 7,
            usage = emptyList(),
            attempts = emptyList(),
            skills = listOf(
                TopicSkillSummary("spelling", 4, 2, false, masteredConcepts = listOf("c1"), tierLabel = "advanced"),
                TopicSkillSummary(
                    topic = "math",
                    level = 2,
                    streakCorrect = 0,
                    weak = true,
                    weakConcepts = listOf(WeakConceptHint("add-within-20", "Adding within 20")),
                ),
            ),
            today = today,
        )
        assertEquals("math", snapshot.topics.first().topic)
        assertEquals(listOf("Adding within 20"), snapshot.practiceHints.map { it.title })
        assertEquals("Level 2 of 5 · Building", ReportsAggregator.levelLabel(2))
        assertEquals(listOf("c1"), snapshot.topics.last().masteredConcepts)
    }

    @Test
    fun `empty window reports no data`() {
        val snapshot = ReportsAggregator.build(
            days = 7,
            usage = emptyList(),
            attempts = emptyList(),
            skills = emptyList(),
            today = today,
        )
        assertFalse(snapshot.hasAnyData)
        assertNull(snapshot.quiz.accuracyPercent)
        assertNull(snapshot.busiestDay)
        assertNull(snapshot.trendPercent)
        assertNull(snapshot.educationalPercent)
        assertEquals(0, snapshot.averageMinutesPerDay)
    }

    @Test
    fun `trendPercent compares second half to first half`() {
        val daily = listOf(
            DailyMinutesPoint("d1", 10),
            DailyMinutesPoint("d2", 10),
            DailyMinutesPoint("d3", 20),
            DailyMinutesPoint("d4", 20),
        )
        assertEquals(100, ReportsAggregator.trendPercent(daily))
        assertNull(ReportsAggregator.trendPercent(daily.take(2)))
        assertNull(ReportsAggregator.trendPercent(List(4) { DailyMinutesPoint("d$it", 0) }))
    }

    @Test
    fun `humanizeToken turns concept ids into parent-friendly titles`() {
        assertEquals("Add Within 20", ReportsAggregator.humanizeToken("add-within-20"))
        assertEquals("Math", ReportsAggregator.humanizeToken("math"))
    }
}
