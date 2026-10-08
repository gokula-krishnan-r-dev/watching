package com.meritscreen.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.feature.onboarding.ui.AiLearningContextScreen
import com.meritscreen.feature.onboarding.ui.OnboardingAllowlistScreen
import com.meritscreen.feature.onboarding.ui.OnboardingDeviceHandshakeScreen
import com.meritscreen.feature.onboarding.ui.TimelineScheduleScreen
import com.meritscreen.feature.parent.ui.AccountRoute
import com.meritscreen.feature.parent.ui.AccountScreen
import com.meritscreen.feature.parent.ui.AddChildAccountScreen
import com.meritscreen.feature.parent.ui.AddChildAiRoute
import com.meritscreen.feature.parent.ui.AddChildAllowlistRoute
import com.meritscreen.feature.parent.ui.AddChildPairingRoute
import com.meritscreen.feature.parent.ui.AddChildRoute
import com.meritscreen.feature.parent.ui.AddChildTimelineRoute
import com.meritscreen.feature.parent.ui.AllowlistRoute
import com.meritscreen.feature.parent.ui.AllowlistScreen
import com.meritscreen.feature.parent.ui.ChildDetailRoute
import com.meritscreen.feature.parent.ui.ChildDetailScreen
import com.meritscreen.feature.parent.ui.ChildrenAnalyticsScreen
import com.meritscreen.feature.parent.ui.ChildrenRoute
import com.meritscreen.feature.parent.ui.DashboardScreen
import com.meritscreen.feature.parent.ui.DeleteFamilyRoute
import com.meritscreen.feature.parent.ui.DeleteFamilyScreen
import com.meritscreen.feature.parent.ui.DevicesRoute
import com.meritscreen.feature.parent.ui.DevicesScreen
import com.meritscreen.feature.parent.ui.NotificationsRoute
import com.meritscreen.feature.parent.ui.NotificationsSettingsScreen
import com.meritscreen.feature.parent.ui.ParentAddChildPolicyViewModel
import com.meritscreen.feature.parent.ui.ParentDashboardRoute
import com.meritscreen.feature.parent.ui.ParentRoute
import com.meritscreen.feature.parent.ui.QuizSettingsRoute
import com.meritscreen.feature.parent.ui.QuizSettingsScreen
import com.meritscreen.feature.parent.ui.ReportsRoute
import com.meritscreen.feature.parent.ui.ReportsScreen
import com.meritscreen.feature.parent.ui.ResetPinRoute
import com.meritscreen.feature.parent.ui.ResetPinScreen
import com.meritscreen.feature.parent.ui.RewardsRoute
import com.meritscreen.feature.parent.ui.RewardsScreen
import com.meritscreen.feature.parent.ui.TimeLimitsRoute
import com.meritscreen.feature.parent.ui.TimeLimitsScreen

