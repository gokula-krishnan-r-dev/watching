package com.meritscreen.feature.child.domain

import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.QuizMode
import com.meritscreen.core.common.domain.SessionPhase
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.max

data class SessionSnapshot(
    val phase: SessionPhase = SessionPhase.Idle,
    val activePackage: String? = null,
    val activeAppId: String? = null,
    val blockStartedElapsedMs: Long? = null,
    val blockDurationMinutes: Int = AppConfig.DEFAULT_BLOCK_MINUTES,
    val minutesAccruedInBlock: Float = 0f,
    val deviceShieldedUntilElapsedMs: Long? = null,
    val cooldownMinutes: Int = AppConfig.DEFAULT_COOLDOWN_MINUTES,
    val dayKey: String = todayKey(),
    val minutesUsedToday: Int = 0,
    val lastTickElapsedMs: Long? = null,
    val quizGraceUntilElapsedMs: Long? = null,
) {
    fun remainingBlockMinutes(nowElapsedMs: Long = 0L): Int {
        if (phase != SessionPhase.InBlock) {
            return max(0, (blockDurationMinutes - minutesAccruedInBlock).toInt())
        }
        return max(0, (blockDurationMinutes - minutesAccruedInBlock).toInt())
    }

    fun remainingBlockSeconds(nowElapsedMs: Long = 0L): Int {
        val remSec = max(0, ((blockDurationMinutes - minutesAccruedInBlock) * 60f).toInt())
        return remSec
    }

    fun remainingCooldownSeconds(nowElapsedMs: Long): Int {
        val until = deviceShieldedUntilElapsedMs ?: return 0
        return max(0, ((until - nowElapsedMs) / 1000L).toInt())
    }

    fun dailyRemainingMinutes(policy: ChildPolicy): Int {
        val ceiling = policy.dailyCeilingMinutes ?: AppConfig.DEFAULT_DAILY_CEILING_MINUTES
        return max(0, ceiling - minutesUsedToday)
    }

    companion object {
        fun todayKey(): String = LocalDate.now(ZoneOffset.UTC).toString()
    }
}

/**
 * Pure session engine — no Android / Firebase. Call sites supply elapsedRealtime and policy.
 * Fail lock is always device-wide for all non-emergency apps.
 */
object SessionEngine {

    fun onNewDay(snapshot: SessionSnapshot, dayKey: String = SessionSnapshot.todayKey()): SessionSnapshot {
        if (snapshot.dayKey == dayKey) return snapshot
        return snapshot.copy(
            dayKey = dayKey,
            minutesUsedToday = 0,
            minutesAccruedInBlock = 0f,
            phase = if (snapshot.phase == SessionPhase.InBlock) SessionPhase.Idle else snapshot.phase,
            activePackage = if (snapshot.phase == SessionPhase.InBlock) null else snapshot.activePackage,
            activeAppId = if (snapshot.phase == SessionPhase.InBlock) null else snapshot.activeAppId,
            blockStartedElapsedMs = null,
            quizGraceUntilElapsedMs = null,
        )
    }

