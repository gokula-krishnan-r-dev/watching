package com.meritscreen.feature.parent.data

import com.meritscreen.core.common.messaging.FcmTokenRegistrar
import javax.inject.Inject

/**
 * Triggered whenever `FirebaseMessagingService.onNewToken` fires to push the new token
 * onto this parent device's own Firestore doc.
 */
class ParentPushTokenRegistrar @Inject constructor(
    private val scheduler: ParentPushTokenRegistrationScheduler,
) : FcmTokenRegistrar {
    override fun onTokenRefreshed(token: String) {
        scheduler.registerNow()
    }
}
