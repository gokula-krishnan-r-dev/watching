package com.meritscreen.feature.launcher

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LauncherSetupRoute

fun NavGraphBuilder.launcherGraph(navController: NavHostController) {
    composable<LauncherSetupRoute> {
        LauncherSetupScreen(onDone = { navController.popBackStack() })
    }
}
