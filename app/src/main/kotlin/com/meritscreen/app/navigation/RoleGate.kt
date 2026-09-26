package com.meritscreen.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.meritscreen.core.common.session.DeviceRole
import com.meritscreen.feature.applications.applicationsGraph
import com.meritscreen.feature.authentication.authenticationGraph
import com.meritscreen.feature.authentication.google.GoogleIdTokenClient
import com.meritscreen.feature.authentication.ui.AuthenticationRoute
import com.meritscreen.feature.authentication.ui.ChildPairingRoute
import com.meritscreen.feature.authentication.ui.ParentPairingRoute
import com.meritscreen.feature.child.childGraph
import com.meritscreen.feature.child.ui.ChildRoute
import com.meritscreen.feature.children.childrenGraph
import com.meritscreen.feature.devices.devicesGraph
import com.meritscreen.feature.launcher.launcherGraph
import com.meritscreen.feature.notifications.notificationsGraph
import com.meritscreen.feature.onboarding.onboardingGraph
import com.meritscreen.feature.onboarding.ui.RoleSelectRoute
import com.meritscreen.feature.onboarding.ui.ValueTourRoute
import com.meritscreen.feature.parent.parentGraph
import com.meritscreen.feature.parent.ui.ParentRoute
import com.meritscreen.feature.screentime.screentimeGraph
import com.meritscreen.feature.settings.settingsGraph

@Composable
fun RoleGate(
    role: DeviceRole,
    googleIdTokenClient: GoogleIdTokenClient,
    onSelectRole: (DeviceRole) -> Unit,
    onSignOutParent: () -> Unit,
    onResetRole: () -> Unit,
) {
    key(role) {
        val navController = rememberNavController()
        val startDestination: Any = when (role) {
            DeviceRole.Unassigned -> RoleSelectRoute
            DeviceRole.Parent -> ParentRoute
            DeviceRole.Child -> ChildRoute
        }
        NavHost(navController = navController, startDestination = startDestination) {
            onboardingGraph(
                navController = navController,
                onChildPathChosen = { navController.navigate(ChildPairingRoute) },
                // Draft is ready — Sign-in resume commits the family, then opens dashboard / pairing.
                onParentOnboardingComplete = {
                    navController.navigate(AuthenticationRoute) {
                        popUpTo(RoleSelectRoute) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onParentSignIn = { navController.navigate(AuthenticationRoute) },
                onSkipPairingToDashboard = { onSelectRole(DeviceRole.Parent) },
            )
            authenticationGraph(
                navController = navController,
                googleIdTokenClient = googleIdTokenClient,
                // Existing family → Parent role remounts NavHost on the dashboard.
                onParentReady = { onSelectRole(DeviceRole.Parent) },
                // First-time / no family yet → Value Tour → Add Child…
                onNeedsOnboarding = {
                    navController.navigate(ValueTourRoute) {
                        popUpTo(RoleSelectRoute) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onChildPaired = { onSelectRole(DeviceRole.Child) },
                onPairingFinished = {
                    if (role == DeviceRole.Parent) {
                        navController.popBackStack()
                    } else {
                        onSelectRole(DeviceRole.Parent)
                    }
                },
            )
            parentGraph(
                navController = navController,
                onSignOut = onSignOutParent,
                onPairDevice = { childId ->
                    navController.navigate(ParentPairingRoute(childId))
                },
            )
            childGraph(navController = navController, onResetRole = onResetRole)
            childrenGraph()
            launcherGraph(navController)
            applicationsGraph()
            screentimeGraph()
            settingsGraph()
            devicesGraph()
            notificationsGraph()
        }
    }
}
