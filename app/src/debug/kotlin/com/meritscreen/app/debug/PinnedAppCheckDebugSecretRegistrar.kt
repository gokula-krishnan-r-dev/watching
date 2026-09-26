package com.meritscreen.app.debug

import androidx.annotation.Keep
import com.google.firebase.appcheck.debug.InternalDebugSecretProvider
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.meritscreen.app.BuildConfig

/**
 * Supplies the stable App Check debug secret from [BuildConfig.APP_CHECK_DEBUG_TOKEN]
 * so DebugAppCheckProvider does not mint a random UUID when `SystemProperties.set` is
 * blocked on emulators. Register the same UUID in Firebase Console → App Check →
 * Manage debug tokens (see `./scripts/run-demo.sh`).
 *
 * Uses an explicit [InternalDebugSecretProvider] implementation (not a Kotlin SAM) so
 * Firebase's optionalProvider wiring always resolves the component.
 */
@Keep
class PinnedAppCheckDebugSecretRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> {
        val token = BuildConfig.APP_CHECK_DEBUG_TOKEN.trim()
        if (token.isEmpty()) return emptyList()
        val provider = object : InternalDebugSecretProvider {
            override fun getDebugSecret(): String = token
        }
        return listOf(
            Component.builder(InternalDebugSecretProvider::class.java)
                .name("meritscreen-app-check-debug-secret")
                .factory { provider }
                .build(),
        )
    }
}
