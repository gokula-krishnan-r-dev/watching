package com.meritscreen.feature.parent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.ui.components.ChildAvatar
import com.meritscreen.core.ui.components.ChildAvatarTile
import com.meritscreen.core.ui.components.MeritPrimaryButton
import com.meritscreen.core.ui.components.MeritSecondaryButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.firebase.family.FamilyChildProfile
import com.meritscreen.core.ui.components.AppUsageLegend
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.components.MeritCircularProgressRing
import com.meritscreen.core.ui.components.MeritPullToRefreshBox
import com.meritscreen.core.ui.components.MeritSegmentedProgressBar
import com.meritscreen.core.ui.components.PulsingStatusDot
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDetailScreen(
    onBack: () -> Unit,
    onAllowlist: () -> Unit,
    onTimeLimits: () -> Unit,
    onQuizSettings: () -> Unit,
    onRewards: () -> Unit,
    onReports: () -> Unit,
    onPairDevice: () -> Unit,
    onDevices: () -> Unit,
    onDeleted: () -> Unit = onBack,
    viewModel: ChildDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val savingProfile by viewModel.savingProfile.collectAsStateWithLifecycle()
    val profileSaved by viewModel.profileSaved.collectAsStateWithLifecycle()

    LaunchedEffect(deleted) {
        if (deleted) onDeleted()
    }

    when (val current = state) {
        UiState.Loading -> LoadingState(message = "Loading child profile")
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(message = current.error.userMessage, onRetry = viewModel::refresh)
        is UiState.Success -> {
            ChildDetailContent(
                data = current.data,
                onBack = onBack,
                onAllowlist = onAllowlist,
                onTimeLimits = onTimeLimits,
                onQuizSettings = onQuizSettings,
                onRewards = onRewards,
                onReports = onReports,
                onPairDevice = onPairDevice,
                onDevices = onDevices,
                onTogglePause = viewModel::togglePause,
                onAddBonus = { viewModel.grantBonus(15) },
                onDeleteChild = viewModel::deleteChild,
                onUpdateProfile = viewModel::updateProfile,
                deleting = deleting,
                savingProfile = savingProfile,
                profileSaved = profileSaved,
                actionError = actionError,
                onClearActionError = viewModel::clearActionError,
                onClearProfileSaved = viewModel::clearProfileSaved,
                onRefresh = viewModel::refresh,
            )
        }
    }
}

