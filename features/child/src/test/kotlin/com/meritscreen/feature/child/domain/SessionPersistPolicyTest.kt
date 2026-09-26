package com.meritscreen.feature.child.domain

import com.meritscreen.core.common.domain.SessionPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPersistPolicyTest {

    private val base = SessionSnapshot(
        phase = SessionPhase.InBlock,
        activePackage = "com.example.yt",
        activeAppId = "yt",
        dayKey = "2026-09-16",
        minutesUsedToday = 10,
        blockDurationMinutes = 30,
        cooldownMinutes = 15,
    )

    @Test
    fun persistsWhenNothingPreviouslyWritten() {
        assertTrue(
            SessionPersistPolicy.shouldPersist(
                previousPersisted = null,
                next = base,
                nowElapsedMs = 1_000L,
                lastPersistElapsedMs = 0L,
                intervalSeconds = 45,
            ),
        )
    }

    @Test
    fun skipsUnchangedTickInsideInterval() {
        assertFalse(
            SessionPersistPolicy.shouldPersist(
                previousPersisted = base,
                next = base.copy(minutesAccruedInBlock = 0.5f, lastTickElapsedMs = 2_000L),
                nowElapsedMs = 10_000L,
                lastPersistElapsedMs = 0L,
                intervalSeconds = 45,
            ),
        )
    }

    @Test
    fun persistsAfterIntervalEvenIfOnlyTickFieldsChanged() {
        assertTrue(
            SessionPersistPolicy.shouldPersist(
                previousPersisted = base,
                next = base.copy(minutesAccruedInBlock = 1.2f, lastTickElapsedMs = 50_000L),
                nowElapsedMs = 50_000L,
                lastPersistElapsedMs = 0L,
                intervalSeconds = 45,
            ),
        )
    }

    @Test
    fun alwaysPersistsPhaseChange() {
        assertTrue(
            SessionPersistPolicy.shouldPersist(
                previousPersisted = base,
                next = base.copy(phase = SessionPhase.QuizDue),
                nowElapsedMs = 1_000L,
                lastPersistElapsedMs = 0L,
                intervalSeconds = 45,
            ),
        )
    }

    @Test
    fun alwaysPersistsDailyMinuteBoundary() {
        assertTrue(
            SessionPersistPolicy.shouldPersist(
                previousPersisted = base,
                next = base.copy(minutesUsedToday = 11),
                nowElapsedMs = 1_000L,
                lastPersistElapsedMs = 0L,
                intervalSeconds = 45,
            ),
        )
    }
}