fun NavGraphBuilder.parentGraph(
    navController: NavHostController,
    onSignOut: () -> Unit,
    onPairDevice: (childId: String) -> Unit,
) {
    navigation<ParentRoute>(startDestination = ParentDashboardRoute) {
        composable<ParentDashboardRoute> {
            DashboardScreen(
                onOpenChild = { navController.navigate(ChildDetailRoute(it)) },
                onOpenAccount = { navController.navigate(AccountRoute) },
                onOpenNotifications = { navController.navigate(NotificationsRoute) },
                onAddChild = { navController.navigate(AddChildRoute) },
                onOpenChildren = { navController.navigate(ChildrenRoute) },
                onOpenReports = { childId ->
                    if (childId != null) {
                        navController.navigate(ReportsRoute(childId))
                    }
                },
            )
        }
        composable<ChildrenRoute> {
            ChildrenAnalyticsScreen(
                onOpenChild = { childId -> navController.navigate(ChildDetailRoute(childId)) },
                onAddChild = { navController.navigate(AddChildRoute) },
                onOpenReports = { childId ->
                    navController.navigate(ReportsRoute(childId ?: ""))
                },
                onOpenAccount = { navController.navigate(AccountRoute) },
                onOpenHome = {
                    navController.navigate(ParentDashboardRoute) {
                        popUpTo(ParentDashboardRoute) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<ChildDetailRoute> {
            val route = it.toRoute<ChildDetailRoute>()
            ChildDetailScreen(
                onBack = { navController.popBackStack() },
                onAllowlist = { navController.navigate(AllowlistRoute(route.childId)) },
                onTimeLimits = { navController.navigate(TimeLimitsRoute(route.childId)) },
                onQuizSettings = { navController.navigate(QuizSettingsRoute(route.childId)) },
                onRewards = { navController.navigate(RewardsRoute(route.childId)) },
                onReports = { navController.navigate(ReportsRoute(route.childId)) },
                onPairDevice = { onPairDevice(route.childId) },
                onDevices = { navController.navigate(DevicesRoute(route.childId)) },
                onDeleted = {
                    navController.popBackStack(ParentDashboardRoute, inclusive = false)
                },
            )
        }
        composable<DevicesRoute> {
            val route = it.toRoute<DevicesRoute>()
            DevicesScreen(
                onBack = { navController.popBackStack() },
                onPairDevice = { onPairDevice(route.childId) },
            )
        }
        composable<AllowlistRoute> {
            AllowlistScreen(onBack = { navController.popBackStack() })
        }
        composable<TimeLimitsRoute> {
            TimeLimitsScreen(onBack = { navController.popBackStack() })
        }
        composable<QuizSettingsRoute> {
            QuizSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<RewardsRoute> {
            RewardsScreen(onBack = { navController.popBackStack() })
        }
        composable<ReportsRoute> {
            ReportsScreen(
                onBack = { navController.popBackStack() },
                onAdjustQuizFocus = { childId ->
                    navController.navigate(QuizSettingsRoute(childId))
                },
            )
        }
        composable<NotificationsRoute> {
            NotificationsSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<AccountRoute> {
            AccountScreen(
                onBack = { navController.popBackStack() },
                onAddChild = { navController.navigate(AddChildRoute) },
                onResetPin = { navController.navigate(ResetPinRoute) },
                onDeleteFamily = { navController.navigate(DeleteFamilyRoute) },
                onSignOut = onSignOut,
            )
        }
        // Profile → Timeline → Pair device → Allowlist → AI (mirrors onboarding; PIN already set)
        composable<AddChildRoute> {
            AddChildAccountScreen(
                onBack = { navController.popBackStack() },
                onCreated = { childId, childName, ageBand, avatar ->
                    navController.navigate(
                        AddChildTimelineRoute(
                            childId = childId,
                            childName = childName,
                            ageBand = ageBand.name,
                            avatar = avatar.name,
                        ),
                    ) {
                        popUpTo(AddChildRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<AddChildTimelineRoute> {
            val route = it.toRoute<AddChildTimelineRoute>()
            ParentAddChildTimelineRoute(
                childId = route.childId,
                childName = route.childName,
                ageBandRaw = route.ageBand,
                avatarRaw = route.avatar,
                onBack = { navController.popBackStack() },
                onContinue = { childId, childName, ageBand, avatar ->
                    navController.navigate(
                        AddChildPairingRoute(
                            childId = childId,
                            childName = childName,
                            ageBand = ageBand,
                            avatar = avatar,
                        ),
                    )
                },
            )
        }
        composable<AddChildPairingRoute> {
            val route = it.toRoute<AddChildPairingRoute>()
            OnboardingDeviceHandshakeScreen(
                onPaired = {
                    navController.navigate(
                        AddChildAllowlistRoute(
                            childId = route.childId,
                            childName = route.childName,
                            ageBand = route.ageBand,
                            avatar = route.avatar,
                        ),
                    ) {
                        popUpTo<AddChildPairingRoute> { inclusive = true }
                    }
                },
                onSkipToDashboard = {
                    // Skip pairing mid add-child → still configure allowlist defaults, then AI.
                    navController.navigate(
                        AddChildAllowlistRoute(
                            childId = route.childId,
                            childName = route.childName,
                            ageBand = route.ageBand,
                            avatar = route.avatar,
                        ),
                    ) {
                        popUpTo<AddChildPairingRoute> { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<AddChildAllowlistRoute> {
            val route = it.toRoute<AddChildAllowlistRoute>()
            OnboardingAllowlistScreen(
                onContinue = {
                    navController.navigate(
                        AddChildAiRoute(
                            childId = route.childId,
                            childName = route.childName,
                            ageBand = route.ageBand,
                            avatar = route.avatar,
                        ),
                    )
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<AddChildAiRoute> {
            val route = it.toRoute<AddChildAiRoute>()
            ParentAddChildAiRoute(
                childId = route.childId,
                childName = route.childName,
                ageBandRaw = route.ageBand,
                avatarRaw = route.avatar,
                onBack = { navController.popBackStack() },
                onFinished = { childId ->
                    navController.navigate(ChildDetailRoute(childId)) {
                        popUpTo(ParentDashboardRoute)
                    }
                },
            )
        }
        composable<ResetPinRoute> {
            ResetPinScreen(onBack = { navController.popBackStack() })
        }
        composable<DeleteFamilyRoute> {
            DeleteFamilyScreen(
                onBack = { navController.popBackStack() },
                onDeleted = onSignOut,
            )
        }
    }
}

@Composable
private fun ParentAddChildTimelineRoute(
    childId: String,
    childName: String,
    ageBandRaw: String,
    avatarRaw: String,
    onBack: () -> Unit,
    onContinue: (childId: String, childName: String, ageBand: String, avatar: String) -> Unit,
    viewModel: ParentAddChildPolicyViewModel = hiltViewModel(),
) {
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val ageBand = ParentAddChildPolicyViewModel.parseAgeBand(ageBandRaw)
    val avatar = AvatarPreset.entries.firstOrNull { it.name.equals(avatarRaw, ignoreCase = true) }
        ?: AvatarPreset.Default

    Box(modifier = Modifier.fillMaxSize()) {
        TimelineScheduleScreen(
            childName = childName,
            avatar = avatar,
            stepLabel = "Step 2 of 5 • Timeline",
            onBack = onBack,
            onSaveAndContinue = { budget, quizFreq, _, _, _, cooldown, _ ->
                if (saving) return@TimelineScheduleScreen
                viewModel.saveTimeline(
                    childId = childId,
                    ageBand = ageBand,
                    budgetMinutes = budget,
                    quizFreqMinutes = quizFreq,
                    cooldownMinutes = cooldown,
                    onSuccess = { onContinue(childId, childName, ageBandRaw, avatarRaw) },
                )
            },
        )
        if (!error.isNullOrBlank()) {
            Text(
                text = error.orEmpty(),
                color = MeritColors.Error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (saving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MeritColors.Primary)
            }
        }
    }
}

@Composable
private fun ParentAddChildAiRoute(
    childId: String,
    childName: String,
    ageBandRaw: String,
    avatarRaw: String,
    onBack: () -> Unit,
    onFinished: (childId: String) -> Unit,
    viewModel: ParentAddChildPolicyViewModel = hiltViewModel(),
) {
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val ageBand = ParentAddChildPolicyViewModel.parseAgeBand(ageBandRaw)
    val avatar = AvatarPreset.entries.firstOrNull { it.name.equals(avatarRaw, ignoreCase = true) }
        ?: AvatarPreset.Default

    Box(modifier = Modifier.fillMaxSize()) {
        AiLearningContextScreen(
            childName = childName,
            grade = ParentAddChildPolicyViewModel.gradeLabelFor(ageBand),
            ageBand = ageBand,
            avatarEmoji = avatar.emoji,
            stepLabel = "Step 5 of 5 • AI",
            onBack = onBack,
            onSaveAndContinue = { prompt ->
                if (saving) return@AiLearningContextScreen
                viewModel.saveAiPrompt(
                    childId = childId,
                    ageBand = ageBand,
                    rawPrompt = prompt,
                    onSuccess = { onFinished(childId) },
                )
            },
        )
        if (!error.isNullOrBlank()) {
            Text(
                text = error.orEmpty(),
                color = MeritColors.Error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (saving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MeritColors.Primary)
            }
        }
    }
}
