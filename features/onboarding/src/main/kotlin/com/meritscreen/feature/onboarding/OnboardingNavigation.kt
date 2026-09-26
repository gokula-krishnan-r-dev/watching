package com.meritscreen.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.feature.onboarding.ui.AddChildRoute
import com.meritscreen.feature.onboarding.ui.AddChildScreen
import com.meritscreen.feature.onboarding.ui.AiLearningContextRoute
import com.meritscreen.feature.onboarding.ui.AiLearningContextScreen
import com.meritscreen.feature.onboarding.ui.CreateFamilyRoute
import com.meritscreen.feature.onboarding.ui.CreateFamilyScreen
import com.meritscreen.feature.onboarding.ui.OnboardingAllowlistRoute
import com.meritscreen.feature.onboarding.ui.OnboardingAllowlistScreen
import com.meritscreen.feature.onboarding.ui.OnboardingChildContextViewModel
import com.meritscreen.feature.onboarding.ui.OnboardingDeviceHandshakeRoute
import com.meritscreen.feature.onboarding.ui.OnboardingDeviceHandshakeScreen
import com.meritscreen.feature.onboarding.ui.RoleSelectRoute
import com.meritscreen.feature.onboarding.ui.RoleSelectScreen
import com.meritscreen.feature.onboarding.ui.SetParentPinRoute
import com.meritscreen.feature.onboarding.ui.SetParentPinScreen
import com.meritscreen.feature.onboarding.ui.SetupCompleteRoute
import com.meritscreen.feature.onboarding.ui.SetupCompleteScreen
import com.meritscreen.feature.onboarding.ui.TimelineScheduleRoute
import com.meritscreen.feature.onboarding.ui.TimelineScheduleScreen
import com.meritscreen.feature.onboarding.ui.ValueTourRoute
import com.meritscreen.feature.onboarding.ui.ValueTourScreen

/**
 * Parent first-run after OTP / Google:
 * Role Select → Auth → Value Tour → Add Child → Timeline & Schedule →
 * Device Handshake → App Allowlist → Set Parent PIN →
 * AI Learning Context → Setup Complete.
 *
 * Post-signup Add Child (parent dashboard) reuses the same screens without PIN:
 * Profile → Timeline → Device Handshake → App Allowlist → AI → Child Detail.
 *
 * Child name / grade / avatar are passed as navigation args into Timeline, AI, and
 * Setup Complete so those screens never show a static preview name.
 */
fun NavGraphBuilder.onboardingGraph(
    navController: NavHostController,
    onChildPathChosen: () -> Unit,
    onParentOnboardingComplete: () -> Unit,
    onParentSignIn: () -> Unit,
    onSkipPairingToDashboard: () -> Unit = onParentOnboardingComplete,
) {
    composable<RoleSelectRoute> {
        RoleSelectScreen(
            onParentChosen = onParentSignIn,
            onChildChosen = onChildPathChosen,
        )
    }
    composable<ValueTourRoute> {
        ValueTourScreen(
            onContinue = { navController.navigate(AddChildRoute) },
            onSkip = { navController.navigate(AddChildRoute) },
            onBack = { navController.popBackStack() },
        )
    }
    composable<CreateFamilyRoute> {
        CreateFamilyScreen(
            onContinue = { navController.navigate(AddChildRoute) },
            onBack = { navController.popBackStack() },
        )
    }
    composable<AddChildRoute> {
        AddChildScreen(
            onContinue = { childName, ageBand, avatar ->
                navController.navigate(
                    TimelineScheduleRoute(
                        childName = childName,
                        ageBand = ageBand.name,
                        avatar = avatar.name,
                    ),
                )
            },
            onBack = { navController.popBackStack() },
        )
    }
    composable<TimelineScheduleRoute> { entry ->
        val route = entry.toRoute<TimelineScheduleRoute>()
        TimelineScheduleScreen(
            childName = route.childName,
            avatar = parseAvatar(route.avatar),
            onContinue = {
                navController.navigate(OnboardingDeviceHandshakeRoute())
            },
            onBack = { navController.popBackStack() },
        )
    }
    composable<OnboardingDeviceHandshakeRoute> {
        OnboardingDeviceHandshakeScreen(
            onPaired = { navController.navigate(OnboardingAllowlistRoute()) },
            onSkipToDashboard = onSkipPairingToDashboard,
            onBack = { navController.popBackStack() },
        )
    }
    composable<OnboardingAllowlistRoute> {
        OnboardingAllowlistScreen(
            onContinue = { navController.navigate(SetParentPinRoute) },
            onBack = { navController.popBackStack() },
        )
    }
    composable<SetParentPinRoute> {
        SetParentPinToAiBridge(
            onContinue = { childName, grade, avatar ->
                navController.navigate(
                    AiLearningContextRoute(
                        childName = childName,
                        grade = grade,
                        avatar = avatar,
                    ),
                )
            },
            onBack = { navController.popBackStack() },
        )
    }
    composable<AiLearningContextRoute> { entry ->
        val route = entry.toRoute<AiLearningContextRoute>()
        val avatar = parseAvatar(route.avatar)
        AiLearningContextScreen(
            childName = route.childName,
            grade = route.grade,
            avatarEmoji = avatar.emoji,
            onContinue = {
                navController.navigate(
                    SetupCompleteRoute(
                        childName = route.childName,
                        grade = route.grade,
                        avatar = route.avatar,
                    ),
                )
            },
            onBack = { navController.popBackStack() },
        )
    }
    composable<SetupCompleteRoute> { entry ->
        val route = entry.toRoute<SetupCompleteRoute>()
        SetupCompleteScreen(
            childName = route.childName,
            grade = route.grade,
            avatar = parseAvatar(route.avatar),
            onOpenDashboard = onParentOnboardingComplete,
        )
    }
}

@Composable
private fun SetParentPinToAiBridge(
    onContinue: (childName: String, grade: String, avatar: String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: OnboardingChildContextViewModel = hiltViewModel()
    val child = viewModel.childContext.collectAsState().value
    SetParentPinScreen(
        onContinue = {
            onContinue(child.name, child.gradeLabel, child.avatar.name)
        },
        onBack = onBack,
    )
}

private fun parseAvatar(raw: String): AvatarPreset =
    AvatarPreset.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?: AvatarPreset.Default
