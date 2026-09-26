package com.meritscreen.feature.onboarding.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.meritscreen.feature.onboarding.data.DataStoreOnboardingDraftRepository
import com.meritscreen.feature.onboarding.data.OnboardingDraftRepository
import com.meritscreen.feature.onboarding.data.OnboardingDraftSerializer
import com.meritscreen.feature.onboarding.domain.OnboardingDraft
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.onboardingDraftStore: DataStore<OnboardingDraft> by dataStore(
    fileName = "onboarding_draft.json",
    serializer = OnboardingDraftSerializer,
)

@Module
@InstallIn(SingletonComponent::class)
object OnboardingDataStoreModule {
    @Provides
    @Singleton
    fun provideOnboardingDraftDataStore(@ApplicationContext context: Context): DataStore<OnboardingDraft> =
        context.onboardingDraftStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingBindingsModule {
    @Binds
    @Singleton
    abstract fun bindOnboardingDraftRepository(
        impl: DataStoreOnboardingDraftRepository,
    ): OnboardingDraftRepository
}
