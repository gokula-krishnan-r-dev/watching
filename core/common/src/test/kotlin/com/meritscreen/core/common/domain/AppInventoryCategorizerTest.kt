package com.meritscreen.core.common.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInventoryCategorizerTest {

    @Test
    fun classifiesEducationalApps() {
        val result = AppInventoryCategorizer.classify("org.khankids.android", "Khan Academy Kids")
        assertEquals(AppInventoryCategorizer.Category.EDUCATIONAL, result.category)
        assertTrue(result.verifiedSafe)
    }

    @Test
    fun classifiesEntertainmentApps() {
        val result = AppInventoryCategorizer.classify("com.roblox.client", "Roblox")
        assertEquals(AppInventoryCategorizer.Category.ENTERTAINMENT, result.category)
        assertFalse(result.verifiedSafe)
    }

    @Test
    fun classifiesSystemTools() {
        val result = AppInventoryCategorizer.classify("com.android.dialer", "Phone")
        assertEquals(AppInventoryCategorizer.Category.SYSTEM, result.category)
        assertTrue(result.verifiedSafe)
    }
}
