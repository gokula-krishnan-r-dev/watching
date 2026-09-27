package com.meritscreen.feature.child.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.graphics.Bitmap
import android.provider.MediaStore
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HotelClass
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.domain.SessionPhase
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.components.MeritPullToRefreshBox
import com.meritscreen.core.ui.components.MeritSecondaryButton
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import com.meritscreen.feature.applications.AppIconLoader
import com.meritscreen.feature.launcher.LockTaskGuard
import com.meritscreen.feature.child.service.ForegroundAppDetector
import kotlinx.coroutines.launch

@Composable
fun ChildHomeScreen(
    onOpenQuiz: () -> Unit,
    onOpenStickerBook: () -> Unit,
    onOpenPin: (endFailLockOnUnlock: Boolean) -> Unit,
    onNotAllowed: () -> Unit,
    onOpenLauncherSetup: () -> Unit,
    onResetRole: () -> Unit,
    viewModel: ChildHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launchError by viewModel.launchError.collectAsStateWithLifecycle()
    val forceSignedOut by viewModel.forceSignedOut.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isCallingEmergency by viewModel.isCallingEmergency.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var homeIsResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    homeIsResumed = true
                    viewModel.onResumed()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    homeIsResumed = false
                    viewModel.onPaused()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(forceSignedOut) {
        if (forceSignedOut) onResetRole()
    }

    when (val current = state) {
        UiState.Loading -> LoadingState(message = "Loading your playground apps")
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(
            message = current.error.userMessage,
            onRetry = viewModel::refreshRules,
        )
        is UiState.Success -> {
            val data = current.data
            LaunchedEffect(data.session.phase, homeIsResumed) {
                // With overlay access the foreground service launches the interruption
                // activity. Do not push a second quiz destination from a Home composition
                // that remains alive underneath that activity. Without access, Home is the
                // intentional fallback when it is actually visible to the child.
                if (data.session.phase == SessionPhase.QuizDue && homeIsResumed &&
                    !Settings.canDrawOverlays(context)
                ) {
                    onOpenQuiz()
                }
            }

            if (data.policy.paused) {
                FailLockPane(
                    secondsLeft = 0,
                    title = "Device paused by a parent",
                    subtitle = "Non-emergency apps are frozen until a parent resumes this device.",
                    hideCountdown = true,
                    onParentLock = { onOpenPin(false) },
                    emergencyApps = data.apps.filter {
                        it.rule.isEmergency || data.policy.emergencyApps.any { pkg ->
                            pkg.equals(it.rule.packageOrBundleId, ignoreCase = true)
                        }
                    },
                    onEmergency = { tile ->
                        scope.launch { viewModel.onAppTapped(tile) }
                    },
                )
            } else if (data.session.phase == SessionPhase.Shielded) {
                FailLockPane(
                    secondsLeft = data.remainingCooldownSeconds,
                    onParentLock = { onOpenPin(true) },
                    emergencyApps = data.apps.filter {
                        it.rule.isEmergency || data.policy.emergencyApps.any { pkg ->
                            pkg.equals(it.rule.packageOrBundleId, ignoreCase = true)
                        }
                    },
                    onEmergency = { tile ->
                        scope.launch { viewModel.onAppTapped(tile) }
                    },
                )
            } else if (data.dailyRemainingMinutes != null && data.dailyRemainingMinutes <= 0) {
                DailyLimitBlockerPane(
                    data = data,
                    isRefreshing = isRefreshing,
                    onRefreshRules = viewModel::refreshRules,
                    onOpenPhone = { launchNativePhone(context) },
                    onOpenCamera = { launchNativeCamera(context) },
                    onCallMom = { launchNativePhone(context) },
                    onCallDad = { launchNativePhone(context) },
                    onEmergency = { launchDialerWithNumber(context, "911") },
                    onOpenQuiz = {
                        if (!Settings.canDrawOverlays(context)) onOpenQuiz()
                    },
                    onOpenPin = { onOpenPin(false) },
                )
            } else {
                ChildHomeContent(
                    data = data,
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    isCallingEmergency = isCallingEmergency,
                    onStartEmergencyCall = viewModel::startEmergencyCall,
                    onEndEmergencyCall = viewModel::endEmergencyCall,
                    onAppTapped = { tile ->
                        scope.launch {
                            when (viewModel.onAppTapped(tile)) {
                                AppTapResult.QuizDue -> {
                                    if (!Settings.canDrawOverlays(context)) onOpenQuiz()
                                }
                                AppTapResult.Shielded -> Unit
                                AppTapResult.Blocked -> {
                                    if (!tile.rule.allowed) onNotAllowed()
                                }
                                AppTapResult.Launched -> Unit
                            }
                        }
                    },
                    onOpenQuiz = onOpenQuiz,
                    onOpenStickerBook = onOpenStickerBook,
                    onOpenPin = { onOpenPin(false) },
                    onOpenLauncherSetup = onOpenLauncherSetup,
                    isRefreshing = isRefreshing,
                    onRefreshRules = viewModel::refreshRules,
                    launchError = launchError,
                    onClearLaunchError = viewModel::clearLaunchError,
                    iconLoader = viewModel.iconLoader,
                )
            }
        }
    }
}

