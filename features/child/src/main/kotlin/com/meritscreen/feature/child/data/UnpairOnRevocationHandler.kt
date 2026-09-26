package com.meritscreen.feature.child.data

import com.meritscreen.feature.child.domain.UnpairChildDeviceUseCase
import com.meritscreen.feature.devices.DeviceRevocationHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Wires heartbeat / FCM revocation to the shared unpair path and flips
 * [ChildDeviceLifecycleCoordinator.forceSignedOut] so Child Home exits immediately.
 */
class UnpairOnRevocationHandler @Inject constructor(
    private val unpairUseCase: UnpairChildDeviceUseCase,
    private val deviceLifecycle: ChildDeviceLifecycleCoordinator,
) : DeviceRevocationHandler {
    override suspend fun onDeviceRevoked() {
        unpairUseCase()
        deviceLifecycle.markForceSignedOut()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ChildDeviceBindingsModule {
    @Binds
    abstract fun bindDeviceRevocationHandler(impl: UnpairOnRevocationHandler): DeviceRevocationHandler
}