    /**
     * Ticks the session clock by [nowElapsedMs].
     * If [isAppActive] is true and [snapshot.phase] is [SessionPhase.InBlock], active screen time
     * is accurately accumulated. If [isAppActive] is false (e.g. app minimized, on launcher,
     * or screen off), no time is accrued for the background app.
     */
    fun tick(
        snapshot: SessionSnapshot,
        nowElapsedMs: Long,
        policy: ChildPolicy,
        isAppActive: Boolean = true,
    ): SessionSnapshot {
        var state = onNewDay(snapshot)
        state = expireShieldIfNeeded(state, nowElapsedMs)

        if (state.phase == SessionPhase.InBlock) {
            val lastTick = state.lastTickElapsedMs
            val deltaMs = if (lastTick != null && isAppActive) {
                (nowElapsedMs - lastTick).coerceAtLeast(0L)
            } else if (state.blockStartedElapsedMs != null && state.minutesAccruedInBlock == 0f && isAppActive) {
                (nowElapsedMs - state.blockStartedElapsedMs).coerceAtLeast(0L)
            } else {
                0L
            }

            if (isAppActive && deltaMs > 0L) {
                val previousAccruedMs = kotlin.math.round(state.minutesAccruedInBlock * 60_000.0).toLong()
                val newAccruedMs = previousAccruedMs + deltaMs
                val newAccruedMinutes = (newAccruedMs / 60_000.0).toFloat()

                val previousCompletedMins = (previousAccruedMs / 60_000L).toInt()
                val newCompletedMins = (newAccruedMs / 60_000L).toInt()
                val newlyCompletedMinutes = (newCompletedMins - previousCompletedMins).coerceAtLeast(0)
                val today = state.minutesUsedToday + newlyCompletedMinutes

                state = state.copy(
                    minutesAccruedInBlock = newAccruedMinutes,
                    minutesUsedToday = today,
                    lastTickElapsedMs = nowElapsedMs,
                )

                val ceiling = policy.dailyCeilingMinutes ?: AppConfig.DEFAULT_DAILY_CEILING_MINUTES
                val isUnderGrace = state.quizGraceUntilElapsedMs != null && nowElapsedMs < state.quizGraceUntilElapsedMs
                val ceilingHit = today >= ceiling && !isUnderGrace
                val blockHit = newAccruedMinutes >= state.blockDurationMinutes && !isUnderGrace

                if (ceilingHit || (policy.quizMode == QuizMode.DAILY_CEILING && ceilingHit)) {
                    state = state.copy(phase = SessionPhase.QuizDue, blockStartedElapsedMs = null)
                } else if (blockHit && policy.quizMode == QuizMode.APP_BLOCK) {
                    state = state.copy(phase = SessionPhase.QuizDue, blockStartedElapsedMs = null)
                }
            } else {
                // Inactive tick (minimized / paused / screen off): advance lastTick without accruing time
                state = state.copy(lastTickElapsedMs = nowElapsedMs)
            }
        } else if (state.phase == SessionPhase.Idle) {
            // Track active screen time while child is exploring launcher or educational activities
            val lastTick = state.lastTickElapsedMs
            val deltaMs = if (lastTick != null && isAppActive) {
                (nowElapsedMs - lastTick).coerceAtLeast(0L)
            } else {
                0L
            }
            if (isAppActive && deltaMs > 0L) {
                val previousAccruedMs = kotlin.math.round(state.minutesAccruedInBlock * 60_000.0).toLong()
                val accruedMs = previousAccruedMs + deltaMs
                val newlyCompletedMinutes = (accruedMs / 60_000L).toInt()
                val leftoverMs = accruedMs % 60_000L
                val today = state.minutesUsedToday + newlyCompletedMinutes
                state = state.copy(
                    minutesAccruedInBlock = (leftoverMs / 60_000.0).toFloat(),
                    minutesUsedToday = today,
                    lastTickElapsedMs = nowElapsedMs,
                )
            } else {
                state = state.copy(lastTickElapsedMs = nowElapsedMs)
            }
        }
        return state
    }

    fun openApp(
        snapshot: SessionSnapshot,
        nowElapsedMs: Long,
        rule: AppRule,
        policy: ChildPolicy,
    ): SessionSnapshot {
        var state = tick(snapshot, nowElapsedMs, policy, isAppActive = true)
        if (rule.isEmergency || isEmergencyPackage(rule.packageOrBundleId, policy)) {
            return state
        }
        if (!rule.allowed) return state
        if (policy.paused) return state
        if (state.phase == SessionPhase.Shielded) return state
        if (state.phase == SessionPhase.QuizDue) return state

        val isUnderGrace = state.quizGraceUntilElapsedMs != null && nowElapsedMs < state.quizGraceUntilElapsedMs
        val ceiling = policy.dailyCeilingMinutes
        if (ceiling != null && state.minutesUsedToday >= ceiling && !isUnderGrace) {
            return state.copy(phase = SessionPhase.QuizDue)
        }

        // If resuming the same application that was already active in this block, resume without wiping time
        if (state.activePackage == rule.packageOrBundleId && state.phase == SessionPhase.InBlock) {
            return state.copy(lastTickElapsedMs = nowElapsedMs)
        }

        val blockMinutes = rule.blockMinutes.coerceIn(1, 240)
        return when (policy.quizMode) {
            QuizMode.EVERY_SESSION -> state.copy(
                phase = SessionPhase.QuizDue,
                activePackage = rule.packageOrBundleId,
                activeAppId = rule.appId,
                blockDurationMinutes = blockMinutes,
                minutesAccruedInBlock = 0f,
                blockStartedElapsedMs = null,
                lastTickElapsedMs = nowElapsedMs,
            )
            else -> state.copy(
                phase = SessionPhase.InBlock,
                activePackage = rule.packageOrBundleId,
                activeAppId = rule.appId,
                blockDurationMinutes = blockMinutes,
                minutesAccruedInBlock = 0f,
                blockStartedElapsedMs = nowElapsedMs,
                lastTickElapsedMs = nowElapsedMs,
                cooldownMinutes = rule.cooldownMinutes.coerceIn(1, 180),
                quizGraceUntilElapsedMs = null,
            )
        }
    }

