package com.meritscreen.feature.applications

import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.core.common.logging.AppLogger
import com.meritscreen.core.database.device.DeviceRuntimeStateDao
import com.meritscreen.core.database.device.DeviceRuntimeStateEntity
import com.meritscreen.core.firebase.device.DeviceRegistryClient
import com.meritscreen.core.network.NetworkMonitor
import com.meritscreen.core.security.pairing.ChildPairingStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads installed-app inventory (package + label + optional WEBP thumbnail) to this
 * device's Firestore record when the set actually changed (hash-gated) and online.
 */
@Singleton
class InstalledAppsSyncCoordinator @Inject constructor(
    private val repository: InstalledAppsRepository,
    private val deviceRegistryClient: DeviceRegistryClient,
    private val pairingStore: ChildPairingStore,
    private val networkMonitor: NetworkMonitor,
    private val runtimeStateDao: DeviceRuntimeStateDao,
    private val iconLoader: AppIconLoader,
    private val iconEncoder: AppIconThumbnailEncoder,
    private val logger: AppLogger,
) {
    suspend fun syncIfNeeded(): Boolean = syncIfNeeded(repository.refresh())

    suspend fun syncIfNeeded(apps: List<InstalledAppInfo>): Boolean {
        val credential = pairingStore.get()
        if (credential == null) {
            logger.d("InstalledApps sync skipped: not paired")
            return false
        }
        if (!networkMonitor.isCurrentlyOnline()) {
            logger.d("InstalledApps sync skipped: offline", "count" to apps.size)
            return false
        }

        val newHash = apps.stableInventoryHash()
        val state = runtimeStateDao.get()
        if (state?.lastUploadedAppsHash == newHash) {
            return true
        }

        return try {
            val summaries = buildSummariesWithIcons(apps)
            deviceRegistryClient.uploadInstalledApps(
                familyId = credential.familyId,
                childId = credential.childId,
                deviceId = credential.deviceId,
                apps = summaries,
            )
            runtimeStateDao.upsert(
                (state ?: DeviceRuntimeStateEntity()).copy(lastUploadedAppsHash = newHash),
            )
            logger.i(
                "InstalledApps uploaded",
                "count" to summaries.size,
                "withIcons" to summaries.count { !it.iconBase64.isNullOrBlank() },
            )
            true
        } catch (error: Throwable) {
            logger.w("InstalledApps upload failed", error, "count" to apps.size)
            false
        }
    }

    private suspend fun buildSummariesWithIcons(apps: List<InstalledAppInfo>): List<InstalledAppSummary> {
        val capped = apps.take(AppConfig.INSTALLED_APPS_MAX_COUNT)
        // Prefer user apps for icon budget; system tools last.
        val prioritized = capped.sortedBy { if (it.isSystemApp) 1 else 0 }
        var budget = AppConfig.APP_ICON_SYNC_BUDGET_BYTES
        val encoded = LinkedHashMap<String, AppIconThumbnailEncoder.EncodedIcon>(prioritized.size)

        for (app in prioritized) {
            if (budget <= 0) break
            val bitmap = iconLoader.loadForSync(app.packageName) ?: continue
            val thumb = runCatching { iconEncoder.encode(bitmap) }.getOrNull() ?: continue
            if (thumb.byteSize > budget) continue
            encoded[app.packageName] = thumb
            budget -= thumb.byteSize
        }

        return capped.map { app ->
            val icon = encoded[app.packageName]
            InstalledAppSummary(
                packageName = app.packageName,
                label = app.label.take(AppConfig.INSTALLED_APP_LABEL_MAX_CHARS),
                iconBase64 = icon?.base64,
                iconHash = icon?.hash,
            )
        }
    }
}
