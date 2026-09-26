package com.meritscreen.core.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Unique identifiers for all application-wide theme presets.
 */
enum class MeritThemeId(
    val displayName: String,
    val description: String,
    val category: String,
) {
    CLASSIC(
        displayName = "Merit Classic",
        description = "Calm Horizon · Warm Sand & Deep Pine",
        category = "Original Brand",
    ),
    GOOGLE_SLATE(
        displayName = "Google Slate",
        description = "Workspace Clean · Pure White & Slate Navy",
        category = "Material Workspace",
    ),
    MONOCHROME(
        displayName = "Monochrome Noir",
        description = "Ultra Minimalist · Pitch Black & Clean White",
        category = "Linear / Notion",
    ),
    ELECTRIC_INDIGO(
        displayName = "Electric Indigo",
        description = "Modern Tech · Deep Indigo & Lavender Sky",
        category = "Stripe / Apple",
    ),
    SUNSET_VIOLET(
        displayName = "Sunset Violet",
        description = "Vibrant Horizon · Warm Coral & Velvet Purple",
        category = "Creative Warmth",
    ),
    CYBER_MIDNIGHT(
        displayName = "Cyber Midnight",
        description = "OLED Stealth · Pure Obsidian & Neon Cyan",
        category = "OLED Battery Saver",
    ),
    NORDIC_FROST(
        displayName = "Nordic Frost",
        description = "Scandi Minimal · Ice Blue & Deep Navy",
        category = "Nordic Clean",
    ),
    AMBER_HONEY(
        displayName = "Amber Honey",
        description = "Warm Elegance · Golden Honey & Deep Bronze",
        category = "Warm Luxe",
    ),
    SHADCN_ZINC(
        displayName = "shadcn/ui Zinc",
        description = "Modern Clean · Cool Zinc & Crisp High-Contrast",
        category = "Modern Web UI",
    ),
}

/**
 * Display modes for theme rendering.
 */
enum class ThemeMode(val displayName: String) {
    SYSTEM("Auto"),
    LIGHT("Light"),
    DARK("Dark"),
}

/**
 * Persisted theme preference configuration.
 */
data class ThemeConfig(
    val themeId: MeritThemeId = MeritThemeId.CLASSIC,
    val mode: ThemeMode = ThemeMode.SYSTEM,
)

/**
 * Comprehensive color token set for the application.
 * Matches all M3 semantic color roles and existing Merit design tokens.
 */
data class MeritColorTokens(
    val surface: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val outline: Color,
    val outlineVariant: Color,
    val surfaceTint: Color,

    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val primaryFixed: Color,
    val primaryFixedDim: Color,
    val onPrimaryFixed: Color,
    val onPrimaryFixedVariant: Color,

    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val secondaryFixed: Color,
    val secondaryFixedDim: Color,
    val onSecondaryFixed: Color,
    val onSecondaryFixedVariant: Color,

    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val tertiaryFixed: Color,
    val tertiaryFixedDim: Color,
    val onTertiaryFixed: Color,
    val onTertiaryFixedVariant: Color,

    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,

    val background: Color,
    val onBackground: Color,
    val surfaceVariant: Color,
)

/**
 * Theme-specific structural styling (corner radius, elevation, borders).
 */
data class MeritThemeStyle(
    val id: MeritThemeId,
    val cardCornerRadius: Dp = 16.dp,
    val buttonCornerRadius: Dp = 12.dp,
    val borderWidth: Dp = 1.dp,
    val shadowElevation: Dp = 2.dp,
    val shapes: Shapes = MeritShapes,
) {
    companion object {
        val Default = MeritThemeStyle(id = MeritThemeId.CLASSIC)
    }
}

/**
 * Display model used by the Theme Selector UI in Parent Settings.
 */
data class ThemeDisplayInfo(
    val id: MeritThemeId,
    val name: String,
    val description: String,
    val category: String,
    val previewPrimary: Color,
    val previewAccent: Color,
    val previewSurface: Color,
    val previewBackground: Color,
)
