package com.meritscreen.core.firebase.messaging

import com.google.firebase.messaging.FirebaseMessaging
import com.meritscreen.core.common.dispatchers.AppDispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper so feature modules never need their own `firebase-messaging` dependency just
 * to read the current FCM token — mirrors the `DeviceRegistryClient` pattern of keeping
 * Firebase SDK types confined to `:core:firebase`.
 */
interface PushTokenProvider {
    suspend fun currentToken(): String?
}

@Singleton
class FirebasePushTokenProvider @Inject constructor(
    private val messaging: FirebaseMessaging,
    private val dispatchers: AppDispatchers,
) : PushTokenProvider {
    override suspend fun currentToken(): String? = withContext(dispatchers.io) {
        runCatching { messaging.token.await() }.getOrNull()
    }
}
