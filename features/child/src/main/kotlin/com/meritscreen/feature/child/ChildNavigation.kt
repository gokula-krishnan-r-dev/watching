package com.meritscreen.feature.child

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.meritscreen.feature.child.ui.ChildHomeRoute
import com.meritscreen.feature.child.ui.ChildHomeScreen
import com.meritscreen.feature.child.ui.ChildNotAllowedRoute
import com.meritscreen.feature.child.ui.ChildNotAllowedScreen
import com.meritscreen.feature.child.ui.ChildParentMenuRoute
import com.meritscreen.feature.child.ui.ChildParentMenuScreen
import com.meritscreen.feature.child.ui.ChildPinRoute
import com.meritscreen.feature.child.ui.ChildPinScreen
import com.meritscreen.feature.child.ui.ChildQuizRoute
import com.meritscreen.feature.child.ui.ChildQuizScreen
import com.meritscreen.feature.child.ui.ChildRoute
import com.meritscreen.feature.child.ui.ChildStickerBookRoute
import com.meritscreen.feature.child.ui.ChildStickerBookScreen
import com.meritscreen.feature.launcher.LauncherSetupRoute

fun NavGraphBuilder.childGraph(
    navController: NavHostController,
    onResetRole: () -> Unit,
) {
    fun openQuizOnce() {
        navController.navigate(ChildQuizRoute) { launchSingleTop = true }
    }

    navigation<ChildRoute>(startDestination = ChildHomeRoute) {
        composable<ChildHomeRoute> {
            ChildHomeScreen(
                onOpenQuiz = ::openQuizOnce,
                onOpenStickerBook = { navController.navigate(ChildStickerBookRoute) },
                onOpenPin = { endFailLock ->
                    navController.navigate(ChildPinRoute(endFailLockOnUnlock = endFailLock))
                },
                onNotAllowed = { navController.navigate(ChildNotAllowedRoute) },
                onOpenLauncherSetup = { navController.navigate(LauncherSetupRoute) },
                onResetRole = onResetRole,
            )
        }
        composable<ChildQuizRoute> {
            ChildQuizScreen(
                onFinished = {
                    navController.popBackStack(ChildHomeRoute, inclusive = false)
                },
                onOpenPin = {
                    navController.navigate(ChildPinRoute(endFailLockOnUnlock = true))
                },
            )
        }
        composable<ChildStickerBookRoute> {
            ChildStickerBookScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<ChildPinRoute> { entry ->
            val route = entry.toRoute<ChildPinRoute>()
            ChildPinScreen(
                endFailLockOnUnlock = route.endFailLockOnUnlock,
                onUnlockedToMenu = {
                    navController.navigate(ChildParentMenuRoute) {
                        popUpTo<ChildPinRoute> { inclusive = true }
                    }
                },
                onUnlockedEndFailLock = {
                    // Leave quiz + PIN and land on Home with fail lock already cleared.
                    navController.popBackStack(ChildHomeRoute, inclusive = false)
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ChildParentMenuRoute> {
            ChildParentMenuScreen(
                onBack = { navController.popBackStack() },
                onUnpaired = onResetRole,
                onFailLockEnded = {
                    navController.popBackStack(ChildHomeRoute, inclusive = false)
                },
            )
        }
        composable<ChildNotAllowedRoute> {
            ChildNotAllowedScreen(onBack = { navController.popBackStack() })
        }
    }
}
