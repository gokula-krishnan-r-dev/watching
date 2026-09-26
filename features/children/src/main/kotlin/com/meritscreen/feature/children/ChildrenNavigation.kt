package com.meritscreen.feature.children

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meritscreen.core.ui.components.FeaturePlaceholderScreen
import kotlinx.serialization.Serializable

@Serializable
data object ChildrenRoute

fun NavGraphBuilder.childrenGraph() {
    composable<ChildrenRoute> {
        FeaturePlaceholderScreen(
            title = "Children",
            subtitle = "Child profile management (Phase 4).",
        )
    }
}
