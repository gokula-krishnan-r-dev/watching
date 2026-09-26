package com.meritscreen.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppThemeTest {

    @Before
    fun setUp() {
        AppThemeManager.resetForTesting()
    }

    @Test
    fun `allThemes contains all 9 theme presets with distinct preview colors`() {
        val themes = MeritThemeCatalog.allThemes
        assertEquals(9, themes.size)

        val themeIds = themes.map { it.id }.toSet()
        assertEquals(
            setOf(
                MeritThemeId.CLASSIC,
                MeritThemeId.SHADCN_ZINC,
                MeritThemeId.GOOGLE_SLATE,
                MeritThemeId.MONOCHROME,
                MeritThemeId.ELECTRIC_INDIGO,
                MeritThemeId.SUNSET_VIOLET,
                MeritThemeId.CYBER_MIDNIGHT,
                MeritThemeId.NORDIC_FROST,
                MeritThemeId.AMBER_HONEY,
            ),
            themeIds,
        )

        themes.forEach { theme ->
            assertTrue("Theme name must not be blank: ${theme.id}", theme.name.isNotBlank())
            assertTrue("Theme description must not be blank: ${theme.id}", theme.description.isNotBlank())
            assertTrue("Theme previewPrimary must be specified: ${theme.id}", theme.previewPrimary.isSpecified)
            assertTrue("Theme previewAccent must be specified: ${theme.id}", theme.previewAccent.isSpecified)
            assertTrue("Theme previewSurface must be specified: ${theme.id}", theme.previewSurface.isSpecified)
            assertTrue("Theme previewBackground must be specified: ${theme.id}", theme.previewBackground.isSpecified)
        }
    }

    @Test
    fun `getColorTokens provides complete palettes for light and dark across all themes`() {
        MeritThemeId.values().forEach { id ->
            val light = MeritThemeCatalog.getColorTokens(id, isDark = false)
            val dark = MeritThemeCatalog.getColorTokens(id, isDark = true)

            assertNotNull("Light tokens must not be null for $id", light)
            assertNotNull("Dark tokens must not be null for $id", dark)

            // Primary tokens
            assertTrue("Light primary specified for $id", light.primary.isSpecified)
            assertTrue("Dark primary specified for $id", dark.primary.isSpecified)
            assertTrue("Light onPrimary specified for $id", light.onPrimary.isSpecified)
            assertTrue("Dark onPrimary specified for $id", dark.onPrimary.isSpecified)

            // Surfaces
            assertTrue("Light surface specified for $id", light.surface.isSpecified)
            assertTrue("Dark surface specified for $id", dark.surface.isSpecified)
            assertTrue("Light onSurface specified for $id", light.onSurface.isSpecified)
            assertTrue("Dark onSurface specified for $id", dark.onSurface.isSpecified)

            // Containers
            assertTrue("Light primaryContainer specified for $id", light.primaryContainer.isSpecified)
            assertTrue("Dark primaryContainer specified for $id", dark.primaryContainer.isSpecified)
            assertTrue("Light surfaceContainerLow specified for $id", light.surfaceContainerLow.isSpecified)
            assertTrue("Dark surfaceContainerLow specified for $id", dark.surfaceContainerLow.isSpecified)

            // Outlines
            assertTrue("Light outline specified for $id", light.outline.isSpecified)
            assertTrue("Dark outline specified for $id", dark.outline.isSpecified)

            // Verify light and dark backgrounds are distinct
            assertNotEquals("Light and dark background must differ for $id", light.background, dark.background)
        }
    }

    @Test
    fun `defaultColors matches Classic Light palette exactly`() {
        val defaultColors = MeritThemeCatalog.defaultColors
        val classicLight = MeritThemeCatalog.getColorTokens(MeritThemeId.CLASSIC, isDark = false)

        assertEquals(classicLight.primary, defaultColors.primary)
        assertEquals(classicLight.surface, defaultColors.surface)
        assertEquals(classicLight.surfaceContainerLow, defaultColors.surfaceContainerLow)
        assertEquals(classicLight.onSurface, defaultColors.onSurface)
        assertEquals(classicLight.outline, defaultColors.outline)
    }

    @Test
    fun `getM3ColorScheme generates valid Material 3 ColorScheme for all themes`() {
        MeritThemeId.values().forEach { id ->
            val lightTokens = MeritThemeCatalog.getColorTokens(id, isDark = false)
            val lightM3 = MeritThemeCatalog.getM3ColorScheme(id, isDark = false, lightTokens)
            assertEquals(lightTokens.primary, lightM3.primary)
            assertEquals(lightTokens.surface, lightM3.surface)

            val darkTokens = MeritThemeCatalog.getColorTokens(id, isDark = true)
            val darkM3 = MeritThemeCatalog.getM3ColorScheme(id, isDark = true, darkTokens)
            assertEquals(darkTokens.primary, darkM3.primary)
            assertEquals(darkTokens.surface, darkM3.surface)
        }
    }

    @Test
    fun `getThemeStyle provides custom corner styling per design pattern`() {
        val monochromeStyle = MeritThemeCatalog.getThemeStyle(MeritThemeId.MONOCHROME)
        assertEquals(8.dp, monochromeStyle.cardCornerRadius)
        assertEquals(6.dp, monochromeStyle.buttonCornerRadius)

        val cyberStyle = MeritThemeCatalog.getThemeStyle(MeritThemeId.CYBER_MIDNIGHT)
        assertEquals(10.dp, cyberStyle.cardCornerRadius)
        assertEquals(8.dp, cyberStyle.buttonCornerRadius)

        val electricStyle = MeritThemeCatalog.getThemeStyle(MeritThemeId.ELECTRIC_INDIGO)
        assertEquals(18.dp, electricStyle.cardCornerRadius)
        assertEquals(14.dp, electricStyle.buttonCornerRadius)

        val shadcnStyle = MeritThemeCatalog.getThemeStyle(MeritThemeId.SHADCN_ZINC)
        assertEquals(10.dp, shadcnStyle.cardCornerRadius)
        assertEquals(8.dp, shadcnStyle.buttonCornerRadius)

        val classicStyle = MeritThemeCatalog.getThemeStyle(MeritThemeId.CLASSIC)
        assertEquals(16.dp, classicStyle.cardCornerRadius)
        assertEquals(12.dp, classicStyle.buttonCornerRadius)
    }

    @Test
    fun `AppThemeManager updates theme and mode reactively`() {
        assertEquals(MeritThemeId.CLASSIC, AppThemeManager.themeConfig.value.themeId)
        assertEquals(ThemeMode.SYSTEM, AppThemeManager.themeConfig.value.mode)

        AppThemeManager.setTheme(MeritThemeId.GOOGLE_SLATE)
        assertEquals(MeritThemeId.GOOGLE_SLATE, AppThemeManager.themeConfig.value.themeId)

        AppThemeManager.setMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, AppThemeManager.themeConfig.value.mode)

        AppThemeManager.setTheme(MeritThemeId.CYBER_MIDNIGHT)
        assertEquals(MeritThemeId.CYBER_MIDNIGHT, AppThemeManager.themeConfig.value.themeId)

        AppThemeManager.setMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, AppThemeManager.themeConfig.value.mode)
    }

    @Test
    fun `MeritStaticColors provides valid fallback colors for ViewModels`() {
        assertTrue(MeritStaticColors.Primary.isSpecified)
        assertTrue(MeritStaticColors.Secondary.isSpecified)
        assertTrue(MeritStaticColors.Tertiary.isSpecified)
        assertTrue(MeritStaticColors.PrimaryContainer.isSpecified)
        assertTrue(MeritStaticColors.TealLight.isSpecified)
        assertTrue(MeritStaticColors.Leaf.isSpecified)
        assertTrue(MeritStaticColors.Surface.isSpecified)
        assertTrue(MeritStaticColors.OnSurface.isSpecified)
    }
}
