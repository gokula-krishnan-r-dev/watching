package com.meritscreen.feature.child.domain

import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.QuizMode
import com.meritscreen.core.common.domain.SessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {

    private val youtube = AppRule(
        appId = "yt",
        packageOrBundleId = "com.google.android.youtube",
        displayName = "YouTube",
        allowed = true,
        blockMinutes = 30,
        grantOnPassMinutes = 30,
        cooldownMinutes = 15,
    )

    @Test
    fun openApp_startsBlock() {
        val next = SessionEngine.openApp(SessionSnapshot(), nowElapsedMs = 1_000L, youtube, ChildPolicy())
        assertEquals(SessionPhase.InBlock, next.phase)
        assertEquals("com.google.android.youtube", next.activePackage)
        assertEquals(30, next.blockDurationMinutes)
    }

    @Test
    fun tick_reachesQuizDue_afterBlockMinutes() {
        val started = SessionEngine.openApp(SessionSnapshot(), 0L, youtube, ChildPolicy())
        val after = SessionEngine.tick(started, nowElapsedMs = 30 * 60_000L + 1, ChildPolicy())
        assertEquals(SessionPhase.QuizDue, after.phase)
    }

    @Test
    fun quizFail_shieldsDeviceWide() {
        val inBlock = SessionEngine.openApp(SessionSnapshot(), 0L, youtube, ChildPolicy())
        val failed = SessionEngine.onQuizFailed(inBlock, nowElapsedMs = 1_000L, ChildPolicy(), youtube)
        assertEquals(SessionPhase.Shielded, failed.phase)
        assertFalse(SessionEngine.canLaunch(failed, youtube, ChildPolicy()))
        assertTrue(
            SessionEngine.canLaunch(
                failed,
                AppRule("phone", "com.android.dialer", isEmergency = true),
                ChildPolicy(),
            ),
        )
    }

    @Test
    fun quizPass_grantsNewBlock() {
        val quizDue = SessionSnapshot(
            phase = SessionPhase.QuizDue,
            activePackage = youtube.packageOrBundleId,
            activeAppId = youtube.appId,
        )
        val passed = SessionEngine.onQuizPassed(quizDue, 5_000L, youtube, ChildPolicy())
        assertEquals(SessionPhase.InBlock, passed.phase)
        assertEquals(0f, passed.minutesAccruedInBlock, 0.01f)
    }

    @Test
    fun cooldownExpiry_returnsToIdle() {
        val shielded = SessionSnapshot(
            phase = SessionPhase.Shielded,
            deviceShieldedUntilElapsedMs = 10_000L,
        )
        val unlocked = SessionEngine.expireShieldIfNeeded(shielded, nowElapsedMs = 10_000L)
        assertEquals(SessionPhase.Idle, unlocked.phase)
    }

    @Test
    fun endFailLock_clearsShieldImmediately() {
        val shielded = SessionSnapshot(
            phase = SessionPhase.Shielded,
            deviceShieldedUntilElapsedMs = 60_000L,
            cooldownMinutes = 15,
            activePackage = "com.google.android.apps.youtube.kids",
        )
        val cleared = SessionEngine.endFailLock(shielded)
        assertEquals(SessionPhase.Idle, cleared.phase)
        assertEquals(null, cleared.deviceShieldedUntilElapsedMs)
        assertEquals(null, cleared.activePackage)
    }

    @Test
    fun tick_accruesWholeMinutesAcrossSubMinuteTicks() {
        val started = SessionEngine.openApp(SessionSnapshot(), 0L, youtube, ChildPolicy())
        // Simulate many 1s ticks totaling ~2 minutes (as Home ticker would while resumed).
        var state = started
        for (sec in 1..120) {
            state = SessionEngine.tick(state, nowElapsedMs = sec * 1_000L, ChildPolicy())
        }
        assertEquals(SessionPhase.InBlock, state.phase)
        assertEquals(2, state.minutesUsedToday)
        assertEquals(28, state.remainingBlockMinutes(120_000L))
    }

    @Test
    fun tick_onResumeAfterGap_accruesElapsedMinutes() {
        val started = SessionEngine.openApp(SessionSnapshot(), 0L, youtube, ChildPolicy())
        // User left Home for ~2 minutes in Files; one tick on resume catches up.
        val after = SessionEngine.tick(started, nowElapsedMs = 2 * 60_000L, ChildPolicy())
        assertEquals(2, after.minutesUsedToday)
        assertEquals(28, after.remainingBlockMinutes(2 * 60_000L))
    }

    @Test
    fun foregroundUsage_pausesWhenAppLeavesForegroundAndResumesWithoutReset() {
        val policy = ChildPolicy()
        var state = SessionEngine.openApp(SessionSnapshot(), 0L, youtube, policy)
        state = SessionEngine.tick(state, 2 * 60_000L, policy, isAppActive = true)
        state = SessionEngine.tick(state, 12 * 60_000L, policy, isAppActive = false)
        assertEquals(2, state.minutesUsedToday)
        assertEquals(28, state.remainingBlockMinutes())

        state = SessionEngine.tick(state, 15 * 60_000L, policy, isAppActive = true)
        assertEquals(5, state.minutesUsedToday)
        assertEquals(25, state.remainingBlockMinutes())
        assertEquals(SessionPhase.InBlock, state.phase)
    }

    @Test
    fun returningToLauncherDoesNotClearGrantedBlock() {
        val passed = SessionEngine.onQuizPassed(
            SessionSnapshot(
                phase = SessionPhase.QuizDue,
                activePackage = youtube.packageOrBundleId,
                activeAppId = youtube.appId,
            ),
            nowElapsedMs = 5_000L,
            youtube,
            ChildPolicy(),
        )

        val paused = SessionEngine.tick(passed, 65_000L, ChildPolicy(), isAppActive = false)
        assertEquals(SessionPhase.InBlock, paused.phase)
        assertEquals(youtube.packageOrBundleId, paused.activePackage)
        assertEquals(0, paused.minutesUsedToday)
        assertEquals(30, paused.remainingBlockMinutes())
    }

    @Test
    fun pausedPolicy_blocksNonEmergencyLaunch() {
        val policy = ChildPolicy(paused = true)
        assertFalse(SessionEngine.canLaunch(SessionSnapshot(), youtube, policy))
        assertTrue(
            SessionEngine.canLaunch(
                SessionSnapshot(),
                AppRule("phone", "com.android.dialer", isEmergency = true),
                policy,
            ),
        )
    }

    @Test
    fun everySessionMode_opensToQuizDue() {
        val policy = ChildPolicy(quizMode = QuizMode.EVERY_SESSION)
        val next = SessionEngine.openApp(SessionSnapshot(), 0L, youtube, policy)
        assertEquals(SessionPhase.QuizDue, next.phase)
    }

    @Test
    fun dailyRemainingMinutes_defaultsTo120WhenPolicyCeilingNull() {
        val snapshot = SessionSnapshot(minutesUsedToday = 7)
        val policy = ChildPolicy(dailyCeilingMinutes = null)
        val remaining = snapshot.dailyRemainingMinutes(policy)
        assertEquals(113, remaining) // 120 - 7 = 113
    }

    @Test
    fun dailyRemainingMinutes_usesCustomCeiling() {
        val snapshot = SessionSnapshot(minutesUsedToday = 20)
        val policy = ChildPolicy(dailyCeilingMinutes = 60)
        val remaining = snapshot.dailyRemainingMinutes(policy)
        assertEquals(40, remaining) // 60 - 20 = 40
    }

    @Test
    fun tick_inIdle_accruesScreenTimeMinuteByMinute() {
        var state = SessionSnapshot(
            phase = SessionPhase.Idle,
            minutesUsedToday = 0,
            lastTickElapsedMs = 0L,
        )
        val policy = ChildPolicy()

        // 60 ticks of 1000ms each = 60 seconds (1 minute of active screen time)
        for (sec in 1..60) {
            state = SessionEngine.tick(state, nowElapsedMs = sec * 1_000L, policy)
        }

        assertEquals(SessionPhase.Idle, state.phase)
        assertEquals(1, state.minutesUsedToday)
        assertEquals(119, state.dailyRemainingMinutes(policy)) // 120 - 1 = 119
    }
}
