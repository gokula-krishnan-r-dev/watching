package com.meritscreen.core.common.device

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Detects whether this app currently holds the Android Home role, using only official,
 * documented platform APIs:
 * - API 29+ (Android 10+): [RoleManager.ROLE_HOME] is the supported source of truth.
 * - API 26-28: no RoleManager exists yet, so we fall back to resolving the standard
 *   `ACTION_MAIN`/`CATEGORY_HOME` intent and comparing the resolved package.
 *
 * This is intentionally framework-only (no hidden APIs, no root, no AccessibilityService)
 * and is shared by `:features:launcher` (user-facing setup UX) and `:features:devices`
 * (background heartbeat) so both report a consistent status without a feature-to-feature
 * module dependency. See ARCHITECTURE.md "Launcher & device management" for the full
 * platform-capability matrix and OEM caveats.
 */
object DefaultHomeChecker {

    fun isDefaultHome(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        return resolvesToThisApp(context)
    }

    /** Whether the OS supports asking the user to grant the Home role via system UI. */
    fun isRoleRequestSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
    }

    private fun resolvesToThisApp(context: Context): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }
}
