package com.meritscreen.feature.child.domain

import android.os.SystemClock
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.SessionPhase
import com.meritscreen.feature.child.data.ChildPolicyRepository
import com.meritscreen.feature.child.data.ChildSessionRepository
import com.meritscreen.feature.child.data.UsageRecorder
import com.meritscreen.core.security.pairing.ChildPairingStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChildSessionController @Inject constructor(
    private val sessionRepository: ChildSessionRepository,
    private val policyRepository: ChildPolicyRepository,
    private val pairingStore: ChildPairingStore,
    private val usageRecorder: UsageRecorder,
) {
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(SessionSnapshot())
    val snapshot: StateFlow<SessionSnapshot> = _snapshot.asStateFlow()

    private var lastPersisted: SessionSnapshot? = null
    private var lastPersistElapsedMs: Long = 0L

    suspend fun hydrate() {
        mutex.withLock {
            val loaded = sessionRepository.get()
            val nowElapsedMs = now()
            val clockReset = loaded.lastTickElapsedMs?.let { it > nowElapsedMs } == true
            val restored = if (clockReset) {
                val remainingBlockMs = ((loaded.blockDurationMinutes - loaded.minutesAccruedInBlock)
                    .coerceAtLeast(0f) * 60_000f).toLong()
                loaded.copy(
                    lastTickElapsedMs = nowElapsedMs,
                    blockStartedElapsedMs = if (loaded.phase == SessionPhase.InBlock) nowElapsedMs else null,
                    deviceShieldedUntilElapsedMs = if (loaded.phase == SessionPhase.Shielded) {
                        nowElapsedMs + loaded.cooldownMinutes.coerceAtLeast(1) * 60_000L
                    } else loaded.deviceShieldedUntilElapsedMs,
                    quizGraceUntilElapsedMs = loaded.quizGraceUntilElapsedMs?.let { nowElapsedMs + remainingBlockMs },
                )
            } else {
                loaded
            }
            val next = SessionEngine.expireShieldIfNeeded(restored, nowElapsedMs)
            _snapshot.value = next
            persist(next, force = true)
        }
    }

    fun observe(): Flow<SessionSnapshot> = snapshot

    suspend fun tick(isAppActive: Boolean = false) {
        mutex.withLock {
            val childId = pairingStore.get()?.childId ?: return
            val policy = policyRepository.getPolicy(childId)
            val previous = _snapshot.value
            val next = SessionEngine.tick(previous, now(), policy, isAppActive = isAppActive)
            recordUsageDelta(childId, previous, next)
            _snapshot.value = next
            persist(next, force = false)
        }
    }

    /**
     * Records whole-minute usage deltas per app for Phase 7 upload. `minutesUsedToday` only
     * changes on a minute boundary (see `SessionEngine.tick`), so this fires roughly once a
     * minute — never a per-second Room write.
     */
    private suspend fun recordUsageDelta(childId: String, previous: SessionSnapshot, next: SessionSnapshot) {
        val delta = next.minutesUsedToday - previous.minutesUsedToday
        val activePackage = previous.activePackage ?: "com.watching.app"
        if (delta > 0 && activePackage.isNotBlank()) {
            usageRecorder.record(childId, next.dayKey, activePackage, delta)
        }
    }

    suspend fun openApp(rule: AppRule): SessionSnapshot = mutex.withLock {
        val childId = pairingStore.get()?.childId ?: return _snapshot.value
        val policy = policyRepository.getPolicy(childId)
        val next = SessionEngine.openApp(_snapshot.value, now(), rule, policy)
        _snapshot.value = next
        persist(next, force = true)
        next
    }

    suspend fun onQuizPassed(rule: AppRule?): SessionSnapshot = mutex.withLock {
        val childId = pairingStore.get()?.childId ?: return _snapshot.value
        val policy = policyRepository.getPolicy(childId)
        val next = SessionEngine.onQuizPassed(_snapshot.value, now(), rule, policy)
        _snapshot.value = next
        persist(next, force = true)
        next
    }

    suspend fun onQuizFailed(rule: AppRule?): SessionSnapshot = mutex.withLock {
        val childId = pairingStore.get()?.childId ?: return _snapshot.value
        val policy = policyRepository.getPolicy(childId)
        val next = SessionEngine.onQuizFailed(_snapshot.value, now(), policy, rule)
        _snapshot.value = next
        persist(next, force = true)
        next
    }

    /** Parent PIN override — clear the device-wide fail-lock / resting window immediately. */
    suspend fun endFailLock(): SessionSnapshot = mutex.withLock {
        val next = SessionEngine.endFailLock(_snapshot.value)
        _snapshot.value = next
        persist(next, force = true)
        next
    }

    suspend fun returnHome(): SessionSnapshot = mutex.withLock {
        // Returning to the launcher pauses the active app session; it does not grant a
        // fresh block or discard already accrued usage. The monitor resumes it on reopen.
        val next = SessionEngine.tick(
            _snapshot.value,
            now(),
            policyRepository.getPolicy(pairingStore.get()?.childId ?: return _snapshot.value),
            isAppActive = false,
        )
        _snapshot.value = next
        persist(next, force = true)
        next
    }

    /** Repairs session state created by older builds while Home/system UI was mis-tracked. */
    suspend fun clearUntrackedSurfaceSession(): SessionSnapshot = mutex.withLock {
        val next = SessionEngine.clearUntrackedSurfaceSession(_snapshot.value, now())
        _snapshot.value = next
        persist(next, force = true)
        next
    }

    suspend fun currentPolicy(): ChildPolicy {
        val childId = pairingStore.get()?.childId ?: return ChildPolicy()
        return policyRepository.getPolicy(childId)
    }

    fun phase(): SessionPhase = _snapshot.value.phase

    private suspend fun persist(next: SessionSnapshot, force: Boolean) {
        val nowElapsed = now()
        if (!force && !SessionPersistPolicy.shouldPersist(lastPersisted, next, nowElapsed, lastPersistElapsedMs)) {
            return
        }
        sessionRepository.save(next)
        lastPersisted = next
        lastPersistElapsedMs = nowElapsed
    }

    private fun now(): Long = SystemClock.elapsedRealtime()
}
