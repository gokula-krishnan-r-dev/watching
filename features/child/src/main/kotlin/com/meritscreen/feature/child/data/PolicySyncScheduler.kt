package com.meritscreen.feature.child.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns all WorkManager scheduling for policy sync. The periodic job is a **fallback** for
 * when the process is dead and no FCM push arrives; while the app/launcher is alive, network
 * recovery and manual refresh are handled in-process by [ChildPolicySyncCoordinator] directly
 * (no need to round-trip through WorkManager when we already have a live coroutine scope).
 */
@Singleton
class PolicySyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 6-hour bounded fallback in case both the FCM push and the in-process paths miss. */
    fun ensureScheduled() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<PolicySyncWorker>(Duration.ofHours(6))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(5))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Triggered by an FCM `policy_sync` push — works even if the process was killed. */
    fun runSoon() {
        val request = OneTimeWorkRequestBuilder<PolicySyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            EXPEDITED_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(EXPEDITED_WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "policy_sync_periodic"
        const val EXPEDITED_WORK_NAME = "policy_sync_expedited"
    }
}
