package com.meritscreen.feature.child.domain

import com.meritscreen.core.common.domain.SessionPhase
import com.meritscreen.feature.child.service.ForegroundLifecycleState

data class TimerDebugState(
    val activePackage: String? = null,
    val phase: SessionPhase = SessionPhase.Idle,
    val lifecycleState: ForegroundLifecycleState = ForegroundLifecycleState.LAUNCHER,
    val isAppActive: Boolean = false,
    val accruedMinutes: Float = 0f,
    val accruedSeconds: Int = 0,
    val blockLimitMinutes: Int = 30,
    val remainingSeconds: Int = 0,
    val minutesUsedToday: Int = 0,
    val dailyCeilingMinutes: Int = 120,
    val isOverlayShowing: Boolean = false,
    val lastEvent: String = "Engine initialized",
    val lastSyncTime: String = "Pending",
)
