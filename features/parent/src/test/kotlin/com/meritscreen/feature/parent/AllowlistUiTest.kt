package com.meritscreen.feature.parent

import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.feature.parent.ui.AllowlistUi
import org.junit.Assert.assertEquals
import org.junit.Test

class AllowlistUiTest {

    @Test
    fun `pickableApps excludes packages already covered by a rule`() {
        val ui = AllowlistUi(
            rules = listOf(AppRule(appId = "a", packageOrBundleId = "com.a")),
            installedApps = listOf(
                InstalledAppSummary("com.a", "A"),
                InstalledAppSummary("com.b", "B"),
            ),
        )
        assertEquals(listOf(InstalledAppSummary("com.b", "B")), ui.pickableApps)
    }

    @Test
    fun `pickableApps is case-insensitive when matching existing rules`() {
        val ui = AllowlistUi(
            rules = listOf(AppRule(appId = "a", packageOrBundleId = "Com.A")),
            installedApps = listOf(InstalledAppSummary("com.a", "A")),
        )
        assertEquals(emptyList<InstalledAppSummary>(), ui.pickableApps)
    }

    @Test
    fun `pickableApps returns everything when no rules exist yet`() {
        val apps = listOf(InstalledAppSummary("com.a", "A"), InstalledAppSummary("com.b", "B"))
        val ui = AllowlistUi(rules = emptyList(), installedApps = apps)
        assertEquals(apps, ui.pickableApps)
    }
}
