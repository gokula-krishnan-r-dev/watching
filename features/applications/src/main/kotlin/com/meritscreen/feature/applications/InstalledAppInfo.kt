package com.meritscreen.feature.applications

import com.meritscreen.core.common.domain.InstalledAppSummary

/**
 * A launchable app on this device, as discovered via `PackageManager`. This is the
 * in-process representation cached in Room (`installed_app`); [toSummary] is the trimmed
 * shape uploaded to Firestore (icons are attached separately during sync).
 */
data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val versionCode: Long,
) {
    fun toSummary(): InstalledAppSummary = InstalledAppSummary(packageName = packageName, label = label)
}

/** Stable hash independent of list order — includes version so icon/label updates re-upload. */
fun List<InstalledAppInfo>.stableInventoryHash(): Int =
    this.map { Triple(it.packageName, it.label, it.versionCode) }
        .sortedBy { it.first }
        .hashCode()
