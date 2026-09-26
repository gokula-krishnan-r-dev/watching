package com.meritscreen.feature.applications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.meritscreen.core.common.dispatchers.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamically registered receiver for install/uninstall/update broadcasts.
 * Debounces bursts (e.g. Play Store multi-package updates) then notifies with the
 * affected package names so local icon caches can invalidate precisely.
 */
@Singleton
class PackageChangeMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
    private val iconLoader: AppIconLoader,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private var debounceJob: Job? = null
    private var receiver: BroadcastReceiver? = null
    private val pendingPackages = linkedSetOf<String>()

    fun start(onPackagesChanged: suspend (changedPackages: Set<String>) -> Unit) {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val pkg = intent?.data?.schemeSpecificPart
                if (!pkg.isNullOrBlank()) {
                    synchronized(pendingPackages) { pendingPackages.add(pkg) }
                    iconLoader.invalidate(pkg)
                }
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(DEBOUNCE_MS)
                    val changed = synchronized(pendingPackages) {
                        val copy = pendingPackages.toSet()
                        pendingPackages.clear()
                        copy
                    }
                    onPackagesChanged(changed)
                }
            }
        }
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    fun stop() {
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
        debounceJob?.cancel()
        synchronized(pendingPackages) { pendingPackages.clear() }
    }

    private companion object {
        const val DEBOUNCE_MS = 600L
    }
}