private fun launchNativePhone(context: Context) {
    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val targetIntent = runCatching {
        if (dialIntent.resolveActivity(context.packageManager) != null) {
            dialIntent
        } else {
            context.packageManager.getLaunchIntentForPackage("com.google.android.dialer")
                ?: context.packageManager.getLaunchIntentForPackage("com.android.dialer")
                ?: dialIntent
        }
    }.getOrDefault(dialIntent)
    runCatching {
        context.startActivity(targetIntent)
    }
}

private fun launchDialerWithNumber(context: Context, number: String) {
    val dialIntent = if (number.isNotBlank()) {
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
    } else {
        Intent(Intent.ACTION_DIAL)
    }.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(dialIntent)
    }
}

private fun launchNativeCamera(context: Context) {
    val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val targetIntent = runCatching {
        if (cameraIntent.resolveActivity(context.packageManager) != null) {
            cameraIntent
        } else {
            context.packageManager.getLaunchIntentForPackage("com.google.android.GoogleCamera")
                ?: context.packageManager.getLaunchIntentForPackage("com.android.camera")
                ?: cameraIntent
        }
    }.getOrDefault(cameraIntent)
    runCatching {
        context.startActivity(targetIntent)
    }
}

/**
 * Full C05 Child Launcher Screen matching Google Stitch specification.
 * Fully stateless for instant debug previewing and UI tests.
 */
@Composable
fun ChildHomeContent(
    data: ChildHomeUi,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isCallingEmergency: Boolean,
    onStartEmergencyCall: () -> Unit,
    onEndEmergencyCall: () -> Unit,
    onAppTapped: (HomeAppTile) -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenStickerBook: () -> Unit = {},
    onOpenPin: () -> Unit,
    onOpenLauncherSetup: () -> Unit,
    isRefreshing: Boolean = false,
    onRefreshRules: () -> Unit,
    launchError: String? = null,
    onClearLaunchError: () -> Unit = {},
    iconLoader: AppIconLoader? = null,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                onSearchQueryChange(spokenText)
            }
        }
    }

    val filteredApps = remember(data.apps, searchQuery) {
        if (searchQuery.isBlank()) {
            data.apps
        } else {
            val q = searchQuery.trim().lowercase()
            data.apps.filter {
                it.label.lowercase().contains(q) ||
                    it.subtitle.lowercase().contains(q) ||
                    it.category.lowercase().contains(q)
            }
        }
    }

    val isEarlyLearner = data.profile?.ageBand == com.meritscreen.core.common.domain.AgeBand.AGE_3_TO_6.name

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MeritColors.Background),
    ) {
        val layout = rememberChildHomeLayout(
            maxWidthDp = maxWidth.value,
            maxHeightDp = maxHeight.value,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding(),
        ) {
            MeritPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefreshRules,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (layout.useSplitPane) {
                    TabletSplitHomeBody(
                        layout = layout,
                        data = data,
                        filteredApps = filteredApps,
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        launchError = launchError,
                        onClearLaunchError = onClearLaunchError,
                        onOpenPin = onOpenPin,
                        onOpenLauncherSetup = onOpenLauncherSetup,
                        onOpenQuiz = onOpenQuiz,
                        onAppTapped = onAppTapped,
                        onRefreshRules = onRefreshRules,
                        iconLoader = iconLoader,
                        isEarlyLearner = isEarlyLearner,
                        onVoiceSearch = {
                            val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say an app name...")
                            }
                            runCatching { voiceSearchLauncher.launch(voiceIntent) }
                        },
                        onCallParents = { launchNativePhone(context) },
                    )
                } else {
                    PhoneOrPortraitHomeBody(
                        layout = layout,
                        data = data,
                        filteredApps = filteredApps,
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        launchError = launchError,
                        onClearLaunchError = onClearLaunchError,
                        onOpenPin = onOpenPin,
                        onOpenLauncherSetup = onOpenLauncherSetup,
                        onOpenQuiz = onOpenQuiz,
                        onAppTapped = onAppTapped,
                        onRefreshRules = onRefreshRules,
                        iconLoader = iconLoader,
                        isEarlyLearner = isEarlyLearner,
                        scrollState = scrollState,
                        onVoiceSearch = {
                            val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say an app name...")
                            }
                            runCatching { voiceSearchLauncher.launch(voiceIntent) }
                        },
                        onCallParents = { launchNativePhone(context) },
                    )
                }
            }
        }

        ElevatedBottomDock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = layout.dockBottomPadding)
                .padding(horizontal = layout.dockHorizontalPadding)
                .then(
                    if (layout.dockMaxWidth != null) {
                        Modifier.widthIn(max = layout.dockMaxWidth)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
                .fillMaxWidth(),
            layout = layout,
            onPhoneClick = { launchNativePhone(context) },
            onCameraClick = { launchNativeCamera(context) },
            onFamilyClick = onStartEmergencyCall,
            onRewardsClick = onOpenStickerBook,
        )

        if (isCallingEmergency) {
            EmergencyCallModal(
                onCancelCall = onEndEmergencyCall,
                onDirectDial = {
                    onEndEmergencyCall()
                    launchNativePhone(context)
                },
            )
        }
    }
}

