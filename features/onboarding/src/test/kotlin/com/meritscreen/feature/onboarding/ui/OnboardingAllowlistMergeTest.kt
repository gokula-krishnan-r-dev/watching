package com.meritscreen.feature.onboarding.ui

import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.InstalledAppSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingAllowlistMergeTest {

    @Test
    fun mergesInstalledAppsWithRules() {
        val installed = listOf(
            InstalledAppSummary("org.khankids.android", "Khan Academy Kids"),
            InstalledAppSummary("com.roblox.client", "Roblox"),
        )
        val rules = mapOf(
            "org.khankids.android" to AppRule(
                appId = "khan",
                packageOrBundleId = "org.khankids.android",
                displayName = "Khan Academy Kids",
                allowed = true,
            ),
        )

        val rows = OnboardingAllowlistViewModel.mergeRows(installed, rules)

        assertEquals(2, rows.size)
        val khan = rows.first { it.packageName == "org.khankids.android" }
        val roblox = rows.first { it.packageName == "com.roblox.client" }
        assertTrue(khan.isAllowed)
        assertEquals(AppCategoryFilter.EDUCATIONAL, khan.category)
        assertFalse(roblox.isAllowed)
        assertEquals(AppCategoryFilter.ENTERTAINMENT, roblox.category)
    }

    @Test
    fun sortsByLabel() {
        val installed = listOf(
            InstalledAppSummary("com.b", "Zebra"),
            InstalledAppSummary("com.a", "Alpha"),
        )
        val rows = OnboardingAllowlistViewModel.mergeRows(installed, emptyMap())
        assertEquals(listOf("Alpha", "Zebra"), rows.map { it.label })
    }
}
