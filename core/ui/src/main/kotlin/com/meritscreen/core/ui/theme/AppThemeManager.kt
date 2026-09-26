package com.meritscreen.core.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton state manager for the active application theme and display mode.
 * Persists user selections to [SharedPreferences] and exposes a reactive [StateFlow].
 */
object AppThemeManager {

    private const val PREFS_NAME = "merit_theme_prefs"
    private const val KEY_THEME_ID = "selected_theme_id"
    private const val KEY_THEME_MODE = "selected_theme_mode"

    private val _themeConfig = MutableStateFlow(ThemeConfig())
    val themeConfig: StateFlow<ThemeConfig> = _themeConfig.asStateFlow()

    @Volatile
    private var prefs: SharedPreferences? = null

    /**
     * Initialize theme state from local storage. Safe to call multiple times.
     */
    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs = p
                    val savedThemeId = p.getString(KEY_THEME_ID, MeritThemeId.CLASSIC.name) ?: MeritThemeId.CLASSIC.name
                    val savedMode = p.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
                    val themeId = runCatching { MeritThemeId.valueOf(savedThemeId) }.getOrDefault(MeritThemeId.CLASSIC)
                    val mode = runCatching { ThemeMode.valueOf(savedMode) }.getOrDefault(ThemeMode.SYSTEM)
                    _themeConfig.value = ThemeConfig(themeId = themeId, mode = mode)
                }
            }
        }
    }

    /**
     * Switch active theme pattern. Immediately triggers UI recomposition and persists to disk.
     */
    fun setTheme(themeId: MeritThemeId) {
        _themeConfig.update { it.copy(themeId = themeId) }
        prefs?.edit()?.putString(KEY_THEME_ID, themeId.name)?.apply()
    }

    /**
     * Switch display mode (Auto / Light / Dark).
     */
    fun setMode(mode: ThemeMode) {
        _themeConfig.update { it.copy(mode = mode) }
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    /**
     * Reset to defaults (Merit Classic, System Auto). Useful for tests.
     */
    fun resetForTesting() {
        _themeConfig.value = ThemeConfig()
        prefs?.edit()?.clear()?.apply()
    }
}
