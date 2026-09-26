package com.meritscreen.core.security

import com.meritscreen.core.security.pin.Pbkdf2PinHasher
import com.meritscreen.core.security.pin.PinHasher
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.core.security.pairing.SecureChildPairingStore
import com.meritscreen.core.security.storage.KeystoreSecureStorage
import com.meritscreen.core.security.storage.SecureStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    @Singleton
    abstract fun bindPinHasher(impl: Pbkdf2PinHasher): PinHasher

    @Binds
    @Singleton
    abstract fun bindSecureStorage(impl: KeystoreSecureStorage): SecureStorage

    @Binds
    @Singleton
    abstract fun bindChildPairingStore(impl: SecureChildPairingStore): ChildPairingStore
}
