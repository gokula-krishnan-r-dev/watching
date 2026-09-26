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

    private val _debugState = MutableStateFlow(TimerDebugState())
    val debugState: StateFlow<TimerDebugState> = _debugState.asStateFlow()

    private var lastPersisted: SessionSnapshot? = null
    private var lastPersistElapsedMs: Long = 0L

    suspend fun hydrate() {
        mutex.withLock {
            val loaded = sessionRepository.get()
            val next = SessionEngine.expireShieldIfNeeded(loaded, now())
            _snapshot.value = next
            persist(next, force = true)
        }
    }

    fun observe(): Flow<SessionSnapshot> = snapshot

    suspend fun tick(activeForegroundPackage: String? = null, isAppActive: Boolean = true) {
        mutex.withLock {
            val childId = pairingStore.get()?.childId ?: return
            val policy = policyRepository.getPolicy(childId)
            val previous = _snapshot.value
            val next = SessionEngine.tick(previous, now(), policy, isAppActive = isAppActive)
            recordUsageDelta(childId, previous, next)
            _snapshot.value = next
            persist(next, force = false)

            val ceiling = policy.dailyCeilingMinutes ?: com.meritscreen.core.common.config.AppConfig.DEFAULT_DAILY_CEILING_MINUTES
            val accruedSec = (next.minutesAccruedInBlock * 60f).toInt()
            val remSec = next.remainingBlockSeconds(now())
            _debugState.update { cur ->
                cur.copy(
                    activePackage = next.activePackage ?: activeForegroundPackage,
                    phase = next.phase,
                    isAppActive = isAppActive,
                    accruedMinutes = next.minutesAccruedInBlock,
                    accruedSeconds = accruedSec,
                    blockLimitMinutes = next.blockDurationMinutes,
                    remainingSeconds = remSec,
                    minutesUsedToday = next.minutesUsedToday,
                    dailyCeilingMinutes = ceiling,
                )
            }
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
        val next = SessionEngine.returnHome(_snapshot.value, now())
        _snapshot.value = next
        persist(next, force = true)
        updateDebugEvent("Returned to Home")
        next
    }

    suspend fun debugAddMinutes(minutes: Float) = mutex.withLock {
        val previous = _snapshot.value
        val newAccrued = (previous.minutesAccruedInBlock + minutes)
            .coerceIn(0f, previous.blockDurationMinutes.toFloat())
        val newUsedToday = (previous.minutesUsedToday + minutes.toInt()).coerceAtLeast(0)
        var next = previous.copy(
            minutesAccruedInBlock = newAccrued,
            minutesUsedToday = newUsedToday,
        )
        if (newAccrued >= previous.blockDurationMinutes && previous.phase == SessionPhase.InBlock) {
            next = next.copy(phase = SessionPhase.QuizDue, blockStartedElapsedMs = null)
        }
        _snapshot.value = next
        persist(next, force = true)
        val sign = if (minutes >= 0) "+" else ""
        updateDebugEvent("Debug: ${sign}${minutes.toInt()}m adjusted")
    }

    suspend fun debugTriggerQuiz() = mutex.withLock {
        val previous = _snapshot.value
        val next = previous.copy(
            phase = SessionPhase.QuizDue,
            minutesAccruedInBlock = previous.blockDurationMinutes.toFloat(),
            blockStartedElapsedMs = null,
        )
        _snapshot.value = next
        persist(next, force = true)
        updateDebugEvent("Debug: Quiz triggered manually")
    }

    suspend fun debugResetBlock() = mutex.withLock {
        val previous = _snapshot.value
        val next = previous.copy(
            phase = SessionPhase.Idle,
            activePackage = null,
            activeAppId = null,
            blockStartedElapsedMs = null,
            minutesAccruedInBlock = 0f,
            deviceShieldedUntilElapsedMs = null,
            quizGraceUntilElapsedMs = null,
        )
        _snapshot.value = next
        persist(next, force = true)
        updateDebugEvent("Debug: Session reset to Idle")
    }

    fun updateDebugEvent(event: String) {
        _debugState.update { it.copy(lastEvent = event) }
    }

    fun updateDebugLifecycle(
        state: com.meritscreen.feature.child.service.ForegroundLifecycleState,
        isAppActive: Boolean,
        isOverlayShowing: Boolean,
        packageName: String? = null,
    ) {
        _debugState.update {
            it.copy(
                lifecycleState = state,
                isAppActive = isAppActive,
                isOverlayShowing = isOverlayShowing,
                activePackage = packageName ?: it.activePackage,
            )
        }
    }

    fun updateDebugSyncTime(timeStr: String) {
        _debugState.update { it.copy(lastSyncTime = timeStr) }
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