@Composable
private fun PhoneOrPortraitHomeBody(
    layout: ChildHomeLayout,
    data: ChildHomeUi,
    filteredApps: List<HomeAppTile>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    launchError: String?,
    onClearLaunchError: () -> Unit,
    onOpenPin: () -> Unit,
    onOpenLauncherSetup: () -> Unit,
    onOpenQuiz: () -> Unit,
    onAppTapped: (HomeAppTile) -> Unit,
    onRefreshRules: () -> Unit,
    iconLoader: AppIconLoader?,
    isEarlyLearner: Boolean,
    scrollState: androidx.compose.foundation.ScrollState,
    onVoiceSearch: () -> Unit,
    onCallParents: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = layout.contentHorizontalPadding, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (layout.contentMaxWidth != null) {
                        Modifier.widthIn(max = layout.contentMaxWidth)
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing),
        ) {
            HomeChromeSections(
                layout = layout,
                data = data,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                launchError = launchError,
                onClearLaunchError = onClearLaunchError,
                onOpenPin = onOpenPin,
                onOpenLauncherSetup = onOpenLauncherSetup,
                onOpenQuiz = onOpenQuiz,
                isEarlyLearner = isEarlyLearner,
                onVoiceSearch = onVoiceSearch,
                onCallParents = onCallParents,
                includeQuestAndSpeedDial = true,
            )

            ApprovedPlaygroundSection(
                layout = layout,
                apps = filteredApps,
                allowedCount = data.apps.size,
                searchQuery = searchQuery,
                onClearSearch = { onSearchQueryChange("") },
                onAppTapped = onAppTapped,
                onRefreshRules = onRefreshRules,
                iconLoader = iconLoader,
                fillViewport = false,
            )

            Spacer(modifier = Modifier.height(layout.dockClearance))
        }
    }
}

@Composable
private fun TabletSplitHomeBody(
    layout: ChildHomeLayout,
    data: ChildHomeUi,
    filteredApps: List<HomeAppTile>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    launchError: String?,
    onClearLaunchError: () -> Unit,
    onOpenPin: () -> Unit,
    onOpenLauncherSetup: () -> Unit,
    onOpenQuiz: () -> Unit,
    onAppTapped: (HomeAppTile) -> Unit,
    onRefreshRules: () -> Unit,
    iconLoader: AppIconLoader?,
    isEarlyLearner: Boolean,
    onVoiceSearch: () -> Unit,
    onCallParents: () -> Unit,
) {
    val sideScroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = layout.contentHorizontalPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight()
                .verticalScroll(sideScroll)
                .padding(end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing),
        ) {
            HomeChromeSections(
                layout = layout,
                data = data,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                launchError = launchError,
                onClearLaunchError = onClearLaunchError,
                onOpenPin = onOpenPin,
                onOpenLauncherSetup = onOpenLauncherSetup,
                onOpenQuiz = onOpenQuiz,
                isEarlyLearner = isEarlyLearner,
                onVoiceSearch = onVoiceSearch,
                onCallParents = onCallParents,
                includeQuestAndSpeedDial = true,
            )
            Spacer(modifier = Modifier.height(layout.dockClearance))
        }

        Column(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight()
                .padding(bottom = layout.dockClearance),
        ) {
            ApprovedPlaygroundSection(
                layout = layout,
                apps = filteredApps,
                allowedCount = data.apps.size,
                searchQuery = searchQuery,
                onClearSearch = { onSearchQueryChange("") },
                onAppTapped = onAppTapped,
                onRefreshRules = onRefreshRules,
                iconLoader = iconLoader,
                fillViewport = true,
            )
        }
    }
}

@Composable
private fun HomeChromeSections(
    layout: ChildHomeLayout,
    data: ChildHomeUi,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    launchError: String?,
    onClearLaunchError: () -> Unit,
    onOpenPin: () -> Unit,
    onOpenLauncherSetup: () -> Unit,
    onOpenQuiz: () -> Unit,
    isEarlyLearner: Boolean,
    onVoiceSearch: () -> Unit,
    onCallParents: () -> Unit,
    includeQuestAndSpeedDial: Boolean,
) {
    KidProfileHeader(
        layout = layout,
        greetingName = data.greetingName,
        explorerLevel = data.explorerLevel,
        avatarEmoji = resolveAvatarEmoji(data.profile?.avatarId),
        onParentLock = onOpenPin,
    )


    if (!data.isDefaultHome) {
        HomeRoleBanner(onSetup = onOpenLauncherSetup)
    }
    ProtectionPermissionBanner()

    ScreenTimeAllowanceCard(
        dailyRemainingMinutes = data.dailyRemainingMinutes
            ?: kotlin.math.max(0, data.dailyCeilingMinutes - data.usedTodayMinutes),
        usedTodayMinutes = data.usedTodayMinutes,
        dailyCeilingMinutes = data.dailyCeilingMinutes,
        quietTimeHint = data.quietTimeHint,
        balanceStatus = data.balanceStatus,
        onOpenQuiz = onOpenQuiz,
        compact = layout.useSplitPane,
    )

    SearchVoiceBar(
        query = searchQuery,
        onQueryChange = onSearchQueryChange,
        onVoiceClick = onVoiceSearch,
    )

    launchError?.let { err ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClearLaunchError() },
            shape = RoundedCornerShape(12.dp),
            color = MeritColors.ErrorContainer,
        ) {
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MeritColors.OnErrorContainer,
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    if (includeQuestAndSpeedDial) {
        RocketMissionQuestBanner(
            onPlayNow = onOpenQuiz,
            isEarlyLearner = isEarlyLearner,
        )
        SpeedDialEmergencyCard(
            onCallParents = onCallParents,
        )
    }
}

