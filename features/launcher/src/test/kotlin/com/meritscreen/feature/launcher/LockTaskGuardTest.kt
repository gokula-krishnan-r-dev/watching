package com.meritscreen.feature.launcher

import com.meritscreen.core.common.domain.SessionPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure policy tests for when Child Home may engage screen pinning.
 * Pinning while the Approved Playground is visible blocks `startActivity` to YouTube
 * and other approved apps (system "unpin this app" toast).
 */
class LockTaskGuardTest {

    @Test
    fun shouldPin_onlyWhenParentPausedOrShielded() {
        assertTrue(LockTaskGuard.shouldPinForFailLock(paused = true, phase = SessionPhase.Idle))
        assertTrue(LockTaskGuard.shouldPinForFailLock(paused = false, phase = SessionPhase.Shielded))
        assertTrue(LockTaskGuard.shouldPinForFailLock(paused = true, phase = SessionPhase.Shielded))

        assertFalse(LockTaskGuard.shouldPinForFailLock(paused = false, phase = SessionPhase.Idle))
        assertFalse(LockTaskGuard.shouldPinForFailLock(paused = false, phase = SessionPhase.InBlock))
        assertFalse(LockTaskGuard.shouldPinForFailLock(paused = false, phase = SessionPhase.QuizDue))
    }
}
