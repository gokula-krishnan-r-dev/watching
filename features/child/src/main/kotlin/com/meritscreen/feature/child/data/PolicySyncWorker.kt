package com.meritscreen.feature.child.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Bounded, WorkManager-scheduled one-shot policy/appRules pull. Enqueued by
 * [PolicySyncScheduler] — from a periodic fallback cadence, an FCM `policy_sync` push, or a
 * local network-recovery signal. Never a continuous Firestore listener — see
 * ARCHITECTURE.md "Real-time synchronization (Phase 7)".
 */
@HiltWorker
class PolicySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncCoordinator: ChildPolicySyncCoordinator,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pulled = syncCoordinator.refreshNow()
        return when {
            pulled -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
