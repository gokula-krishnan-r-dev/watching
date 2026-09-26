package com.meritscreen.feature.devices

import com.meritscreen.core.common.messaging.FcmMessageHandler
import com.meritscreen.core.common.messaging.FcmTokenRegistrar
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class DevicesFcmModule {
    @Binds
    @IntoSet
    abstract fun bindDeviceRevokedFcmHandler(impl: DeviceRevokedFcmHandler): FcmMessageHandler

    @Binds
    @IntoSet
    abstract fun bindDevicePushTokenRegistrar(impl: DevicePushTokenRegistrar): FcmTokenRegistrar
}
