package com.meritscreen.feature.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.meritscreen.core.common.device.DefaultHomeChecker

/**
 * Requests the Android Home role using only official APIs (Phase 6 §"Core Objective").
 *
 * - API 29+: [RoleManager.createRequestRoleIntent] — **must** be started with
 *   `startActivityForResult` / Activity Result API (platform contract). Plain
 *   `Context.startActivity` is ignored or no-ops on many API 29+ devices/emulators.
 * - Fallback: [Settings.ACTION_HOME_SETTINGS] opens the system “Home app” picker.
 * - API 26-28: fire `ACTION_MAIN`/`CATEGORY_HOME` via a chooser when multiple Homes exist.
 *
 * Neither path uses Device Owner, Accessibility, or any private API.
 */
object HomeRoleManager {

    fun isDefaultHome(context: Context): Boolean = DefaultHomeChecker.isDefaultHome(context)

    fun isRoleRequestSupported(context: Context): Boolean =
        DefaultHomeChecker.isRoleRequestSupported(context)

    /**
     * Best intent to start via Activity Result (API 29+) or `startActivity` (legacy).
     * Never returns an unresolved intent.
     */
    fun createSetupIntent(context: Context): Intent {
        createRoleRequestIntent(context)?.let { roleIntent ->
            if (canResolve(context, roleIntent)) return roleIntent
        }
        homeSettingsIntent()?.let { settings ->
            if (canResolve(context, settings)) return settings
        }
        return legacyHomeChooserIntent(context)
    }

    /** Intent to launch for a system-mediated Home-role request; null if unsupported (API<29). */
    fun createRoleRequestIntent(context: Context): Intent? {
        if (!isRoleRequestSupported(context)) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

    fun homeSettingsIntent(): Intent? =
        Intent(Settings.ACTION_HOME_SETTINGS).takeIf {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        }

    /**
     * API 26-28 (and ultimate fallback): Home intent wrapped in a chooser so a preferred
     * launcher cannot swallow the tap without showing alternatives.
     */
    fun legacyHomeChooserIntent(@Suppress("UNUSED_PARAMETER") context: Context): Intent {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return Intent.createChooser(home, "Choose Home app").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun canResolve(context: Context, intent: Intent): Boolean {
        val pm = context.packageManager
        return pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    }
}
