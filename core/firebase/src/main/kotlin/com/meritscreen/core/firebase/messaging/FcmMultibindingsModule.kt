package com.meritscreen.core.firebase.messaging

import com.meritscreen.core.common.messaging.FcmMessageHandler
import com.meritscreen.core.common.messaging.FcmTokenRegistrar
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Guarantees the `Set<FcmMessageHandler>` / `Set<FcmTokenRegistrar>` bindings resolve (as an
 * empty set) even in build variants where no feature module contributes a handler yet.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FcmMultibindingsModule {
    @Multibinds
    abstract fun fcmMessageHandlers(): Set<FcmMessageHandler>

    @Multibinds
    abstract fun fcmTokenRegistrars(): Set<FcmTokenRegistrar>
}