// -----------------------------------------------------------------------------
// Sub-components: Kid Profile Header
// -----------------------------------------------------------------------------

@Composable
private fun KidProfileHeader(
    layout: ChildHomeLayout,
    greetingName: String,
    explorerLevel: Int,
    avatarEmoji: String,
    onParentLock: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Avatar with Level Badge Pill
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(layout.profileAvatarSize)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(MeritColors.Primary, MeritColors.PrimaryFixedDim),
                            ),
                        )
                        .border(2.dp, MeritColors.SurfaceContainerLowest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = avatarEmoji,
                        fontSize = if (layout.isTablet) 28.sp else 24.sp,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = MeritColors.Tertiary,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.SurfaceContainerLowest),
                    modifier = Modifier.offset(x = 4.dp, y = 2.dp),
                ) {
                    Text(
                        text = "Lv.$explorerLevel",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MeritColors.OnTertiary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }

            Column {
                Text(
                    text = "Hi, $greetingName! 👋",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = layout.titleTextSize,
                    ),
                    color = MeritColors.OnSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = MeritColors.Tertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Level $explorerLevel Explorer • Safe Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }
        }

        // Parent Gate Lock Button
        Surface(
            modifier = Modifier
                .size(layout.parentLockSize)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onParentLock),
            color = MeritColors.SurfaceContainerLow,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.4f)),
            shadowElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Parent Gate PIN",
                    tint = MeritColors.OnSurfaceVariant,
                    modifier = Modifier.size(if (layout.isTablet) 22.dp else 20.dp),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Sub-components: Screen Time Allowance Hero Card
// -----------------------------------------------------------------------------

