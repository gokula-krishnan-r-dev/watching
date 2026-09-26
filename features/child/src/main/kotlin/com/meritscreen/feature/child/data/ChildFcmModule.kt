package com.meritscreen.feature.child.data

import com.meritscreen.core.common.messaging.FcmMessageHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ChildFcmModule {
    @Binds
    @IntoSet
    abstract fun bindPolicySyncFcmHandler(impl: PolicySyncFcmHandler): FcmMessageHandler

    @Binds
    @IntoSet
    abstract fun bindPinSyncFcmHandler(impl: PinSyncFcmHandler): FcmMessageHandler

    @Binds
    @IntoSet
    abstract fun bindFamilyDeletedFcmHandler(impl: FamilyDeletedFcmHandler): FcmMessageHandler
}
