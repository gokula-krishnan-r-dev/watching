package com.meritscreen.feature.child.data

import android.content.Context
import android.os.Build
import com.meritscreen.core.common.device.DefaultHomeChecker
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.logging.AppLogger
import com.meritscreen.core.firebase.device.DeviceRegistryClient
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.feature.applications.InstalledAppsRepository
import com.meritscreen.feature.applications.InstalledAppsSyncCoordinator
import com.meritscreen.feature.applications.PackageChangeMonitor
import com.meritscreen.feature.child.domain.UnpairChildDeviceUseCase
import com.meritscreen.feature.child.service.ChildTimeLimitService
import com.meritscreen.feature.devices.DeviceHeartbeatScheduler
import com.meritscreen.feature.devices.DeviceHeartbeatUseCase
import com.meritscreen.feature.devices.HeartbeatOutcome
import com.meritscreen.feature.devices.PushTokenRegistrationScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ties together Phase 6 device management for the child process: schedules the bounded
 * WorkManager heartbeat, watches this device's Firestore doc for parent revoke (kill switch),
 * refreshes installed-app inventory on package changes, and runs one lightweight one-shot
 * revocation check on foreground. Kept out of `ChildHomeViewModel` so Home stays Room-only.
 */
@Singleton
class ChildDeviceLifecycleCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pairingStore: ChildPairingStore,
    private val deviceRegistryClient: DeviceRegistryClient,
    private val heartbeatScheduler: DeviceHeartbeatScheduler,
    private val heartbeatUseCase: DeviceHeartbeatUseCase,
    private val packageChangeMonitor: PackageChangeMonitor,
    private val installedAppsRepository: InstalledAppsRepository,
    private val installedAppsSyncCoordinator: InstalledAppsSyncCoordinator,
    private val pushTokenRegistrationScheduler: PushTokenRegistrationScheduler,
    private val usageSyncScheduler: UsageSyncScheduler,
    private val unpairUseCase: UnpairChildDeviceUseCase,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var started = false
    private var inventoryJob: Job? = null
    private var revokeWatchJob: Job? = null

    private val _forceSignedOut = MutableStateFlow(false)
    val forceSignedOut: StateFlow<Boolean> = _forceSignedOut.asStateFlow()

    fun start() {
        // Clear any prior kill-switch signal so a fresh pair can stay on Child Home.
        _forceSignedOut.value = false
        ChildTimeLimitService.start(context)
        if (started) {
            // Process already warm (e.g. Home recreated after pairing) — still push inventory.
            syncInventoryWithRetry()
            ensureRevokeWatch()
            return
        }
        started = true
        heartbeatScheduler.ensureScheduled()
        usageSyncScheduler.ensureScheduled()
        pushTokenRegistrationScheduler.registerNow()
        packageChangeMonitor.start { changed ->
            logger.d("Package change", "packages" to changed.joinToString(",").ifBlank { "(unknown)" })
            refreshAndSyncApps()
        }
        scope.launch { checkNow() }
        ensureRevokeWatch()
        syncInventoryWithRetry()
    }

    /** Cheap, safe to call on every Home resume — not a network listener. */
    fun checkOnResume() {
        ChildTimeLimitService.start(context)
        scope.launch { checkNow() }
        syncInventoryWithRetry(maxAttempts = 2)
    }

    /** Explicit pull (Home "Refresh rules") — rescan PackageManager and upload if needed. */
    fun refreshInventoryNow() {
        syncInventoryWithRetry(maxAttempts = 3)
    }

    fun isDefaultHome(): Boolean = DefaultHomeChecker.isDefaultHome(context)

    /**
     * Called after local unpair (FCM / heartbeat / menu) so Compose can leave Child Home
     * immediately without waiting for DataStore role emission.
     */
    fun markForceSignedOut() {
        ChildTimeLimitService.stop(context)
        stopRevokeWatch()
        _forceSignedOut.value = true
    }

    fun stop() {
        ChildTimeLimitService.stop(context)
        stopRevokeWatch()
        inventoryJob?.cancel()
        inventoryJob = null
        started = false
    }

    private fun ensureRevokeWatch() {
        if (revokeWatchJob?.isActive == true) return
        revokeWatchJob = scope.launch {
            val credential = pairingStore.get() ?: return@launch
            deviceRegistryClient.observeStatus(
                familyId = credential.familyId,
                childId = credential.childId,
                deviceId = credential.deviceId,
            )
                .map { it.revoked && it.registered }
                .distinctUntilChanged()
                .filter { revoked -> revoked }
                .collect {
                    logger.i("Device revoked via Firestore listener; unpairing")
                    runCatching { unpairUseCase() }
                    markForceSignedOut()
                }
        }
    }

    private fun stopRevokeWatch() {
        revokeWatchJob?.cancel()
        revokeWatchJob = null
    }

    private fun syncInventoryWithRetry(maxAttempts: Int = MAX_INVENTORY_ATTEMPTS) {
        if (inventoryJob?.isActive == true) return
        inventoryJob = scope.launch {
            var attempt = 0
            var delayMs = INITIAL_RETRY_MS
            while (attempt < maxAttempts) {
                attempt++
                val uploaded = runCatching { refreshAndSyncApps() }
                    .onFailure { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        logger.w("InstalledApps refresh failed", error, "attempt" to attempt)
                    }
                    .getOrDefault(false)
                if (uploaded) return@launch
                if (attempt < maxAttempts) {
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_MS)
                }
            }
        }
    }

    private suspend fun checkNow() {
        val isDefaultHome = DefaultHomeChecker.isDefaultHome(context)
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val battery = readBatteryPercent()
        val osVersion = "Android ${Build.VERSION.RELEASE}"
        if (heartbeatUseCase(isDefaultHome, model, battery, osVersion) is HeartbeatOutcome.Revoked) {
            unpairUseCase()
            markForceSignedOut()
        }
    }

    private fun readBatteryPercent(): Int? {
        val manager = context.getSystemService(android.os.BatteryManager::class.java) ?: return null
        return manager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
    }

    /** @return true when remote inventory is current after this attempt. */
    private suspend fun refreshAndSyncApps(): Boolean {
        val apps = installedAppsRepository.refresh()
        logger.d("InstalledApps scanned", "count" to apps.size)
        return installedAppsSyncCoordinator.syncIfNeeded(apps)
    }

    private companion object {
        const val MAX_INVENTORY_ATTEMPTS = 5
        const val INITIAL_RETRY_MS = 1_500L
        const val MAX_RETRY_MS = 12_000L
    }
}