@Composable
private fun ScreenTimeAllowanceCard(
    dailyRemainingMinutes: Int,
    usedTodayMinutes: Int,
    dailyCeilingMinutes: Int,
    quietTimeHint: String,
    balanceStatus: String,
    onOpenQuiz: () -> Unit,
    compact: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 20.dp else 24.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 14.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MeritColors.Primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = formatMinutes(dailyRemainingMinutes),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MeritColors.OnSurface,
                            )
                            Text(
                                text = "remaining today",
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                        Text(
                            text = quietTimeHint,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }

                // Fast Quiz Action CTA
                Button(
                    onClick = onOpenQuiz,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeritColors.Primary,
                        contentColor = MeritColors.OnPrimary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "+30m Quiz",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MeritColors.PrimaryFixed,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            // Dynamic Segmented Allowance Track
            val fractionUsed = if (dailyCeilingMinutes > 0) {
                (usedTodayMinutes.toFloat() / dailyCeilingMinutes.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (i in 0 until 4) {
                    val segmentStart = i * 0.25f
                    val segmentFill = ((fractionUsed - segmentStart) / 0.25f).coerceIn(0f, 1f)
                    val shape = when (i) {
                        0 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                        3 -> RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                        else -> RoundedCornerShape(0.dp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(shape)
                            .background(MeritColors.SurfaceContainerHigh),
                    ) {
                        if (segmentFill > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(segmentFill)
                                    .fillMaxHeight()
                                    .background(
                                        if (fractionUsed > 0.85f) MeritColors.Tertiary
                                        else MeritColors.Primary,
                                    ),
                            )
                        }
                    }
                }
            }

            // Bottom stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Used ${formatMinutes(usedTodayMinutes)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MeritColors.OnSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MeritColors.Tertiary),
                    )
                    Text(
                        text = balanceStatus,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MeritColors.Tertiary,
                    )
                }
                Text(
                    text = "Daily cap: ${formatMinutes(dailyCeilingMinutes)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Sub-components: Search & Voice Assist Pill Bar
// -----------------------------------------------------------------------------

@Composable
private fun SearchVoiceBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onVoiceClick: () -> Unit = {},
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(9999.dp),
        color = MeritColors.SurfaceContainerLowest,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (query.isNotEmpty()) MeritColors.Primary.copy(alpha = 0.5f)
            else MeritColors.OutlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = if (query.isNotEmpty()) MeritColors.Primary else MeritColors.Outline,
                modifier = Modifier.size(20.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MeritColors.OnSurface,
                    fontWeight = FontWeight.Medium,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MeritColors.Primary),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search apps, games & audio...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )

            // Clear search button if query entered
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MeritColors.OnSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Voice search action button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MeritColors.Primary.copy(alpha = 0.12f))
                    .clickable(onClick = onVoiceClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Search",
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Sub-components: Approved Playground App Grid (4 Columns)
// -----------------------------------------------------------------------------

@Composable
private fun ApprovedPlaygroundSection(
    layout: ChildHomeLayout,
    apps: List<HomeAppTile>,
    allowedCount: Int,
    searchQuery: String = "",
    onClearSearch: () -> Unit = {},
    onAppTapped: (HomeAppTile) -> Unit,
    onRefreshRules: () -> Unit,
    iconLoader: AppIconLoader?,
    fillViewport: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fillViewport) Modifier.fillMaxHeight() else Modifier),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchQuery.isNotBlank()) {
                Text(
                    text = "SEARCH RESULTS (${apps.size})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.sp,
                    ),
                    color = MeritColors.Primary,
                )
                Text(
                    text = "Clear filter",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MeritColors.Primary,
                    ),
                    modifier = Modifier.clickable(onClick = onClearSearch),
                )
            } else {
                Text(
                    text = "APPROVED PLAYGROUND",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 11.sp,
                    ),
                    color = MeritColors.OnSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = MeritColors.Primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "$allowedCount Allowed",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.Primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (apps.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MeritColors.SurfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.3f)),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "No apps found for \"$searchQuery\""
                        } else {
                            "No apps allowed yet in playground."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MeritColors.OnSurfaceVariant,
                    )
                    if (searchQuery.isNotBlank()) {
                        MeritSecondaryButton(
                            text = "Clear Search",
                            onClick = onClearSearch,
                        )
                    } else {
                        MeritSecondaryButton(
                            text = "Refresh Playground",
                            onClick = onRefreshRules,
                        )
                    }
                }
            }
        } else {
            val columns = layout.appGridColumns
            val rows = (apps.size + columns - 1) / columns
            val rowGap = if (layout.isTablet) 18.dp else 14.dp
            val gridModifier = if (fillViewport) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            } else {
                val gridHeight = layout.appTileRowHeight * rows + rowGap * (rows - 1).coerceAtLeast(0)
                Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = gridModifier,
                horizontalArrangement = Arrangement.spacedBy(if (layout.isTablet) 14.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(rowGap),
                userScrollEnabled = fillViewport,
                contentPadding = if (fillViewport) {
                    PaddingValues(bottom = 8.dp)
                } else {
                    PaddingValues(0.dp)
                },
            ) {
                items(
                    items = apps,
                    key = { it.rule.appId },
                    contentType = { "app_tile" },
                ) { tile ->
                    LauncherAppSquircleTile(
                        layout = layout,
                        tile = tile,
                        iconLoader = iconLoader,
                        onClick = { onAppTapped(tile) },
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Sub-components: Tactile Squircle Launcher App Tile
// -----------------------------------------------------------------------------

@Composable
private fun LauncherAppSquircleTile(
    layout: ChildHomeLayout,
    tile: HomeAppTile,
    iconLoader: AppIconLoader?,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (isPressed) 0.90f else 1.0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = tile.launchable || tile.rule.isEmergency,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            SquircleAppIcon(
                tile = tile,
                iconLoader = iconLoader,
                iconSize = layout.appIconSize,
            )

            tile.badgeLabel?.let { badge ->
                val (badgeBg, badgeFg) = when (tile.badgeType) {
                    TileBadgeType.TIME_ALERT -> MeritColors.SurfaceContainerHighest to Color(0xFF93000A)
                    TileBadgeType.FREE -> MeritColors.Primary to MeritColors.OnPrimary
                    TileBadgeType.OPEN -> MeritColors.Tertiary to MeritColors.OnTertiary
                    TileBadgeType.INFO -> {
                        when (badge) {
                            "Friends" -> Color(0xFF00514D) to Color.White
                            "Weekend" -> MeritColors.Secondary to MeritColors.OnSecondary
                            "Creative" -> Color(0xFF2F6B45) to Color.White
                            "School" -> Color(0xFF1C1C19) to Color.White
                            else -> MeritColors.SurfaceContainerHigh to MeritColors.OnSurfaceVariant
                        }
                    }
                    TileBadgeType.NEUTRAL -> MeritColors.SurfaceContainerHigh to MeritColors.OnSurfaceVariant
                }

                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = badgeBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.Background),
                    modifier = Modifier.offset(x = 4.dp, y = (-4).dp),
                    shadowElevation = 1.dp,
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = layout.badgeTextSize,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = tile.label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = layout.appLabelTextSize,
                fontWeight = FontWeight.Medium,
            ),
            color = MeritColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        Text(
            text = tile.subtitle.ifBlank { tile.category },
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = layout.appSubtitleTextSize,
            ),
            color = if (tile.subtitle.contains("Safe")) MeritColors.Tertiary else MeritColors.OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SquircleAppIcon(
    tile: HomeAppTile,
    iconLoader: AppIconLoader?,
    iconSize: Dp = 62.dp,
) {
    val designSize = 62.dp
    val scaleFactor = iconSize / designSize
    Box(
        modifier = Modifier.size(iconSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(designSize)
                .graphicsLayer {
                    scaleX = scaleFactor
                    scaleY = scaleFactor
                },
        ) {
            SquircleAppIconDesign(tile = tile, iconLoader = iconLoader)
        }
    }
}

@Composable
private fun SquircleAppIconDesign(
    tile: HomeAppTile,
    iconLoader: AppIconLoader?,
) {
    val pkg = tile.rule.packageOrBundleId.lowercase()
    var realBitmap by remember(pkg) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pkg) {
        if (iconLoader != null) {
            realBitmap = iconLoader.load(tile.rule.packageOrBundleId)
        }
    }

    val loaded = realBitmap
    if (loaded != null) {
        Surface(
            modifier = Modifier
                .size(62.dp)
                .shadow(4.dp, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            color = MeritColors.SurfaceContainerLowest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = tile.label,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        return
    }

    // High fidelity Google Stitch designed styled squircles
    when {
        // App 1: YouTube Kids (Red rounded squircle with white inner box + play arrow)
        pkg.contains("youtube") || tile.label.contains("YT Kids") -> {
            Surface(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFFF0000),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFFFF0000),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }

        // App 2: SafeGram / Instagram (Sunset gradient + camera glyph)
        pkg.contains("instagram") || pkg.contains("safegram") || tile.label.contains("SafeGram") -> {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFFDC80), Color(0xFFFD1D1D), Color(0xFF833AB4)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        // App 3: Kids Chat / Messenger (Blue + speech bubble with lightning bolt)
        pkg.contains("chat") || pkg.contains("message") || tile.label.contains("Kids Chat") -> {
            Surface(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0084FF),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }

        // App 4: Minecraft (Grass block + AR block glyph)
        pkg.contains("minecraft") || tile.label.contains("Minecraft") -> {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF4B8B3B))
                    .border(1.dp, Color(0xFF376B29), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF5C9E48)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF4B8B3B)),
                    )
                }
                Icon(
                    imageVector = Icons.Default.ViewInAr,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // App 5: Duo ABC (Duolingo lime green + open book)
        pkg.contains("duo") || tile.label.contains("Duo ABC") -> {
            Surface(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF58CC02),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }

        // App 6: Disney+ (Deep navy + glowing star)
        pkg.contains("disney") || tile.label.contains("Disney+") -> {
            Surface(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F1B4C),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B2B73)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.HotelClass,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        // App 7: Scratch Jr (Vibrant orange + puzzle piece)
        pkg.contains("scratch") || tile.label.contains("Scratch Jr") -> {
            Surface(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFFF8C1A),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }

        // App 8: Google Classroom (Forest green chalkboard + gold border + student group)
        pkg.contains("classroom") || tile.label.contains("Classroom") -> {
            Surface(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1E8E3E),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFF9AB00)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        // Generic Fallback App Tile
        else -> {
            Surface(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(4.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = MeritColors.PrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = tile.label.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnPrimaryContainer,
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Sub-components: Rocket Math Quest Banner
// -----------------------------------------------------------------------------

@Composable
private fun RocketMissionQuestBanner(
    onPlayNow: () -> Unit,
    isEarlyLearner: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isEarlyLearner) MeritColors.TertiaryContainer.copy(alpha = 0.5f) else MeritColors.SecondaryContainer.copy(alpha = 0.65f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isEarlyLearner) MeritColors.Tertiary else MeritColors.Primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isEarlyLearner) Icons.Default.AutoStories else Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (isEarlyLearner) "Sing & Learn Songs 🎶" else "Rocket Math Quest",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnSurface,
                        )
                        Surface(
                            shape = RoundedCornerShape(9999.dp),
                            color = (if (isEarlyLearner) MeritColors.Tertiary else MeritColors.Primary).copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = if (isEarlyLearner) "+Bonus" else "+20m",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = if (isEarlyLearner) MeritColors.Tertiary else MeritColors.Primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        text = if (isEarlyLearner) "Watch fun ABC, counting & animal songs to earn bonus time!" else "Solve 3 coin puzzles to unlock bonus Minecraft time!",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MeritColors.OnSecondaryContainer,
                        lineHeight = 15.sp,
                    )
                }
            }

            Button(
                onClick = onPlayNow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEarlyLearner) MeritColors.Tertiary else MeritColors.Primary,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (isEarlyLearner) "Sing Now" else "Play Now",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Sub-components: Speed Dial Emergency Card
// -----------------------------------------------------------------------------

@Composable
private fun SpeedDialEmergencyCard(onCallParents: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Overlapping Mom & Dad avatars
                Row(
                    modifier = Modifier.padding(start = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy((-8).dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MeritColors.Primary.copy(alpha = 0.18f))
                            .border(2.dp, MeritColors.SurfaceContainerLowest, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "👩", fontSize = 16.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MeritColors.SecondaryContainer)
                            .border(2.dp, MeritColors.SurfaceContainerLowest, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "👨", fontSize = 16.sp)
                    }
                }

                Column {
                    Text(
                        text = "Speed Dial: Mom & Dad",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = "Emergency calls always free • 1-tap dial",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MeritColors.Tertiary,
                    )
                }
            }

            IconButton(
                onClick = onCallParents,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MeritColors.Primary),
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call Mom & Dad",
                    tint = MeritColors.OnPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Sub-components: Fixed Elevated Frosted Bottom Dock
// -----------------------------------------------------------------------------

@Composable
private fun ElevatedBottomDock(
    modifier: Modifier = Modifier,
    layout: ChildHomeLayout,
    onPhoneClick: () -> Unit,
    onCameraClick: () -> Unit,
    onFamilyClick: () -> Unit,
    onRewardsClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MeritColors.SurfaceContainerLowest.copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = if (layout.isTablet) 10.dp else 8.dp,
                    horizontal = if (layout.isTablet) 20.dp else 16.dp,
                ),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockItem(
                label = "Phone",
                icon = Icons.Default.Call,
                backgroundColor = MeritColors.Primary,
                iconColor = MeritColors.OnPrimary,
                iconSize = layout.dockIconSize,
                glyphSize = layout.dockGlyphSize,
                onClick = onPhoneClick,
            )
            DockItem(
                label = "Camera",
                icon = Icons.Default.PhotoCamera,
                brush = Brush.linearGradient(listOf(Color(0xFF6B21A8), Color(0xFF9333EA))),
                iconColor = Color.White,
                iconSize = layout.dockIconSize,
                glyphSize = layout.dockGlyphSize,
                onClick = onCameraClick,
            )
            DockItem(
                label = "Family",
                icon = Icons.Default.Forum,
                backgroundColor = Color(0xFF0284C7),
                iconColor = Color.White,
                showUnreadDot = true,
                iconSize = layout.dockIconSize,
                glyphSize = layout.dockGlyphSize,
                onClick = onFamilyClick,
            )
            DockItem(
                label = "Rewards",
                icon = Icons.Default.Stars,
                brush = Brush.linearGradient(listOf(Color(0xFFEAB308), Color(0xFFCA8A04))),
                iconColor = Color.White,
                iconSize = layout.dockIconSize,
                glyphSize = layout.dockGlyphSize,
                onClick = onRewardsClick,
            )
        }
    }
}