@Composable
fun ChildDetailContent(
    data: ChildDetailUi,
    onBack: () -> Unit,
    onAllowlist: () -> Unit = {},
    onTimeLimits: () -> Unit = {},
    onQuizSettings: () -> Unit = {},
    onRewards: () -> Unit = {},
    onReports: () -> Unit = {},
    onPairDevice: () -> Unit = {},
    onDevices: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    onAddBonus: () -> Unit = {},
    onDeleteChild: () -> Unit = {},
    onUpdateProfile: (name: String, ageBand: AgeBand, avatar: AvatarPreset, language: String) -> Unit = { _, _, _, _ -> },
    onRefresh: () -> Unit = {},
    deleting: Boolean = false,
    savingProfile: Boolean = false,
    profileSaved: Boolean = false,
    actionError: String? = null,
    onClearActionError: () -> Unit = {},
    onClearProfileSaved: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }

    LaunchedEffect(profileSaved) {
        if (profileSaved) {
            showEditProfile = false
            onClearProfileSaved()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteConfirm = false },
            title = { Text("Delete ${data.profile.displayName}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This permanently removes their profile, devices, usage, and quiz data. " +
                            "Paired devices will sign out. This cannot be undone.",
                    )
                    if (actionError != null) {
                        Text(
                            text = actionError,
                            color = MeritColors.Error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDeleteChild,
                    enabled = !deleting,
                ) {
                    Text(if (deleting) "Deleting…" else "Delete Child", color = MeritColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, enabled = !deleting) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showEditProfile) {
        EditChildProfileDialog(
            profile = data.profile,
            saving = savingProfile,
            error = actionError,
            onDismiss = {
                if (!savingProfile) {
                    showEditProfile = false
                    onClearActionError()
                }
            },
            onSave = onUpdateProfile,
        )
    }

    Scaffold(
        topBar = {
            ChildDetailTopBar(
                avatar = data.profile.avatar,
                childName = data.profile.displayName,
                onBack = onBack,
                menuExpanded = showMenu,
                onMenuExpandedChange = { showMenu = it },
                onEditProfile = {
                    showMenu = false
                    showEditProfile = true
                },
                onDevices = {
                    showMenu = false
                    onDevices()
                },
                onDeleteChild = {
                    showMenu = false
                    onClearActionError()
                    showDeleteConfirm = true
                },
            )
        },
        containerColor = MeritColors.SurfaceContainerLow,
    ) { padding ->
        MeritPullToRefreshBox(
            isRefreshing = data.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            if (actionError != null && !showEditProfile && !showDeleteConfirm) {
                Text(actionError, color = MeritColors.Error, style = MaterialTheme.typography.bodySmall)
            }
            // Child Profile Banner
            ChildProfileBannerCard(
                displayName = data.profile.displayName,
                avatar = data.profile.avatar,
                ageLabel = data.profile.ageBand.displayLabel,
                explorationTierLabel = data.explorationTierLabel,
                launcherStatusLabel = data.launcherStatusLabel,
                deviceModelAndOs = data.deviceModelAndOs,
                batteryPercent = data.batteryPercent,
                lastSyncedLabel = data.lastSyncedLabel,
                connectionStatus = data.connectionStatus,
            )

            // Realtime Usage Snapshot (Screen Time Today)
            RealtimeUsageCard(
                todayMinutes = data.todayMinutes,
                dailyCeilingMinutes = data.dailyCeilingMinutes,
                remainingMinutes = data.remainingMinutes,
                segments = data.appUsageBreakdown,
                hasUsageBreakdown = data.hasUsageBreakdown,
                isPaused = data.isPaused,
                bonusToastMessage = data.bonusToastMessage,
                onTogglePause = onTogglePause,
                onAddBonus = onAddBonus,
            )

            // Quiz & Cognitive Health Card
            QuizCognitiveHealthCard(
                passThresholdPercent = data.policy.passScorePercent,
                scorePercent = data.quizScorePercent,
                scoreFraction = data.quizScoreFraction,
                feedbackNote = data.quizFeedbackNote,
                strengthsText = data.strengthsText,
                practiceGoalText = data.practiceGoalText,
                curriculumLevelText = data.curriculumLevelText,
                hasQuizData = data.hasQuizData,
                onAdjustQuiz = onQuizSettings,
            )

            // Policy & Device Controls List
            PolicyAndDeviceControlsCard(
                allowedAppCount = data.allowedAppCount,
                emergencyContactCount = data.emergencyContactCount,
                dailyCeilingMinutes = data.dailyCeilingMinutes,
                blockMinutes = data.policy.defaultBlockMinutes,
                questionsCount = data.policy.questionsPerQuiz,
                passThreshold = data.policy.passScorePercent,
                stickersUnlockedThisWeek = data.stickersUnlockedThisWeek,
                stickersUnlockedTotal = data.stickersUnlockedTotal,
                explorerLevel = data.explorerLevel,
                rewardsEnabled = data.policy.rewardsEnabled,
                onAllowlist = onAllowlist,
                onTimeLimits = onTimeLimits,
                onQuizSettings = onQuizSettings,
                onRewards = onRewards,
                onDevices = onDevices,
                onReports = onReports,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
}

@Composable
private fun ChildDetailTopBar(
    avatar: AvatarPreset,
    childName: String,
    onBack: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onDevices: () -> Unit,
    onDeleteChild: () -> Unit,
) {
    Surface(
        color = MeritColors.SurfaceContainerLow.copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MeritColors.OnSurface,
                    )
                }
                Text(
                    text = "Child Profile Calibration",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box {
                    IconButton(onClick = { onMenuExpandedChange(true) }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MeritColors.OnSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenuExpandedChange(false) },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit profile") },
                            onClick = onEditProfile,
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Devices") },
                            onClick = onDevices,
                            leadingIcon = {
                                Icon(Icons.Default.TabletAndroid, contentDescription = null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Child") },
                            onClick = onDeleteChild,
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MeritColors.Error)
                            },
                        )
                    }
                }
                ChildAvatar(
                    avatar = avatar,
                    size = 32.dp,
                    background = MeritColors.PrimaryContainer,
                    contentDescription = "$childName avatar",
                )
            }
        }
    }
}

