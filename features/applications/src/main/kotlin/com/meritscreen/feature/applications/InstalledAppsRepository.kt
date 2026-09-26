package com.meritscreen.feature.applications

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.database.device.InstalledAppDao
import com.meritscreen.core.database.device.InstalledAppEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Efficient, cached representation of installed/launchable apps (Phase 6 §8).
 *
 * The full `PackageManager` query only runs from [refresh], which is called once at
 * startup and thereafter only when `PackageChangeMonitor` observes an install/uninstall
 * broadcast — never polled and never on the child Home render path (that path reads
 * [observeInstalledApps], which is Room-only).
 */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: InstalledAppDao,
    private val dispatchers: AppDispatchers,
) {

    fun observeInstalledApps(): Flow<List<InstalledAppInfo>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /** Room-only package set for Home tile install checks — never hits PackageManager. */
    fun observeInstalledPackageNames(): Flow<Set<String>> =
        dao.observeAll().map { entities -> entities.mapTo(linkedSetOf()) { it.packageName } }

    suspend fun cachedApps(): List<InstalledAppInfo> = dao.getAll().map { it.toDomain() }

    fun isInstalledCached(packageName: String, installedPackages: Set<String>): Boolean =
        packageName in installedPackages

    /**
     * PackageManager probe — reserved for one-off checks off the Home render path
     * (e.g. launch intent resolution). Prefer [observeInstalledPackageNames] / Room on hot paths.
     */
    fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Re-queries `PackageManager` and replaces the Room cache. Returns the fresh list. */
    suspend fun refresh(): List<InstalledAppInfo> = withContext(dispatchers.default) {
        val apps = queryLaunchableApps()
        dao.replaceAll(apps.map { it.toEntity() })
        apps
    }

    private fun queryLaunchableApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // Flag 0 (not MATCH_ALL): MATCH_ALL can return an empty set under API 30+ package
        // visibility even when the LAUNCHER <queries> declaration is present.
        val resolved = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }
        return resolved
            .asSequence()
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .mapNotNull { activityInfo ->
                try {
                    val appInfo = pm.getApplicationInfo(activityInfo.packageName, 0)
                    val label = pm.getApplicationLabel(appInfo).toString().ifBlank {
                        activityInfo.packageName
                    }
                    val versionCode = try {
                        val pkgInfo = pm.getPackageInfo(activityInfo.packageName, 0)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            pkgInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            pkgInfo.versionCode.toLong()
                        }
                    } catch (_: PackageManager.NameNotFoundException) {
                        0L
                    }
                    InstalledAppInfo(
                        packageName = activityInfo.packageName,
                        label = label,
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        versionCode = versionCode,
                    )
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun InstalledAppEntity.toDomain() = InstalledAppInfo(
        packageName = packageName,
        label = label,
        isSystemApp = isSystemApp,
        versionCode = versionCode,
    )

    private fun InstalledAppInfo.toEntity() = InstalledAppEntity(
        packageName = packageName,
        label = label,
        isSystemApp = isSystemApp,
        versionCode = versionCode,
        updatedAtEpochMs = System.currentTimeMillis(),
    )
}
