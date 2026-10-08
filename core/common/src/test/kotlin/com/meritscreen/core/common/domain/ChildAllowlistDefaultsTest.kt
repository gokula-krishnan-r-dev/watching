package com.meritscreen.core.common.domain

import com.meritscreen.core.common.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildAllowlistDefaultsTest {

    @Test
    fun ruleForInstalledApp_allowsWithDefaultFifteenMinuteBlock() {
        val rule = ChildAllowlistDefaults.ruleForInstalledApp(
            InstalledAppSummary("com.android.chrome", "Chrome"),
        )
        assertTrue(rule.allowed)
        assertEquals(AppConfig.DEFAULT_BLOCK_MINUTES, rule.blockMinutes)
        assertEquals(AppConfig.DEFAULT_BLOCK_MINUTES, rule.grantOnPassMinutes)
        assertEquals(AppConfig.DEFAULT_COOLDOWN_MINUTES, rule.cooldownMinutes)
        assertEquals("com.android.chrome", rule.packageOrBundleId)
        assertFalse(rule.isEmergency)
    }

    @Test
    fun missingRulesToSeed_onlyPackagesWithoutExistingRule() {
        val installed = listOf(
            InstalledAppSummary("com.android.chrome", "Chrome"),
            InstalledAppSummary("com.google.android.calendar", "Calendar"),
            InstalledAppSummary("com.android.camera2", "Camera"),
        )
        val existing = mapOf(
            "com.android.chrome" to AppRule(
                appId = "chrome",
                packageOrBundleId = "com.android.chrome",
                displayName = "Chrome",
                allowed = false,
                blockMinutes = 30,
            ),
        )

        val missing = ChildAllowlistDefaults.missingRulesToSeed(installed, existing)

        assertEquals(2, missing.size)
        assertTrue(missing.all { it.allowed })
        assertTrue(missing.all { it.blockMinutes == AppConfig.DEFAULT_BLOCK_MINUTES })
        assertEquals(
            setOf("com.google.android.calendar", "com.android.camera2"),
            missing.map { it.packageOrBundleId }.toSet(),
        )
        // Existing blocked Chrome must not be re-seeded / overwritten.
        assertFalse(missing.any { it.packageOrBundleId.equals("com.android.chrome", true) })
    }

    @Test
    fun missingRulesToSeed_emptyWhenInventoryEmptyOrFullyCovered() {
        assertTrue(
            ChildAllowlistDefaults.missingRulesToSeed(emptyList(), emptyMap()).isEmpty(),
        )
        val app = InstalledAppSummary("com.android.chrome", "Chrome")
        val covered = mapOf(
            "com.android.chrome" to ChildAllowlistDefaults.ruleForInstalledApp(app),
        )
        assertTrue(ChildAllowlistDefaults.missingRulesToSeed(listOf(app), covered).isEmpty())
    }

    @Test
    fun effectiveDefaults_whenRuleMissing() {
        assertTrue(ChildAllowlistDefaults.effectiveAllowed(null))
        assertEquals(AppConfig.DEFAULT_BLOCK_MINUTES, ChildAllowlistDefaults.effectiveBlockMinutes(null))
        assertFalse(
            ChildAllowlistDefaults.effectiveAllowed(
                AppRule("x", "com.x", allowed = false),
            ),
        )
    }

    @Test
    fun allowlistPresets_startWithDefaultBlock() {
        assertEquals(AppConfig.DEFAULT_BLOCK_MINUTES, AppConfig.ALLOWLIST_BLOCK_PRESET_MINUTES.first())
        assertTrue(AppConfig.ALLOWLIST_BLOCK_PRESET_MINUTES.contains(15))
    }
}