@Composable
private fun EditChildProfileDialog(
    profile: FamilyChildProfile,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, ageBand: AgeBand, avatar: AvatarPreset, language: String) -> Unit,
) {
    var name by remember(profile.childId) { mutableStateOf(profile.displayName) }
    var ageBand by remember(profile.childId) { mutableStateOf(profile.ageBand) }
    var avatar by remember(profile.childId) { mutableStateOf(profile.avatar) }
    var language by remember(profile.childId) { mutableStateOf(profile.language.ifBlank { "en" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit child profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Child Name") },
                    placeholder = { Text("Enter child name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Age band", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgeBand.entries.forEach { band ->
                        val selected = band == ageBand
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (selected) MeritColors.PrimaryContainer
                                    else MeritColors.SurfaceContainer,
                                )
                                .clickable { ageBand = band }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(band.displayLabel, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Text("Avatar", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarPreset.entries.take(6).forEach { preset ->
                        val selected = preset == avatar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MeritColors.PrimaryContainer
                                    else MeritColors.SurfaceContainer,
                                )
                                .clickable { avatar = preset },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(preset.emoji)
                        }
                    }
                }
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it.take(16) },
                    label = { Text("Preferred Language") },
                    placeholder = { Text("Enter language code (e.g. en)") },
                    supportingText = { Text("Standard 2-letter language code (such as en, es, fr)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(error, color = MeritColors.Error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, ageBand, avatar, language) },
                enabled = !saving,
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ChildProfileBannerCard(
    displayName: String,
    avatar: AvatarPreset,
    ageLabel: String,
    explorationTierLabel: String,
    launcherStatusLabel: String,
    deviceModelAndOs: String,
    batteryPercent: Int?,
    lastSyncedLabel: String,
    connectionStatus: DeviceConnectionStatus,
) {
    val syncColor = when (connectionStatus) {
        DeviceConnectionStatus.Connected -> MeritColors.Tertiary
        DeviceConnectionStatus.Paused -> MeritColors.Secondary
        DeviceConnectionStatus.Disconnected -> MeritColors.Error
        DeviceConnectionStatus.Unpaired -> MeritColors.OnSurfaceVariant
    }
    val launcherActive = connectionStatus == DeviceConnectionStatus.Connected ||
        launcherStatusLabel.contains("Active", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Friendly illustrated avatar + name + grade
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(modifier = Modifier.size(64.dp)) {
                    ChildAvatarTile(
                        avatar = avatar,
                        size = 64.dp,
                        contentDescription = "$displayName avatar",
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (connectionStatus == DeviceConnectionStatus.Connected) {
                                    MeritColors.Tertiary
                                } else {
                                    MeritColors.OutlineVariant
                                },
                            )
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (connectionStatus == DeviceConnectionStatus.Connected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MeritColors.OnTertiary,
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            ),
                            color = MeritColors.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9999.dp))
                                .background(MeritColors.PrimaryFixed)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Age $ageLabel",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MeritColors.OnPrimaryFixed,
                            )
                        }
                    }
                    Text(
                        text = explorationTierLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = if (launcherActive) MeritColors.Tertiary else MeritColors.OnSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = launcherStatusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (launcherActive) MeritColors.Tertiary else MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }

            // Device Spec Capsule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeritColors.SurfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Icon(
                        imageVector = Icons.Default.TabletAndroid,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = deviceModelAndOs,
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (batteryPercent != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Battery5Bar,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "$batteryPercent%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MeritColors.OnSurface,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MeritColors.OutlineVariant),
                        )
                    }
                    Text(
                        text = lastSyncedLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = syncColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun RealtimeUsageCard(
    todayMinutes: Int,
    dailyCeilingMinutes: Int,
    remainingMinutes: Int,
    segments: List<com.meritscreen.core.ui.components.AppUsageSegment>,
    hasUsageBreakdown: Boolean,
    isPaused: Boolean,
    bonusToastMessage: String?,
    onTogglePause: () -> Unit,
    onAddBonus: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = "SCREEN TIME TODAY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            text = formatMinutes(todayMinutes),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                            ),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "/ ${formatMinutes(dailyCeilingMinutes)} limit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(MeritColors.PrimaryFixed.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = MeritColors.OnPrimaryFixedVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "${remainingMinutes}m left",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnPrimaryFixedVariant,
                        )
                    }
                }
            }

            // Segmented Visual Bar & Breakdown Legend
            if (hasUsageBreakdown) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeritSegmentedProgressBar(
                        segments = segments,
                        ceilingMinutes = dailyCeilingMinutes,
                    )
                    AppUsageLegend(segments = segments)
                }
            } else {
                Text(
                    text = if (todayMinutes == 0) {
                        "No screen time recorded today yet."
                    } else {
                        "Usage synced — per-app breakdown will appear after the next upload."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            // Instant Parent Actions (Height 48dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val pauseBg by animateColorAsState(
                    targetValue = if (isPaused) MeritColors.ErrorContainer else MeritColors.SurfaceContainer,
                    label = "detail_pause_bg",
                )
                val pauseFg = if (isPaused) MeritColors.OnErrorContainer else MeritColors.OnSurface
                val pauseIcon = if (isPaused) Icons.Default.LockOpen else Icons.Default.LockClock
                val pauseText = if (isPaused) "Resume Device" else "Pause Device"

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pauseBg)
                        .clickable(onClick = onTogglePause),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = pauseIcon,
                            contentDescription = null,
                            tint = if (isPaused) pauseFg else MeritColors.Primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = pauseText,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = pauseFg,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeritColors.PrimaryFixed)
                        .clickable(onClick = onAddBonus),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            tint = MeritColors.OnPrimaryFixed,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "+15m Bonus",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnPrimaryFixed,
                        )
                    }
                }
            }

            // Bonus Toast / Celebration Banner
            AnimatedVisibility(
                visible = bonusToastMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                if (bonusToastMessage != null) {
                    Text(
                        text = bonusToastMessage,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MeritColors.Tertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuizCognitiveHealthCard(
    passThresholdPercent: Int,
    scorePercent: Int,
    scoreFraction: String,
    feedbackNote: String,
    strengthsText: String,
    practiceGoalText: String,
    curriculumLevelText: String,
    hasQuizData: Boolean,
    onAdjustQuiz: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
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
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeritColors.TertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MeritColors.OnTertiaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Quiz & Cognitive Health",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "Adaptive unlock gate status",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(MeritColors.TertiaryFixed.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Pass: $passThresholdPercent%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.Tertiary,
                    )
                }
            }

            // Score Breakdown Card with Circular Progress Ring
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeritColors.SurfaceContainerLow)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Morning Unlock Check",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Text(
                        text = scoreFraction,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = feedbackNote,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = if (hasQuizData) MeritColors.Tertiary else MeritColors.OnSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                if (hasQuizData) {
                    MeritCircularProgressRing(
                        percent = scorePercent,
                        modifier = Modifier.size(56.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MeritColors.SurfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = MeritColors.OnSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            // Strengths & Practice Goal Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeritColors.SurfaceContainerLow)
                        .padding(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = null,
                            tint = MeritColors.Tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Strengths",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.Tertiary,
                        )
                    }
                    Text(
                        text = strengthsText,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MeritColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeritColors.SurfaceContainerLow)
                        .padding(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = MeritColors.Secondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Practice Goal",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.Secondary,
                        )
                    }
                    Text(
                        text = practiceGoalText,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MeritColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Bottom Curriculum Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Curriculum level: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Text(
                        text = curriculumLevelText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSurface,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onAdjustQuiz),
                ) {
                    Text(
                        text = "Adjust AI",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.Primary,
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyAndDeviceControlsCard(
    allowedAppCount: Int,
    emergencyContactCount: Int,
    dailyCeilingMinutes: Int,
    blockMinutes: Int,
    questionsCount: Int,
    passThreshold: Int,
    stickersUnlockedThisWeek: Int,
    stickersUnlockedTotal: Int,
    explorerLevel: Int,
    rewardsEnabled: Boolean,
    onAllowlist: () -> Unit,
    onTimeLimits: () -> Unit,
    onQuizSettings: () -> Unit,
    onRewards: () -> Unit,
    onDevices: () -> Unit,
    onReports: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "POLICY & DEVICE CONTROLS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            ),
            color = MeritColors.OnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column {
                ControlItemRow(
                    icon = Icons.Default.Apps,
                    title = "App Allowlist & Rules",
                    subtitle = "$allowedAppCount approved apps • $emergencyContactCount emergency contacts",
                    onClick = onAllowlist,
                )
                HorizontalDivider(color = MeritColors.SurfaceContainer, modifier = Modifier.padding(horizontal = 16.dp))

                ControlItemRow(
                    icon = Icons.Default.Timer,
                    title = "Time Limits & Daily Ceiling",
                    subtitle = "${formatMinutes(dailyCeilingMinutes)} ceiling • ${blockMinutes}m app blocks • Bedtime 8:00 PM",
                    onClick = onTimeLimits,
                )
                HorizontalDivider(color = MeritColors.SurfaceContainer, modifier = Modifier.padding(horizontal = 16.dp))

                ControlItemRow(
                    icon = Icons.Default.AutoStories,
                    title = "AI Adaptive Settings",
                    subtitle = "$questionsCount questions • $passThreshold% pass • MeritAI guidance",
                    onClick = onQuizSettings,
                )
                HorizontalDivider(color = MeritColors.SurfaceContainer, modifier = Modifier.padding(horizontal = 16.dp))

                ControlItemRow(
                    icon = Icons.Default.MilitaryTech,
                    title = "Rewards & Stickers Earned",
                    subtitle = when {
                        !rewardsEnabled -> "Stickers paused in Rewards settings"
                        stickersUnlockedTotal == 0 -> "Explorer Lv.$explorerLevel • No stickers yet"
                        else ->
                            "$stickersUnlockedThisWeek this week • $stickersUnlockedTotal total • Lv.$explorerLevel"
                    },
                    hasNotificationDot = stickersUnlockedThisWeek > 0,
                    onClick = onRewards,
                )
                HorizontalDivider(color = MeritColors.SurfaceContainer, modifier = Modifier.padding(horizontal = 16.dp))

                ControlItemRow(
                    icon = Icons.Default.Security,
                    title = "Device Health & Permissions",
                    subtitle = "Accessibility, Knox & Launcher granted",
                    onClick = onDevices,
                )
                HorizontalDivider(color = MeritColors.SurfaceContainer, modifier = Modifier.padding(horizontal = 16.dp))

                ControlItemRow(
                    icon = Icons.Default.Insights,
                    title = "Weekly Activity Reports",
                    subtitle = "App trends, bedtime adherence & streaks",
                    onClick = onReports,
                )
            }
        }
    }
}

@Composable
private fun ControlItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    hasNotificationDot: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeritColors.SurfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasNotificationDot) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MeritColors.Tertiary),
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MeritColors.OnSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

