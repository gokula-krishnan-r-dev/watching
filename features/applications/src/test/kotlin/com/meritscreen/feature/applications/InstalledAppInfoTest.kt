package com.meritscreen.feature.applications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InstalledAppInfoTest {

    private fun app(pkg: String, label: String) =
        InstalledAppInfo(packageName = pkg, label = label, isSystemApp = false, versionCode = 1L)

    @Test
    fun `hash is stable regardless of list order`() {
        val a = listOf(app("com.a", "A"), app("com.b", "B"))
        val b = listOf(app("com.b", "B"), app("com.a", "A"))
        assertEquals(a.stableInventoryHash(), b.stableInventoryHash())
    }

    @Test
    fun `hash changes when an app is added`() {
        val before = listOf(app("com.a", "A"))
        val after = listOf(app("com.a", "A"), app("com.b", "B"))
        assertNotEquals(before.stableInventoryHash(), after.stableInventoryHash())
    }

    @Test
    fun `hash changes when a label changes but package does not`() {
        val before = listOf(app("com.a", "Old Name"))
        val after = listOf(app("com.a", "New Name"))
        assertNotEquals(before.stableInventoryHash(), after.stableInventoryHash())
    }

    @Test
    fun `toSummary only carries package and label`() {
        val summary = app("com.a", "A").toSummary()
        assertEquals("com.a", summary.packageName)
        assertEquals("A", summary.label)
    }
}
