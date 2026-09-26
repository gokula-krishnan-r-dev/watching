package com.meritscreen.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * CompositionLocal providing active semantic color tokens for the current theme and display mode.
 */
val LocalMeritColors = staticCompositionLocalOf { MeritThemeCatalog.defaultColors }

/**
 * CompositionLocal providing active structural styles (corner radius, elevation, borders).
 */
val LocalMeritThemeStyle = staticCompositionLocalOf { MeritThemeStyle.Default }

/**
 * Dynamic design tokens for MeritScreen.
 *
 * All properties are `@Composable get()` accessors delegating to [LocalMeritColors].
 * Any Compose component reading [MeritColors] will automatically recompose whenever
 * the theme or dark mode is toggled, without needing code refactoring across the app.
 */
object MeritColors {
    // Surfaces & Containers
    val Surface: Color @Composable get() = LocalMeritColors.current.surface
    val SurfaceDim: Color @Composable get() = LocalMeritColors.current.surfaceDim
    val SurfaceBright: Color @Composable get() = LocalMeritColors.current.surfaceBright
    val SurfaceContainerLowest: Color @Composable get() = LocalMeritColors.current.surfaceContainerLowest
    val SurfaceContainerLow: Color @Composable get() = LocalMeritColors.current.surfaceContainerLow
    val SurfaceContainer: Color @Composable get() = LocalMeritColors.current.surfaceContainer
    val SurfaceContainerHigh: Color @Composable get() = LocalMeritColors.current.surfaceContainerHigh
    val SurfaceContainerHighest: Color @Composable get() = LocalMeritColors.current.surfaceContainerHighest
    val OnSurface: Color @Composable get() = LocalMeritColors.current.onSurface
    val OnSurfaceVariant: Color @Composable get() = LocalMeritColors.current.onSurfaceVariant
    val InverseSurface: Color @Composable get() = LocalMeritColors.current.inverseSurface
    val InverseOnSurface: Color @Composable get() = LocalMeritColors.current.inverseOnSurface
    val Outline: Color @Composable get() = LocalMeritColors.current.outline
    val OutlineVariant: Color @Composable get() = LocalMeritColors.current.outlineVariant
    val SurfaceTint: Color @Composable get() = LocalMeritColors.current.surfaceTint

    // Primary Accents
    val Primary: Color @Composable get() = LocalMeritColors.current.primary
    val OnPrimary: Color @Composable get() = LocalMeritColors.current.onPrimary
    val PrimaryContainer: Color @Composable get() = LocalMeritColors.current.primaryContainer
    val OnPrimaryContainer: Color @Composable get() = LocalMeritColors.current.onPrimaryContainer
    val InversePrimary: Color @Composable get() = LocalMeritColors.current.inversePrimary
    val PrimaryFixed: Color @Composable get() = LocalMeritColors.current.primaryFixed
    val PrimaryFixedDim: Color @Composable get() = LocalMeritColors.current.primaryFixedDim
    val OnPrimaryFixed: Color @Composable get() = LocalMeritColors.current.onPrimaryFixed
    val OnPrimaryFixedVariant: Color @Composable get() = LocalMeritColors.current.onPrimaryFixedVariant

    // Secondary Accents
    val Secondary: Color @Composable get() = LocalMeritColors.current.secondary
    val OnSecondary: Color @Composable get() = LocalMeritColors.current.onSecondary
    val SecondaryContainer: Color @Composable get() = LocalMeritColors.current.secondaryContainer
    val OnSecondaryContainer: Color @Composable get() = LocalMeritColors.current.onSecondaryContainer
    val SecondaryFixed: Color @Composable get() = LocalMeritColors.current.secondaryFixed
    val SecondaryFixedDim: Color @Composable get() = LocalMeritColors.current.secondaryFixedDim
    val OnSecondaryFixed: Color @Composable get() = LocalMeritColors.current.onSecondaryFixed
    val OnSecondaryFixedVariant: Color @Composable get() = LocalMeritColors.current.onSecondaryFixedVariant

