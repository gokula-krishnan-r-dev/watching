package com.meritscreen.feature.child.domain

import com.meritscreen.core.analytics.AnalyticsEvent
import com.meritscreen.core.analytics.AnalyticsTracker
import com.meritscreen.core.common.session.SessionRoleRepository
import com.meritscreen.core.firebase.auth.AuthClient
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.core.security.pin.PinGateStore
import com.meritscreen.feature.child.data.ChildLocalDataWiper
import com.meritscreen.feature.child.data.ChildPolicySyncCoordinator
import com.meritscreen.feature.child.data.UsageSyncScheduler
import javax.inject.Inject

class UnpairChildDeviceUseCase @Inject constructor(
    private val pairingStore: ChildPairingStore,
    private val authClient: AuthClient,
    private val sessionRoleRepository: SessionRoleRepository,
    private val syncCoordinator: ChildPolicySyncCoordinator,
    private val usageSyncScheduler: UsageSyncScheduler,
    private val localDataWiper: ChildLocalDataWiper,
    private val pinGateStore: PinGateStore,
    private val analyticsTracker: AnalyticsTracker,
) {
    suspend operator fun invoke() {
        val profiles = pairingStore.listProfiles()
        syncCoordinator.stop()
        usageSyncScheduler.cancel()
        authClient.signOut()
        profiles.forEach { localDataWiper.wipeForChild(it.childId) }
        pinGateStore.clear()
        pairingStore.clear()
        sessionRoleRepository.clear()
        analyticsTracker.track(AnalyticsEvent.ChildDeviceUnpaired)
    }
}
