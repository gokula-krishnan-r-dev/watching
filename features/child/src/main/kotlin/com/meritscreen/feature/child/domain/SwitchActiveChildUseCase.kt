package com.meritscreen.feature.child.domain

import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.core.firebase.auth.AuthClient
import com.meritscreen.core.firebase.error.FirebaseErrorMapper
import com.meritscreen.core.firebase.pairing.PairingClient
import com.meritscreen.core.network.NetworkMonitor
import com.meritscreen.core.security.pairing.ChildPairingCredential
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.feature.child.data.ChildDeviceLifecycleCoordinator
import com.meritscreen.feature.child.data.ChildPolicySyncCoordinator
import com.meritscreen.feature.child.data.UsageSyncCoordinator
import javax.inject.Inject

/**
 * PIN-gated (caller verifies PIN first) switch between child profiles on one device.
 *
 * Flushes the outgoing child's session timers, remints Firebase Auth claims for the
 * target child, reloads that child's Room session, and restarts sync/heartbeat watchers.
 */
class SwitchActiveChildUseCase @Inject constructor(
    private val pairingStore: ChildPairingStore,
    private val pairingClient: PairingClient,
    private val authClient: AuthClient,
    private val sessionController: ChildSessionController,
    private val syncCoordinator: ChildPolicySyncCoordinator,
    private val usageSync: UsageSyncCoordinator,
    private val lifecycleCoordinator: ChildDeviceLifecycleCoordinator,
    private val networkMonitor: NetworkMonitor,
) {
    suspend operator fun invoke(targetChildId: String): Outcome<ChildPairingCredential> {
        val active = pairingStore.get()
            ?: return Outcome.Failure(AppError.Auth("This device is not paired."))
        if (active.childId == targetChildId) {
            return Outcome.Success(active)
        }
        val target = pairingStore.listProfiles()
            .firstOrNull { it.childId == targetChildId }
            ?: return Outcome.Failure(
                AppError.Validation("That child is not paired on this device."),
            )
        if (!networkMonitor.isCurrentlyOnline()) {
            return Outcome.Failure(AppError.Network())
        }
        return try {
            lifecycleCoordinator.stop()
            syncCoordinator.stop()
            runCatching { usageSync.syncNow() }
            sessionController.flushActive()

            val remint = pairingClient.activateChildOnDevice(
                childId = targetChildId,
                deviceId = active.deviceId,
            )
            authClient.signInWithCustomToken(remint.customToken)
            pairingStore.set(
                target.copy(
                    parentPinHash = remint.parentPinHash.ifBlank { target.parentPinHash },
                    displayName = remint.displayName.ifBlank { target.displayName },
                    avatarId = remint.avatarId.ifBlank { target.avatarId },
                    deviceId = remint.deviceId,
                    familyId = remint.familyId,
                ),
            )
            sessionController.switchHydrateFromDisk()
            syncCoordinator.start()
            lifecycleCoordinator.start()
            runCatching { syncCoordinator.refreshNow() }
            Outcome.Success(pairingStore.get() ?: target)
        } catch (error: AppErrorException) {
            runCatching {
                pairingStore.setActive(active.childId)
                lifecycleCoordinator.start()
                syncCoordinator.start()
            }
            Outcome.Failure(error.error)
        } catch (error: Throwable) {
            runCatching {
                pairingStore.setActive(active.childId)
                lifecycleCoordinator.start()
                syncCoordinator.start()
            }
            Outcome.Failure(FirebaseErrorMapper.from(error))
        }
    }
}