@Composable
private fun DockItem(
    label: String,
    icon: ImageVector,
    backgroundColor: Color = Color.Transparent,
    brush: Brush? = null,
    iconColor: Color = Color.White,
    showUnreadDot: Boolean = false,
    iconSize: Dp = 50.dp,
    glyphSize: Dp = 26.dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (isPressed) 0.88f else 1.0f

    Column(
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(16.dp))
                    .then(
                        if (brush != null) Modifier.background(brush)
                        else Modifier.background(backgroundColor),
                    )
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(glyphSize),
                )
            }
            if (showUnreadDot) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .offset(x = 2.dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(MeritColors.Tertiary)
                        .border(1.5.dp, Color.White, CircleShape),
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MeritColors.OnSurface,
        )
    }
}

// -----------------------------------------------------------------------------
// Sub-components: Emergency Safe-Line Calling Modal Overlay
// -----------------------------------------------------------------------------

@Composable
private fun EmergencyCallModal(
    onCancelCall: () -> Unit,
    onDirectDial: () -> Unit = onCancelCall,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "callScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onCancelCall),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.85f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(MeritColors.Primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(36.dp),
                    )
                }

                Text(
                    text = "Calling Mom & Dad...",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "High-priority family safe-line connecting instantly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                // Direct action to open native phone dialer
                Button(
                    onClick = onDirectDial,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeritColors.Primary,
                        contentColor = MeritColors.OnPrimary,
                    ),
                    shape = RoundedCornerShape(9999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Phone Dialer",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }

                Button(
                    onClick = onCancelCall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeritColors.SurfaceContainerHigh,
                        contentColor = MeritColors.OnSurface,
                    ),
                    shape = RoundedCornerShape(9999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                ) {
                    Text(
                        text = "Close",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Sub-components: Home Role Setup Banner
// -----------------------------------------------------------------------------

@Composable
private fun HomeRoleBanner(onSetup: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint = MeritColors.Primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Set Watching as Home",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "So the Home button always comes back here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
            MeritSecondaryButton(text = "Set up", onClick = onSetup)
        }
    }
}

@Composable
private fun ProtectionPermissionBanner() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var usageAccessGranted by remember { mutableStateOf(ForegroundAppDetector.hasUsageStatsPermission(context)) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    fun refresh() {
        usageAccessGranted = ForegroundAppDetector.hasUsageStatsPermission(context)
        overlayGranted = Settings.canDrawOverlays(context)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (usageAccessGranted && overlayGranted) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Finish protection setup", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(
                "Usage Access detects app time. Display over other apps lets the quiz appear when time is up.",
                style = MaterialTheme.typography.bodySmall,
                color = MeritColors.OnSurfaceVariant,
            )
            if (!usageAccessGranted) {
                MeritSecondaryButton(
                    text = "Allow Usage Access",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    },
                )
            }
            if (!overlayGranted) {
                MeritSecondaryButton(
                    text = "Allow quiz popup",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    },
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Sub-components: Daily Limit Blocker Pane (End of Day Lockout)
// -----------------------------------------------------------------------------

@Composable
private fun DailyLimitBlockerPane(
    data: ChildHomeUi,
    isRefreshing: Boolean,
    onRefreshRules: () -> Unit,
    onOpenPhone: () -> Unit,
    onOpenCamera: () -> Unit,
    onCallMom: () -> Unit,
    onCallDad: () -> Unit,
    onEmergency: () -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenPin: () -> Unit,
) {
    val avatarEmoji = resolveAvatarEmoji(data.profile?.avatarId)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeritColors.Background)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding(),
    ) {
        MeritPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefreshRules,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Header with Kid Profile and Parent Unlock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(MeritColors.Primary, MeritColors.PrimaryFixedDim),
                                    ),
                                )
                                .border(2.dp, MeritColors.SurfaceContainerLowest, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = avatarEmoji, fontSize = 24.sp)
                        }
                        Column {
                            Text(
                                text = "Hi, ${data.greetingName}!",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MeritColors.OnSurface,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(text = "🌙", fontSize = 12.sp)
                                Text(
                                    text = "Rest Mode • Daily limit reached",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Parent PIN Unlock button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenPin),
                        color = MeritColors.SurfaceContainerLowest,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.4f)),
                        shadowElevation = 1.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Parent Unlock",
                                tint = MeritColors.OnSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 2. Hero Card: "Daily Screen Time Complete!"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MeritColors.SurfaceContainerLowest,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "🌙", fontSize = 32.sp)
                        }

                        Text(
                            text = "Daily Screen Time Complete",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MeritColors.OnSurface,
                            textAlign = TextAlign.Center,
                        )

                        Text(
                            text = "Awesome work today! You've used all your allotted screen time. Apps are locked to help you rest, explore outside, and recharge.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeritColors.OnSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                        )

                        // Allowance Summary Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MeritColors.SurfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = "Used Today",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MeritColors.OnSurfaceVariant,
                                    )
                                    Text(
                                        text = formatMinutes(data.usedTodayMinutes),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MeritColors.OnSurface,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Remaining",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MeritColors.OnSurfaceVariant,
                                    )
                                    Text(
                                        text = "0m",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFDC2626),
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Daily Limit",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MeritColors.OnSurfaceVariant,
                                    )
                                    Text(
                                        text = formatMinutes(data.dailyCeilingMinutes),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MeritColors.OnSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Family Speed Dial: Call Mom & Call Dad
                Text(
                    text = "FAMILY SPEED DIAL",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp,
                    ),
                    color = MeritColors.OnSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 4.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Call Mom Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onCallMom),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFCE7F3)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = "👩", fontSize = 26.sp)
                            }
                            Text(
                                text = "Call Mom",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MeritColors.OnSurface,
                            )
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = Color(0xFFDB2777),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = "Direct Call",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }

                    // Call Dad Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onCallDad),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0F2FE)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = "👨", fontSize = 26.sp)
                            }
                            Text(
                                text = "Call Dad",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MeritColors.OnSurface,
                            )
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = Color(0xFF0284C7),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = "Direct Call",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Emergency SOS Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onEmergency),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Emergency Assistance (911)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF991B1B),
                            )
                            Text(
                                text = "High-priority line • Always accessible anytime",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFDC2626),
                            )
                        }
                    }
                }

                // 5. Bonus Quiz Card (+30m Screen Time)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFECFDF5)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Earn +30m Screen Time",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MeritColors.OnSurface,
                            )
                            Text(
                                text = "Solve a learning quiz to unlock bonus minutes!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = onOpenQuiz,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF059669),
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(9999.dp),
                        ) {
                            Text(
                                text = "Quiz",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }

                // 6. Allowed Tools Dock (Phone, Camera, Parent PIN)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "ALLOWED ESSENTIAL TOOLS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                            ),
                            color = MeritColors.OnSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Phone Tool
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(onClick = onOpenPhone)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0F766E)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Phone",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Phone",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurface,
                                )
                            }

                            // Camera Tool
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(onClick = onOpenCamera)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF7C3AED)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Camera",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurface,
                                )
                            }

                            // Parent Unlock Tool
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(onClick = onOpenPin)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(MeritColors.SurfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Parent Unlock",
                                        tint = MeritColors.OnSurface,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Parent PIN",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Sub-components: Fail Lock Rest Mode Pane
// -----------------------------------------------------------------------------

@Composable
private fun FailLockPane(
    secondsLeft: Int,
    onParentLock: () -> Unit,
    emergencyApps: List<HomeAppTile>,
    onEmergency: (HomeAppTile) -> Unit,
    title: String = "Let’s take a mindful rest",
    subtitle: String = "Apps are temporarily paused by your parent. Emergency calls remain available anytime.",
    hideCountdown: Boolean = false,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        if (activity != null) LockTaskGuard.engage(activity)
        onDispose {
            if (activity != null) LockTaskGuard.release(activity)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeritColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MeritColors.TertiaryFixedDim),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MeritColors.OnTertiaryFixed,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MeritColors.OnSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!hideCountdown) {
                Text(
                    text = formatCountdown(secondsLeft),
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.Primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MeritColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (emergencyApps.isNotEmpty()) {
                Text(
                    text = "Emergency Apps",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                emergencyApps.forEach { tile ->
                    MeritSecondaryButton(
                        text = tile.label,
                        onClick = { onEmergency(tile) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MeritSecondaryButton(
                text = "Parent Lock & Settings",
                onClick = onParentLock,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Utilities
// -----------------------------------------------------------------------------

internal fun formatCountdown(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

internal fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h 00m"
        else -> "${m}m"
    }
}

private fun resolveAvatarEmoji(avatarId: String?): String {
    return when (avatarId?.lowercase()) {
        "lion" -> "🦁"
        "rabbit" -> "🐰"
        "bear" -> "🐻"
        "owl" -> "🦉"
        "turtle" -> "🐢"
        else -> "🦁"
    }
}
