package com.meritscreen.feature.devices

import com.meritscreen.core.common.messaging.FcmMessageHandler
import com.meritscreen.core.common.messaging.FcmTokenRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Parent revoked this device → unpair immediately (do not wait for WorkManager), and still
 * enqueue an expedited heartbeat as a second path if the unpair coroutine is delayed.
 */
class DeviceRevokedFcmHandler @Inject constructor(
    private val revocationHandler: DeviceRevocationHandler,
    private val heartbeatScheduler: DeviceHeartbeatScheduler,
) : FcmMessageHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun handle(data: Map<String, String>): Boolean {
        if (data["type"] != "device_revoked") return false
        heartbeatScheduler.runOnceExpedited()
        scope.launch { runCatching { revocationHandler.onDeviceRevoked() } }
        return true
    }
}

/** Pushes a refreshed FCM token onto this device's own Firestore doc. */
class DevicePushTokenRegistrar @Inject constructor(
    private val scheduler: PushTokenRegistrationScheduler,
) : FcmTokenRegistrar {
    override fun onTokenRefreshed(token: String) {
        scheduler.registerNow()
    }
}
