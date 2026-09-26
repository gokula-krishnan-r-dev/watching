package com.meritscreen.feature.notifications

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.meritscreen.core.ui.components.FeaturePlaceholderScreen
import kotlinx.serialization.Serializable

@Serializable
data object NotificationsRoute

fun NavGraphBuilder.notificationsGraph() {
    composable<NotificationsRoute> {
        FeaturePlaceholderScreen(
            title = "Notifications",
            subtitle = "Parent alerts and FCM (Phase 7/8).",
        )
    }
}
