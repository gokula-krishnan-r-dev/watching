package com.meritscreen.feature.devices

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meritscreen.core.firebase.device.DeviceRegistryClient
import com.meritscreen.core.firebase.messaging.PushTokenProvider
import com.meritscreen.core.security.pairing.ChildPairingStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Registers this device's current FCM token on its own `devices/{deviceId}` doc so Cloud
 * Functions can push "policy updated" / "device revoked" messages (Phase 7). Runs once per
 * app start and whenever `onNewToken` fires — never inline on the FCM callback thread.
 */
@HiltWorker
class PushTokenRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pairingStore: ChildPairingStore,
    private val deviceRegistryClient: DeviceRegistryClient,
    private val pushTokenProvider: PushTokenProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val credential = pairingStore.get() ?: return Result.success()
        return try {
            val token = pushTokenProvider.currentToken()
                ?: return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            deviceRegistryClient.registerPushToken(
                familyId = credential.familyId,
                childId = credential.childId,
                deviceId = credential.deviceId,
                token = token,
            )
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
