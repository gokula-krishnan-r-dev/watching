package com.meritscreen.app

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.meritscreen.core.common.logging.ReleaseTree
import com.meritscreen.core.ui.theme.AppThemeManager
import com.meritscreen.core.firebase.FirebaseEmulators
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

/**
 * [Configuration.Provider] gives WorkManager an on-demand config built from Hilt's
 * [HiltWorkerFactory], so `:features:devices`' [DeviceHeartbeatWorker][com.meritscreen.feature.devices.DeviceHeartbeatWorker]
 * gets constructor-injected dependencies. The default `androidx-startup` WorkManager
 * initializer is disabled in the manifest so this on-demand config is the only one used.
 */
@HiltAndroidApp
class MeritScreenApplication : Application(), Configuration.Provider {

    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        enableStrictModeInDebug()
        plantLogger()
        AppThemeManager.init(this)
        FirebaseApp.initializeApp(this)
        // Must run after FirebaseApp init (needs persistenceKey) and before installAppCheck
        // so DebugAppCheckProvider reads our stable secret instead of a random UUID.
        seedAppCheckDebugSecretFromBuildConfig()
        connectFirebaseEmulatorsIfRequested()
        installAppCheck()
    }

    /**
     * Debug-only: route Auth / Firestore / Functions / Storage to the Firebase Emulator Suite
     * on the host (`10.0.2.2`). Enabled with `-PuseFirebaseEmulators` so cloud demos stay clean.
     */
    private fun connectFirebaseEmulatorsIfRequested() {
        if (!BuildConfig.DEBUG || !BuildConfig.USE_FIREBASE_EMULATORS) return
        FirebaseEmulators.connect()
    }

        /**
     * Debug-only guardrails that fail fast on main-thread disk/network work and leaked
     * resources. Never enabled in release: it is a development aid, not user-facing behavior.
     */
    private fun enableStrictModeInDebug() {
        if (!BuildConfig.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build(),
        )
    }

    private fun plantLogger() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
    }

    /**
     * Installs App Check providers. Enforcement stays **off** until a release keystore
     * SHA-256 is registered and Functions env `APP_CHECK_ENFORCE=true` is set — see SECURITY.md.
     *
     * - Debug / `internal` (closed sideload testers): debug provider + registered token so
     *   Firebase AI Logic works off Play Store.
     * - Store `release`: Play Integrity.
     *
     * Pin a stable token via `appCheckDebugToken` in local.properties and register it in
     * Firebase Console → App Check → Manage debug tokens. `./scripts/run-demo.sh` registers
     * + pins; [seedAppCheckDebugSecretFromBuildConfig] also writes the SDK SharedPreferences
     * store because emulators block SystemProperties.set.
     */
    private fun installAppCheck() {
        val useDebugProvider = BuildConfig.DEBUG || BuildConfig.USE_APP_CHECK_DEBUG_PROVIDER
        if (useDebugProvider) {
            val pinned = BuildConfig.APP_CHECK_DEBUG_TOKEN.trim()
            if (pinned.isNotEmpty()) {
                // Best-effort: DebugAppCheckProvider historically also checked this property.
                pinAppCheckDebugToken(pinned)
            }
        }
        val factory = if (useDebugProvider) {
            loadFactory("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                ?: loadFactory("com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory")
        } else {
            loadFactory("com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory")
        }
        if (factory != null) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
        }
    }

    /**
     * Overwrites the debug provider's persisted secret with [BuildConfig.APP_CHECK_DEBUG_TOKEN]
     * so a previously minted random UUID cannot stick across installs.
     * Prefs name matches `StorageHelper` in firebase-appcheck-debug.
     */
    private fun seedAppCheckDebugSecretFromBuildConfig() {
        if (!BuildConfig.DEBUG && !BuildConfig.USE_APP_CHECK_DEBUG_PROVIDER) return
        val token = BuildConfig.APP_CHECK_DEBUG_TOKEN.trim()
        if (token.isEmpty()) return
        runCatching {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefsName = "com.google.firebase.appcheck.debug.store.$persistenceKey"
            getSharedPreferences(prefsName, MODE_PRIVATE)
                .edit()
                .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", token)
                .apply()
            Timber.d("Seeded App Check debug secret into %s", prefsName)
        }.onFailure { error ->
            Timber.w(error, "Failed to seed App Check debug secret")
        }
    }

    private fun pinAppCheckDebugToken(token: String) {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            clazz.getMethod("set", String::class.java, String::class.java)
                .invoke(null, "debug.firebase.appcheck.app_check_token", token)
            Timber.d("Pinned App Check debug token for this process")
        } catch (_: ReflectiveOperationException) {
            // Emulators often block SystemProperties.set; SharedPreferences seed + registrar cover this.
            Timber.d("App Check debug token relies on SharedPreferences seed / InternalDebugSecretProvider")
        } catch (_: RuntimeException) {
            Timber.d("App Check debug token relies on SharedPreferences seed / InternalDebugSecretProvider")
        }
    }

    private fun loadFactory(className: String): AppCheckProviderFactory? {
        return try {
            val clazz = Class.forName(className)
            clazz.getMethod("getInstance").invoke(null) as AppCheckProviderFactory
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: ClassCastException) {
            null
        }
    }
}
