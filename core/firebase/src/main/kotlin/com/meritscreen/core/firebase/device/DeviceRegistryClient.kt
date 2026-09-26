package com.meritscreen.core.firebase.device

import com.meritscreen.core.common.domain.DeviceRemoteStatus
import com.meritscreen.core.common.domain.InstalledAppSummary
import kotlinx.coroutines.flow.Flow

/**
 * Child-device side of the device-management model (Phase 6). Writes are scoped to this
 * device's own `devices/{deviceId}` document — enforced server-side by
 * `firestore.rules` (`childDeviceOf(...) && token.deviceId == deviceId`), not just by
 * client code, so a compromised client cannot rewrite another device's record.
 *
 * Deliberately avoids Home-path listeners: revocation is watched from
 * `ChildDeviceLifecycleCoordinator` (process-scoped), plus FCM + heartbeat WorkManager.
 */
interface DeviceRegistryClient {
    suspend fun heartbeat(
        familyId: String,
        childId: String,
        deviceId: String,
        launcherDefault: Boolean,
        model: String,
        batteryPercent: Int? = null,
        osVersion: String? = null,
        appVersion: String? = null,
    )

    suspend fun uploadInstalledApps(
        familyId: String,
        childId: String,
        deviceId: String,
        apps: List<InstalledAppSummary>,
    )

    suspend fun fetchStatus(familyId: String, childId: String, deviceId: String): DeviceRemoteStatus

    /**
     * Live snapshot of this device's own Firestore doc (revocation kill-switch).
     * Started from [ChildDeviceLifecycleCoordinator], never from Home ViewModel.
     */
    fun observeStatus(familyId: String, childId: String, deviceId: String): Flow<DeviceRemoteStatus>

    /**
     * Registers this device's current FCM token so Cloud Functions can push a "policy
     * updated" / "device revoked" data message (Phase 7) — see `functions/src/index.ts`.
     */
    suspend fun registerPushToken(familyId: String, childId: String, deviceId: String, token: String)
}
