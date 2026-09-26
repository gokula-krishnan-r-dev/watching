package com.meritscreen.feature.devices

import com.meritscreen.core.firebase.device.DeviceRegistryClient
import com.meritscreen.core.security.pairing.ChildPairingStore
import javax.inject.Inject

/** Outcome of one heartbeat attempt, used by the caller to decide whether to force sign-out. */
sealed interface HeartbeatOutcome {
    data object Skipped : HeartbeatOutcome
    data object Ok : HeartbeatOutcome
    data object Revoked : HeartbeatOutcome
    data class Failed(val cause: Throwable) : HeartbeatOutcome
}

/**
 * Writes this device's liveness + Home-role status to Firestore and checks whether a
 * parent has revoked the device (the offline-safe "kill switch" — see
 * ARCHITECTURE.md "Offline & local enforcement"). One-shot only: no snapshot listener,
 * called from [DeviceHeartbeatWorker] on a bounded WorkManager cadence and opportunistically
 * on app foreground, never continuously.
 */
class DeviceHeartbeatUseCase @Inject constructor(
    private val pairingStore: ChildPairingStore,
    private val deviceRegistryClient: DeviceRegistryClient,
) {
    suspend operator fun invoke(
        isDefaultHome: Boolean,
        deviceModel: String,
        batteryPercent: Int? = null,
        osVersion: String? = null,
        appVersion: String? = null,
    ): HeartbeatOutcome {
        val credential = pairingStore.get() ?: return HeartbeatOutcome.Skipped
        return try {
            deviceRegistryClient.heartbeat(
                familyId = credential.familyId,
                childId = credential.childId,
                deviceId = credential.deviceId,
                launcherDefault = isDefaultHome,
                model = deviceModel,
                batteryPercent = batteryPercent,
                osVersion = osVersion,
                appVersion = appVersion,
            )
            val status = deviceRegistryClient.fetchStatus(
                familyId = credential.familyId,
                childId = credential.childId,
                deviceId = credential.deviceId,
            )
            if (status.registered && status.revoked) {
                HeartbeatOutcome.Revoked
            } else {
                HeartbeatOutcome.Ok
            }
        } catch (t: Throwable) {
            HeartbeatOutcome.Failed(t)
        }
    }
}
