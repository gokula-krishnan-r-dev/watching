package com.meritscreen.feature.parent.data

import com.meritscreen.core.common.messaging.FcmTokenRegistrar
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ParentFcmModule {

    @Binds
    @IntoSet
    abstract fun bindParentPushTokenRegistrar(impl: ParentPushTokenRegistrar): FcmTokenRegistrar
}
