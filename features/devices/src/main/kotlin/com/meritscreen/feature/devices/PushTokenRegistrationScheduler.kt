package com.meritscreen.feature.devices

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bounded, coalesced trigger for [PushTokenRegistrationWorker] — called once at app/child
 * lifecycle start and again whenever `FirebaseMessagingService.onNewToken` fires. `REPLACE`
 * means rapid repeated calls collapse into the latest single job, never a queue.
 */
@Singleton
class PushTokenRegistrationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun registerNow() {
        val request = OneTimeWorkRequestBuilder<PushTokenRegistrationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "push_token_registration"
    }
}
