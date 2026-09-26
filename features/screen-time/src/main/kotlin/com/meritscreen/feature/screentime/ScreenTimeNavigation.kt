package com.meritscreen.feature.screentime

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meritscreen.core.ui.components.FeaturePlaceholderScreen
import kotlinx.serialization.Serializable

@Serializable
data object ScreenTimeRoute

fun NavGraphBuilder.screentimeGraph() {
    composable<ScreenTimeRoute> {
        FeaturePlaceholderScreen(
            title = "Screen time",
            subtitle = "App blocks, daily ceiling, fail lock (Phase 5/7).",
        )
    }
}
