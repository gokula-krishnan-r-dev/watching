package com.meritscreen.core.firebase

import com.meritscreen.core.firebase.auth.AuthClient
import com.meritscreen.core.firebase.auth.FirebaseAuthClient
import com.meritscreen.core.firebase.child.ChildRemotePolicyClient
import com.meritscreen.core.firebase.child.ChildUsageRemoteClient
import com.meritscreen.core.firebase.child.FirestoreChildRemotePolicyClient
import com.meritscreen.core.firebase.child.FirestoreChildUsageRemoteClient
import com.meritscreen.core.firebase.device.DeviceRegistryClient
import com.meritscreen.core.firebase.device.FirestoreDeviceRegistryClient
import com.meritscreen.core.firebase.family.FamilyStore
import com.meritscreen.core.firebase.family.FirestoreFamilyStore
import com.meritscreen.core.firebase.family.FirestoreParentControlStore
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.core.firebase.messaging.FirebasePushTokenProvider
import com.meritscreen.core.firebase.messaging.PushTokenProvider
import com.meritscreen.core.firebase.pairing.FirebasePairingClient
import com.meritscreen.core.firebase.pairing.PairingClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAuthClient(impl: FirebaseAuthClient): AuthClient

    @Binds
    @Singleton
    abstract fun bindEmailOtpClient(impl: com.meritscreen.core.firebase.auth.FirebaseEmailOtpClient): com.meritscreen.core.firebase.auth.EmailOtpClient

    @Binds
    @Singleton
    abstract fun bindFamilyStore(impl: FirestoreFamilyStore): FamilyStore

    @Binds
    @Singleton
    abstract fun bindParentControlStore(impl: FirestoreParentControlStore): ParentControlStore

    @Binds
    @Singleton
    abstract fun bindChildRemotePolicyClient(impl: FirestoreChildRemotePolicyClient): ChildRemotePolicyClient

    @Binds
    @Singleton
    abstract fun bindPairingClient(impl: FirebasePairingClient): PairingClient

    @Binds
    @Singleton
    abstract fun bindDeviceRegistryClient(impl: FirestoreDeviceRegistryClient): DeviceRegistryClient

    @Binds
    @Singleton
    abstract fun bindChildUsageRemoteClient(impl: FirestoreChildUsageRemoteClient): ChildUsageRemoteClient

    @Binds
    @Singleton
    abstract fun bindPushTokenProvider(impl: FirebasePushTokenProvider): PushTokenProvider

    @Binds
    @Singleton
    abstract fun bindQuizAiPackClient(impl: com.meritscreen.core.firebase.ai.FirebaseQuizAiPackClient): com.meritscreen.core.firebase.ai.QuizAiPackClient

    @Binds
    @Singleton
    abstract fun bindNurseryCurriculumRemoteClient(impl: com.meritscreen.core.firebase.curriculum.FirestoreNurseryCurriculumRemoteClient): com.meritscreen.core.firebase.curriculum.NurseryCurriculumRemoteClient
}