    // Tertiary Accents
    val Tertiary: Color @Composable get() = LocalMeritColors.current.tertiary
    val OnTertiary: Color @Composable get() = LocalMeritColors.current.onTertiary
    val TertiaryContainer: Color @Composable get() = LocalMeritColors.current.tertiaryContainer
    val OnTertiaryContainer: Color @Composable get() = LocalMeritColors.current.onTertiaryContainer
    val TertiaryFixed: Color @Composable get() = LocalMeritColors.current.tertiaryFixed
    val TertiaryFixedDim: Color @Composable get() = LocalMeritColors.current.tertiaryFixedDim
    val OnTertiaryFixed: Color @Composable get() = LocalMeritColors.current.onTertiaryFixed
    val OnTertiaryFixedVariant: Color @Composable get() = LocalMeritColors.current.onTertiaryFixedVariant

    // Error & Feedback
    val Error: Color @Composable get() = LocalMeritColors.current.error
    val OnError: Color @Composable get() = LocalMeritColors.current.onError
    val ErrorContainer: Color @Composable get() = LocalMeritColors.current.errorContainer
    val OnErrorContainer: Color @Composable get() = LocalMeritColors.current.onErrorContainer

    // Background & Variants
    val Background: Color @Composable get() = LocalMeritColors.current.background
    val OnBackground: Color @Composable get() = LocalMeritColors.current.onBackground
    val SurfaceVariant: Color @Composable get() = LocalMeritColors.current.surfaceVariant

    // Legacy and domain aliases
    val TealDark: Color @Composable get() = LocalMeritColors.current.primaryContainer
    val Teal: Color @Composable get() = LocalMeritColors.current.primary
    val TealLight: Color @Composable get() = LocalMeritColors.current.primaryFixed
    val Sand: Color @Composable get() = LocalMeritColors.current.secondaryFixed
    val SandMuted: Color @Composable get() = LocalMeritColors.current.secondaryFixedDim
    val Ink: Color @Composable get() = LocalMeritColors.current.onSurface
    val InkMuted: Color @Composable get() = LocalMeritColors.current.onSurfaceVariant
    val Paper: Color @Composable get() = LocalMeritColors.current.surfaceContainerLow
    val PaperRaised: Color @Composable get() = LocalMeritColors.current.surfaceContainerLowest
    val Coral: Color @Composable get() = LocalMeritColors.current.error
    val CoralContainer: Color @Composable get() = LocalMeritColors.current.errorContainer
    val Leaf: Color @Composable get() = LocalMeritColors.current.tertiary

    // Dark theme palette aliases
    val Night: Color @Composable get() = LocalMeritColors.current.background
    val NightRaised: Color @Composable get() = LocalMeritColors.current.surface
    val NightOutline: Color @Composable get() = LocalMeritColors.current.outline
    val Moon: Color @Composable get() = LocalMeritColors.current.onBackground
    val MoonMuted: Color @Composable get() = LocalMeritColors.current.onSurfaceVariant
    val TealGlow: Color @Composable get() = LocalMeritColors.current.primary
    val CoralNight: Color @Composable get() = LocalMeritColors.current.error
    val CoralNightContainer: Color @Composable get() = LocalMeritColors.current.errorContainer
}

/**
 * Static baseline colors for non-composable environments (e.g. ViewModels, static models).
 */
object MeritStaticColors {
    val Primary = Color(0xFF00514D)
    val Secondary = Color(0xFF685D4B)
    val Tertiary = Color(0xFF13522F)
    val PrimaryContainer = Color(0xFF0F6B66)
    val PrimaryFixed = Color(0xFFA2F1EA)
    val PrimaryFixedDim = Color(0xFF87D4CE)
    val SecondaryFixed = Color(0xFFF1E0CA)
    val SecondaryFixedDim = Color(0xFFD4C4AF)
    val TealLight = Color(0xFF4AA39C)
    val Leaf = Color(0xFF2F6B45)
    val Surface = Color(0xFFFCF9F4)
    val OnSurface = Color(0xFF1C1C19)
    val Background = Color(0xFFFCF9F4)
}
