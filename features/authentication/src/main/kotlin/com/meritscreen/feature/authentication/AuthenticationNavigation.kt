package com.meritscreen.feature.authentication

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.meritscreen.feature.authentication.domain.ParentAuthResult
import com.meritscreen.feature.authentication.google.GoogleIdTokenClient
import com.meritscreen.feature.authentication.ui.AuthenticationRoute
import com.meritscreen.feature.authentication.ui.ChildPairingRoute
import com.meritscreen.feature.authentication.ui.ChildPairingScreen
import com.meritscreen.feature.authentication.ui.OtpVerificationRoute
import com.meritscreen.feature.authentication.ui.OtpVerificationScreenRoute
import com.meritscreen.feature.authentication.ui.ParentPairingRoute
import com.meritscreen.feature.authentication.ui.ParentPairingScreen
import com.meritscreen.feature.authentication.ui.SignInRoute


fun NavGraphBuilder.authenticationGraph(
    navController: NavHostController,
    googleIdTokenClient: GoogleIdTokenClient,
    onParentReady: () -> Unit,
    onNeedsOnboarding: () -> Unit,
    onChildPaired: () -> Unit,
    onPairingFinished: (() -> Unit)? = null,
) {
    val afterAuth: (ParentAuthResult) -> Unit = { result ->
        val childId = result.pairingChildId
        if (result.isNewFamily && !childId.isNullOrBlank()) {
            navController.navigate(ParentPairingRoute(childId))
        } else {
            onParentReady()
        }
    }
    val pairingDone = onPairingFinished ?: onParentReady
    composable<AuthenticationRoute> {
        SignInRoute(
            onAuthenticated = afterAuth,
            onNeedsOnboarding = onNeedsOnboarding,
            onOtpRequested = { email ->
                navController.navigate(OtpVerificationRoute(email = email))
            },
            onBack = if (navController.previousBackStackEntry != null) {
                { navController.popBackStack() }
            } else {
                null
            },
            googleIdTokenClient = googleIdTokenClient,
        )
    }
    composable<OtpVerificationRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<OtpVerificationRoute>()
        OtpVerificationScreenRoute(
            email = route.email,
            onAuthenticated = afterAuth,
            onNeedsOnboarding = onNeedsOnboarding,
            onBack = { navController.popBackStack() },
        )
    }

    composable<ParentPairingRoute> {
        ParentPairingScreen(
            onPaired = pairingDone,
            onSkip = pairingDone,
            onBack = if (navController.previousBackStackEntry != null) {
                { navController.popBackStack() }
            } else {
                null
            },
        )
    }
    composable<ChildPairingRoute> {
        ChildPairingScreen(
            onPaired = onChildPaired,
            onBack = if (navController.previousBackStackEntry != null) {
                { navController.popBackStack() }
            } else {
                null
            },
        )
    }
}
