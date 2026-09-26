package com.meritscreen.feature.devices

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
 * Ensures exactly one periodic heartbeat job exists. `KEEP` makes re-enqueuing (app start,
 * boot, process restart) idempotent — it never duplicates or resets the existing schedule.
 * 15 minutes is WorkManager's platform-enforced minimum for periodic work; this app never
 * asks for anything shorter, in line with the "no continuous background work" requirement.
 */
@Singleton
class DeviceHeartbeatScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureScheduled() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<DeviceHeartbeatWorker>(Duration.ofMinutes(30))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(5))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * One-shot, near-immediate revocation check — used when an FCM `device_revoked` push
     * arrives (Phase 7 defense-in-depth on top of the periodic poll). `REPLACE` coalesces
     * bursts into the latest request rather than queueing duplicates.
     */
    fun runOnceExpedited() {
        val request = OneTimeWorkRequestBuilder<DeviceHeartbeatWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            EXPEDITED_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "device_heartbeat"
        const val EXPEDITED_WORK_NAME = "device_heartbeat_expedited"
    }
}
