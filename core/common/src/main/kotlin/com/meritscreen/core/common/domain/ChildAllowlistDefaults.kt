package com.meritscreen.core.common.domain

import com.meritscreen.core.common.config.AppConfig

/**
 * Production defaults for a child's app allowlist at creation / first inventory sync.
 *
 * Product rule: every installed app starts **allowed** with a
 * [AppConfig.DEFAULT_BLOCK_MINUTES] session block (15m). Parents may disable individual
 * apps afterward. Existing rules are never overwritten — only packages with no rule yet
 * are seeded, so a parent's deliberate block is sticky.
 */
object ChildAllowlistDefaults {

    /** Builds the default allow-rule for one installed package. */
    fun ruleForInstalledApp(app: InstalledAppSummary): AppRule {
        val label = app.label.ifBlank { app.packageName.substringAfterLast('.') }
        return AppRule(
            appId = app.packageName.replace('.', '_'),
            packageOrBundleId = app.packageName,
            displayName = label,
            allowed = true,
            blockMinutes = AppConfig.DEFAULT_BLOCK_MINUTES,
            grantOnPassMinutes = AppConfig.DEFAULT_BLOCK_MINUTES,
            cooldownMinutes = AppConfig.DEFAULT_COOLDOWN_MINUTES,
            isEmergency = isLikelyEmergencyPackage(app.packageName),
        )
    }

    /**
     * Rules to persist for installed packages that do not already have an app rule.
     * Idempotent: empty when every installed package already has a rule (allowed or not).
     */
    fun missingRulesToSeed(
        installed: List<InstalledAppSummary>,
        existingByPackage: Map<String, AppRule>,
    ): List<AppRule> {
        if (installed.isEmpty()) return emptyList()
        val existing = existingByPackage.keys.map { it.lowercase() }.toSet()
        return installed
            .distinctBy { it.packageName.lowercase() }
            .filter { it.packageName.lowercase() !in existing }
            .map(::ruleForInstalledApp)
    }

    /**
     * UI helper: when a rule is missing, treat the app as allowed with the default
     * block length so the parent list paints correctly before / while seeding writes.
     */
    fun effectiveAllowed(rule: AppRule?): Boolean = rule?.allowed ?: true

    fun effectiveBlockMinutes(rule: AppRule?): Int =
        rule?.blockMinutes?.takeIf { it > 0 } ?: AppConfig.DEFAULT_BLOCK_MINUTES

    /** Phone / dialer packages stay reachable during fail-lock (matches child runtime heuristics). */
    fun isLikelyEmergencyPackage(packageOrBundleId: String): Boolean {
        val lower = packageOrBundleId.lowercase()
        return lower.contains("dialer") ||
            lower.contains("telecom") ||
            (lower.contains("phone") && !lower.contains("photo") && !lower.contains("headphones"))
    }
}
