package com.meritscreen.feature.applications

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meritscreen.core.ui.components.FeaturePlaceholderScreen
import kotlinx.serialization.Serializable

@Serializable
data object ApplicationsRoute

fun NavGraphBuilder.applicationsGraph() {
    composable<ApplicationsRoute> {
        FeaturePlaceholderScreen(
            title = "Applications",
            subtitle = "Allowed-app management (Phase 4/6).",
        )
    }
}
