package com.meritscreen.core.database.device

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached, deduped inventory of launchable apps on this device (Phase 6 application
 * management). Populated from `PackageManager` and only re-queried when a
 * `PACKAGE_ADDED`/`REMOVED`/`REPLACED` broadcast is observed — never polled.
 */
@Entity(tableName = "installed_app")
data class InstalledAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val versionCode: Long,
    val updatedAtEpochMs: Long,
)

/**
 * Single-row runtime state for this device's registration with the family backend.
 * Lets the heartbeat worker detect changes (installed-app set, Home-role status) without
 * re-uploading identical data, and lets the app apply a safe offline fallback when the
 * last-known server state is stale (see ARCHITECTURE.md "Offline & local enforcement").
 */
@Entity(tableName = "device_runtime_state")
data class DeviceRuntimeStateEntity(
    @PrimaryKey val id: Int = 1,
    val lastHeartbeatAtEpochMs: Long? = null,
    val lastKnownRevoked: Boolean = false,
    val lastKnownLauncherDefault: Boolean? = null,
    val lastUploadedAppsHash: Int? = null,
    val configVersion: Long = 0,
)
