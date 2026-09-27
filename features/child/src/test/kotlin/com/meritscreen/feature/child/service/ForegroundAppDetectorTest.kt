package com.meritscreen.feature.child.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAppDetectorTest {

    @Test
    fun `home and Android control surfaces are excluded from timed app sessions`() {
        val homePackages = setOf("com.example.launcher", "com.google.android.apps.nexuslauncher")
        assertTrue(ForegroundAppDetector.isHomeOrSystemUiPackage("com.example.launcher", homePackages))
        assertTrue(ForegroundAppDetector.isHomeOrSystemUiPackage("com.google.android.apps.nexuslauncher", homePackages))
        assertTrue(ForegroundAppDetector.isHomeOrSystemUiPackage("com.android.systemui", homePackages))
        assertTrue(ForegroundAppDetector.isHomeOrSystemUiPackage("com.google.android.permissioncontroller", homePackages))
        assertFalse(ForegroundAppDetector.isHomeOrSystemUiPackage("com.google.android.googlequicksearchbox", homePackages))
    }
}
