package com.meritscreen.feature.child.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Bounded, WorkManager-scheduled upload of pending usage/quiz/skill data — see [UsageSyncCoordinator]. */
@HiltWorker
class UsageSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: UsageSyncCoordinator,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val succeeded = coordinator.syncNow()
        return when {
            succeeded -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
