package com.meritscreen.app.internal

import androidx.annotation.Keep
import com.google.firebase.appcheck.debug.InternalDebugSecretProvider
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.meritscreen.app.BuildConfig

/**
 * Supplies the stable App Check debug secret for closed-tester (`internal`) APKs so
 * Firebase AI Logic works when sideloaded (Play Integrity is unreliable off Play Store).
 * Register the same UUID in Firebase Console → App Check → Manage debug tokens.
 */
@Keep
class PinnedAppCheckDebugSecretRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> {
        if (!BuildConfig.USE_APP_CHECK_DEBUG_PROVIDER) return emptyList()
        val token = BuildConfig.APP_CHECK_DEBUG_TOKEN.trim()
        if (token.isEmpty()) return emptyList()
        val provider = object : InternalDebugSecretProvider {
            override fun getDebugSecret(): String = token
        }
        return listOf(
            Component.builder(InternalDebugSecretProvider::class.java)
                .name("meritscreen-app-check-debug-secret-internal")
                .factory { provider }
                .build(),
        )
    }
}
