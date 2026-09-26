package com.meritscreen.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Root theme composable for MeritScreen.
 *
 * Automatically connects to [AppThemeManager], applies the active [MeritThemeId] and [ThemeMode],
 * exposes dynamic tokens through [LocalMeritColors], sets Material 3 [MaterialTheme.colorScheme],
 * and synchronizes OS status and navigation bar icons.
 */
@Composable
fun MeritScreenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    SideEffect {
        AppThemeManager.init(context)
    }

    val themeConfig by AppThemeManager.themeConfig.collectAsState()
    val isDark = when (themeConfig.mode) {
        ThemeMode.SYSTEM -> darkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorTokens = MeritThemeCatalog.getColorTokens(themeConfig.themeId, isDark)
    val colorScheme = MeritThemeCatalog.getM3ColorScheme(themeConfig.themeId, isDark, colorTokens)
    val themeStyle = MeritThemeCatalog.getThemeStyle(themeConfig.themeId)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalMeritColors provides colorTokens,
        LocalMeritThemeStyle provides themeStyle,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MeritTypography,
            shapes = themeStyle.shapes,
            content = content,
        )
    }
}
