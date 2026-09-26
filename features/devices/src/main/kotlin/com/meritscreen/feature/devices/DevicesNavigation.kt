package com.meritscreen.feature.devices

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meritscreen.core.ui.components.FeaturePlaceholderScreen
import kotlinx.serialization.Serializable

@Serializable
data object DevicesRoute

fun NavGraphBuilder.devicesGraph() {
    composable<DevicesRoute> {
        FeaturePlaceholderScreen(
            title = "Devices",
            subtitle = "Pairing and device health (Phase 6).",
        )
    }
}