    fun onQuizPassed(
        snapshot: SessionSnapshot,
        nowElapsedMs: Long,
        rule: AppRule?,
        policy: ChildPolicy,
    ): SessionSnapshot {
        val grant = (rule?.grantOnPassMinutes ?: policy.defaultBlockMinutes)
            .coerceIn(AppConfig.SESSION_CHUNK_MIN_MINUTES, AppConfig.SESSION_CHUNK_MAX_MINUTES)
        var extra = if (policy.rewardsEnabled) policy.extraMinutesOnPass else 0
        if (policy.rewardsEnabled && policy.weekendBonusEnabled && isWeekendLocal()) {
            extra += AppConfig.WEEKEND_BONUS_EXTRA_MINUTES
        }
        val block = (grant + extra).coerceIn(
            AppConfig.SESSION_CHUNK_MIN_MINUTES,
            AppConfig.SESSION_CHUNK_MAX_MINUTES,
        )

        // Grant a grace window for the newly unlocked block so ceiling check does not immediately re-lock
        val graceUntil = nowElapsedMs + (block * 60_000L)

        return snapshot.copy(
            phase = SessionPhase.InBlock,
            blockDurationMinutes = block,
            minutesAccruedInBlock = 0f,
            blockStartedElapsedMs = nowElapsedMs,
            lastTickElapsedMs = nowElapsedMs,
            deviceShieldedUntilElapsedMs = null,
            quizGraceUntilElapsedMs = graceUntil,
            activePackage = rule?.packageOrBundleId ?: snapshot.activePackage,
            activeAppId = rule?.appId ?: snapshot.activeAppId,
        )
    }

    fun onQuizFailed(
        snapshot: SessionSnapshot,
        nowElapsedMs: Long,
        policy: ChildPolicy,
        rule: AppRule?,
    ): SessionSnapshot {
        val cooldown = (rule?.cooldownMinutes ?: policy.defaultCooldownMinutes).coerceIn(1, 180)
        return snapshot.copy(
            phase = SessionPhase.Shielded,
            deviceShieldedUntilElapsedMs = nowElapsedMs + cooldown * 60_000L,
            cooldownMinutes = cooldown,
            blockStartedElapsedMs = null,
            minutesAccruedInBlock = 0f,
            lastTickElapsedMs = nowElapsedMs,
            quizGraceUntilElapsedMs = null,
        )
    }

    fun expireShieldIfNeeded(snapshot: SessionSnapshot, nowElapsedMs: Long): SessionSnapshot {
        if (snapshot.phase != SessionPhase.Shielded) return snapshot
        val until = snapshot.deviceShieldedUntilElapsedMs ?: return snapshot.copy(phase = SessionPhase.Idle)
        if (nowElapsedMs >= until) {
            return snapshot.copy(
                phase = SessionPhase.Idle,
                deviceShieldedUntilElapsedMs = null,
                activePackage = null,
                activeAppId = null,
            )
        }
        return snapshot
    }

    /**
     * Parent PIN override — ends the device-wide fail lock immediately without waiting
     * for [deviceShieldedUntilElapsedMs]. Leaves the child in [SessionPhase.Idle] so
     * approved apps are launchable again.
     */
    fun endFailLock(snapshot: SessionSnapshot): SessionSnapshot {
        if (snapshot.phase != SessionPhase.Shielded && snapshot.deviceShieldedUntilElapsedMs == null) {
            return snapshot
        }
        return snapshot.copy(
            phase = SessionPhase.Idle,
            deviceShieldedUntilElapsedMs = null,
            activePackage = null,
            activeAppId = null,
            blockStartedElapsedMs = null,
            minutesAccruedInBlock = 0f,
            quizGraceUntilElapsedMs = null,
        )
    }

    fun returnHome(snapshot: SessionSnapshot, nowElapsedMs: Long): SessionSnapshot {
        return snapshot.copy(
            phase = SessionPhase.Idle,
            activePackage = null,
            activeAppId = null,
            blockStartedElapsedMs = null,
            minutesAccruedInBlock = 0f,
            lastTickElapsedMs = nowElapsedMs,
        )
    }

    fun canLaunch(snapshot: SessionSnapshot, rule: AppRule, policy: ChildPolicy): Boolean {
        if (rule.isEmergency || isEmergencyPackage(rule.packageOrBundleId, policy)) return true
        if (!rule.allowed) return false
        if (policy.paused) return false
        if (snapshot.phase == SessionPhase.Shielded) return false
        val ceiling = policy.dailyCeilingMinutes
        if (ceiling != null && snapshot.minutesUsedToday >= ceiling) {
            val isUnderGrace = snapshot.quizGraceUntilElapsedMs != null &&
                System.currentTimeMillis() < snapshot.quizGraceUntilElapsedMs
            if (!isUnderGrace) return false
        }
        return true
    }

    fun isEmergencyPackage(packageOrBundleId: String, policy: ChildPolicy): Boolean {
        val lower = packageOrBundleId.lowercase()
        if (lower.contains("dialer") || lower.contains("telecom") || lower.contains("phone")) return true
        return policy.emergencyApps.any { it.equals(packageOrBundleId, ignoreCase = true) }
    }

    /** Saturday/Sunday in the device local zone — used for weekend XP/minute bonus. */
    fun isWeekendLocal(now: LocalDate = LocalDate.now(ZoneId.systemDefault())): Boolean {
        val day = now.dayOfWeek
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
    }
}
