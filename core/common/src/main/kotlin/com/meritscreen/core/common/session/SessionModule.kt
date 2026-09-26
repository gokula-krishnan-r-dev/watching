package com.meritscreen.core.common.session

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    @Singleton
    abstract fun bindSessionRoleRepository(
        impl: DataStoreSessionRoleRepository,
    ): SessionRoleRepository

    @Binds
    @Singleton
    abstract fun bindParentSessionRepository(
        impl: DataStoreParentSessionRepository,
    ): ParentSessionRepository
}
