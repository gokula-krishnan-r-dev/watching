package com.meritscreen.core.common.domain

import kotlinx.serialization.Serializable

/**
 * One entry in a child device's installed-app inventory, uploaded so a parent can pick
 * allowed apps by name. Optionally includes a tiny WEBP thumbnail (`iconBase64`) so the
 * parent UI can show the real launcher icon without PackageManager on the parent phone.
 *
 * Icons are optional, size-capped, and never required for allowlisting to work.
 * Usage data and permissions lists are never synced.
 */
@Serializable
data class InstalledAppSummary(
    val packageName: String,
    val label: String,
    /** Base64 (NO_WRAP) WEBP bytes; null when encoding failed or budget exhausted. */
    val iconBase64: String? = null,
    /** Short content hash of [iconBase64] for Coil cache keys / change detection. */
    val iconHash: String? = null,
)

/** Remote device status used for the offline-safe revocation ("kill switch") check. */
@Serializable
data class DeviceRemoteStatus(
    val revoked: Boolean,
    val launcherDefault: Boolean? = null,
    /**
     * False when the Firestore device doc is not visible yet (pairing race / offline cache).
     * Missing must **not** be treated as revoked — only an explicit `revoked: true` is.
     */
    val registered: Boolean = true,
)
