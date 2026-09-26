package com.meritscreen.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meritscreen.core.ui.components.FeaturePlaceholderScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavGraphBuilder.settingsGraph() {
    composable<SettingsRoute> {
        FeaturePlaceholderScreen(
            title = "Settings",
            subtitle = "Account and policy settings (Phase 4).",
        )
    }
}
