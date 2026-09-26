package com.meritscreen.feature.child.data

import com.meritscreen.core.common.messaging.FcmMessageHandler
import com.meritscreen.feature.child.domain.UnpairChildDeviceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Parent changed policy/appRules → pull immediately while the process is alive, and also
 * enqueue the bounded [PolicySyncWorker] so a killed process still catches up via WorkManager.
 * Never touches the network on this (FCM callback) thread.
 */
class PolicySyncFcmHandler @Inject constructor(
    private val scheduler: PolicySyncScheduler,
    private val syncCoordinator: ChildPolicySyncCoordinator,
) : FcmMessageHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun handle(data: Map<String, String>): Boolean {
        if (data["type"] != "policy_sync") return false
        scheduler.runSoon()
        scope.launch { runCatching { syncCoordinator.refreshNow() } }
        return true
    }
}

/**
 * Parent reset the Parent PIN → pull family root so Keystore-backed offline PIN stays current.
 * Reuses the policy sync worker (which also refreshes parentPinHash).
 */
class PinSyncFcmHandler @Inject constructor(
    private val scheduler: PolicySyncScheduler,
    private val syncCoordinator: ChildPolicySyncCoordinator,
) : FcmMessageHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun handle(data: Map<String, String>): Boolean {
        if (data["type"] != "pin_sync") return false
        scheduler.runSoon()
        scope.launch { runCatching { syncCoordinator.refreshNow() } }
        return true
    }
}

/**
 * Parent deleted the family → unpair + wipe local Room (except builtin quiz bank).
 * Work is launched off the FCM thread; the use case is idempotent if already unpaired.
 */
class FamilyDeletedFcmHandler @Inject constructor(
    private val unpairChildDevice: UnpairChildDeviceUseCase,
    private val deviceLifecycle: ChildDeviceLifecycleCoordinator,
) : FcmMessageHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun handle(data: Map<String, String>): Boolean {
        if (data["type"] != "family_deleted") return false
        scope.launch {
            runCatching { unpairChildDevice() }
            deviceLifecycle.markForceSignedOut()
        }
        return true
    }
}
