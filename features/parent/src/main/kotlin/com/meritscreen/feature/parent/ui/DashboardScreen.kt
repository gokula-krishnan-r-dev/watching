package com.meritscreen.feature.parent.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.ui.R as CoreUiR
import com.meritscreen.core.ui.components.ChildAvatar
import com.meritscreen.core.ui.components.EmptyState
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.MeritPullToRefreshBox
import com.meritscreen.core.ui.theme.MeritColors

@Composable
fun DashboardScreen(
    onOpenChild: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenNotifications: () -> Unit,
    onAddChild: () -> Unit,
    onOpenChildren: () -> Unit = {},
    onOpenRules: ((String?) -> Unit)? = null,
    onOpenReports: ((String?) -> Unit)? = null,
    showPostOnboardingModal: Boolean = false,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        UiState.Loading -> DashboardSkeletonLoader(
            onOpenNotifications = onOpenNotifications,
            onOpenAccount = onOpenAccount,
        )
        UiState.Empty -> EmptyState(
            title = "No children added yet",
            message = "Add a child profile to track personalized AI education, adaptive learning milestones, and screen balance.",
            actionLabel = "Add Child Profile",
            onAction = onAddChild,
        )
        is UiState.Error -> ErrorState(
            message = current.error.userMessage,
            onRetry = viewModel::refresh,
        )
        is UiState.Success -> {
            DashboardContent(
                data = current.data,
                onOpenChild = onOpenChild,
                onOpenAccount = onOpenAccount,
                onOpenNotifications = onOpenNotifications,
                onAddChild = onAddChild,
                onOpenChildren = onOpenChildren,
                onOpenRules = onOpenRules,
                onOpenReports = onOpenReports,
                onTogglePause = viewModel::togglePause,
                onAddBonus = { childId -> viewModel.grantBonus(childId, 15) },
                onSelectTimeRange = viewModel::setTimeRange,
                onSelectChild = viewModel::selectChild,
                onRefresh = viewModel::refresh,
                showPostOnboardingModal = showPostOnboardingModal,
            )
        }
    }
}

@Composable
fun DashboardContent(
    data: DashboardUi,
    onOpenChild: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenNotifications: () -> Unit,
    onAddChild: () -> Unit,
    onOpenChildren: () -> Unit = {},
    onOpenRules: ((String?) -> Unit)? = null,
    onOpenReports: ((String?) -> Unit)? = null,
    onTogglePause: (String) -> Unit = {},
    onAddBonus: (String) -> Unit = {},
    onSelectTimeRange: (DashboardTimeRange) -> Unit = {},
    onSelectChild: (String?) -> Unit = {},
    onRefresh: () -> Unit = {},
    showPostOnboardingModal: Boolean = false,
) {
    var showCongratulatoryModal by remember { mutableStateOf(showPostOnboardingModal) }

    if (showCongratulatoryModal) {
        val firstChildName = data.children.firstOrNull()?.profile?.displayName ?: "your child"
        PostOnboardingCongratulatoryModal(
            childName = firstChildName,
            onDismiss = { showCongratulatoryModal = false },
            onViewChildStatus = {
                showCongratulatoryModal = false
                val firstChildId = data.children.firstOrNull()?.profile?.childId
                if (firstChildId != null) onOpenChild(firstChildId)
            },
        )
    }

    val activeChild = if (data.selectedChildId != null) {
        data.children.firstOrNull { it.profile.childId == data.selectedChildId } ?: data.children.firstOrNull()
    } else {
        data.children.firstOrNull()
    }

    Scaffold(
        topBar = {
            DashboardTopBar(
                syncStatusTime = data.syncStatusTime,
                onRefresh = onRefresh,
                onOpenNotifications = onOpenNotifications,
                onOpenAccount = onOpenAccount,
            )
        },
        bottomBar = {
            ParentBottomNavBar(
                selectedTab = ParentNavTab.HOME,
                childrenCount = data.children.size,
                onOpenHome = { /* Current screen */ },
                onOpenChildren = onOpenChildren,
                onOpenReports = {
                    val targetId = activeChild?.profile?.childId
                    onOpenReports?.invoke(targetId)
                },
                onOpenSettings = onOpenAccount,
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            // 1. Household Greeting & Telemetry Status
            item(key = "household_header") {
                HouseholdHeader(
                    familyName = data.familyName,
                    greetingName = data.greetingName,
                    devicesCount = data.protectedDevicesCount,
                )
            }

            // 2. Minimal Child Selector (Compact Sleek Chips)
            item(key = "children_chips") {
                ChildChipSelector(
                    children = data.children,
                    selectedChildId = data.selectedChildId,
                    canAddChild = data.canAddChild,
                    onSelectChild = onSelectChild,
                    onAddChild = onAddChild,
                    onViewDetails = onOpenChild,
                )
            }

            // 3. AI Pedagogical Insight & Milestone Card
            data.analytics.aiRecommendation?.let { recommendation ->
                item(key = "ai_insight_card") {
                    AiInsightCard(
                        recommendation = recommendation,
                        milestoneLabel = data.analytics.nextMilestoneLabel,
                        milestoneProgress = data.analytics.nextMilestoneProgress,
                        onAction = {
                            val targetId = activeChild?.profile?.childId
                            if (targetId != null) onOpenChild(targetId)
                        },
                    )
                }
            }

            // 4. Compact Metrics Grid (4-Metric Micro-Cards)
            item(key = "kpi_grid") {
                AnimatedContent(
                    targetState = data.analytics,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "kpi_transition",
                ) { analytics ->
                    CompactKpiGrid(analytics = analytics)
                }
            }

            // 5. Interactive Weekly AI Training Trend Chart
            item(key = "interactive_chart") {
                AnimatedContent(
                    targetState = data.analytics.dailyTrend,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "chart_transition",
                ) { dailyPoints ->
                    AiTrainingTrendChartCard(
                        dailyPoints = dailyPoints,
                        selectedTimeRange = data.selectedTimeRange,
                        onSelectRange = onSelectTimeRange,
                    )
                }
            }

            // 6. Compact Subject & Skill Mastery Matrix
            item(key = "subject_matrix") {
                AnimatedContent(
                    targetState = data.analytics.subjectBreakdowns,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "subjects_transition",
                ) { subjects ->
                    CompactSubjectMatrix(subjects = subjects)
                }
            }

            // 7. Device Supervision & Screen Balance Cards
            val childrenToSupervise = if (data.selectedChildId != null) {
                listOfNotNull(data.children.firstOrNull { it.profile.childId == data.selectedChildId })
            } else {
                data.children
            }

            if (childrenToSupervise.isNotEmpty()) {
                item(key = "device_supervision_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Device Supervision",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            ),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = if (data.selectedChildId == null) {
                                "${childrenToSupervise.size} ${if (childrenToSupervise.size == 1) "Device" else "Devices"}"
                            } else "Filtered Child",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }

                items(
                    items = childrenToSupervise,
                    key = { "device_supervision_${it.profile.childId}" },
                ) { childItem ->
                    DeviceSupervisionCard(
                        child = childItem,
                        distribution = data.analytics.focusDistribution,
                        onTogglePause = { onTogglePause(childItem.profile.childId) },
                        onAddBonus = { onAddBonus(childItem.profile.childId) },
                        onViewDetails = { onOpenChild(childItem.profile.childId) },
                    )
                }
            }

            // 8. Recent Learning Milestones & Activity Feed
            item(key = "milestones_feed") {
                RecentActivityFeedCard(
                    events = data.recentEvents,
                    milestones = data.analytics.milestones,
                    onOpenReports = {
                        val targetId = activeChild?.profile?.childId
                        onOpenReports?.invoke(targetId)
                    },
                )
            }

            // 9. Quick Controls Row
            item(key = "quick_controls") {
                QuickControlsRow(onAddChild = onAddChild)
            }

            item {
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
}

// ---------------------------------------------------------------------------
// TOP & BOTTOM BARS
// ---------------------------------------------------------------------------

@Composable
private fun DashboardTopBar(
    syncStatusTime: String,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_live")
    val livePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    Surface(
        color = MeritColors.SurfaceContainerLow.copy(alpha = 0.98f),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MeritColors.SurfaceContainerLowest,
                    border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.3f)),
                    shadowElevation = 1.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = CoreUiR.drawable.ic_watching_logo),
                            contentDescription = "Watching Logo",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
                Column {
                    Text(
                        text = "WATCHING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 10.sp,
                        ),
                        color = MeritColors.Primary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Dashboard",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            ),
                            color = MeritColors.OnSurface,
                        )
                        Surface(
                            shape = RoundedCornerShape(9999.dp),
                            color = MeritColors.SecondaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MeritColors.Secondary.copy(alpha = 0.2f)),
                            modifier = Modifier.clickable(onClick = onRefresh),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MeritColors.Tertiary.copy(alpha = livePulseAlpha)),
                                )
                                Text(
                                    text = "Live • $syncStatusTime",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MeritColors.OnSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = MeritColors.OnSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(onClick = onOpenAccount),
                    shape = CircleShape,
                    color = MeritColors.Primary,
                    shadowElevation = 1.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Account",
                            tint = MeritColors.OnPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}



// ---------------------------------------------------------------------------
// 1. HOUSEHOLD HEADER
// ---------------------------------------------------------------------------

@Composable
private fun HouseholdHeader(
    familyName: String,
    greetingName: String,
    devicesCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(9999.dp),
                color = MeritColors.SecondaryContainer.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MeritColors.Secondary.copy(alpha = 0.2f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = MeritColors.OnSecondaryContainer,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = familyName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        ),
                        color = MeritColors.OnSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = "Hi, $greetingName",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.3).sp,
                ),
                color = MeritColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Family AI Education & Supervision Hub",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MeritColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(9999.dp),
            color = MeritColors.SurfaceContainerLowest,
            border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.3f)),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (devicesCount > 0) MeritColors.Tertiary else MeritColors.OnSurfaceVariant.copy(alpha = 0.4f)),
                )
                Icon(
                    imageVector = Icons.Default.TabletAndroid,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = if (devicesCount == 1) "1 Device" else "$devicesCount Devices",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    ),
                    color = MeritColors.OnSurface,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. MINIMAL CHILDREN LIST (SLEEK CHIPS / CARDS)
// ---------------------------------------------------------------------------

@Composable
private fun ChildChipSelector(
    children: List<DashboardChildCard>,
    selectedChildId: String?,
    canAddChild: Boolean,
    onSelectChild: (String?) -> Unit,
    onAddChild: () -> Unit,
    onViewDetails: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Children Overview",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                ),
                color = MeritColors.OnSurface,
            )
            Text(
                text = if (selectedChildId == null) "All Profiles (${children.size})" else "Filtered Child",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MeritColors.OnSurfaceVariant,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // "All Children" Summary Chip
            item(key = "all_children_chip") {
                val isAllSelected = selectedChildId == null
                val bgColor by animateColorAsState(
                    targetValue = if (isAllSelected) MeritColors.PrimaryFixed else MeritColors.SurfaceContainerLowest,
                    label = "all_chip_bg",
                )
                val borderColor = if (isAllSelected) MeritColors.Primary else MeritColors.OutlineVariant.copy(alpha = 0.35f)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bgColor,
                    border = BorderStroke(if (isAllSelected) 1.5.dp else 1.dp, borderColor),
                    shadowElevation = if (isAllSelected) 2.dp else 1.dp,
                    modifier = Modifier
                        .clickable { onSelectChild(null) }
                        .semantics { contentDescription = "View all children aggregated stats" },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isAllSelected) MeritColors.Primary else MeritColors.SecondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.FamilyRestroom,
                                contentDescription = null,
                                tint = if (isAllSelected) MeritColors.OnPrimary else MeritColors.OnSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "All Children",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isAllSelected) MeritColors.OnPrimaryFixed else MeritColors.OnSurface,
                            )
                            Text(
                                text = "${children.size} ${if (children.size == 1) "Profile" else "Profiles"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isAllSelected) MeritColors.OnPrimaryFixedVariant else MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Per-Child Compact Chips
            items(children, key = { it.profile.childId }) { card ->
                val isSelected = card.profile.childId == selectedChildId
                val chipBg by animateColorAsState(
                    targetValue = if (isSelected) MeritColors.PrimaryFixed else MeritColors.SurfaceContainerLowest,
                    label = "child_chip_bg",
                )
                val borderColor = if (isSelected) MeritColors.Primary else MeritColors.OutlineVariant.copy(alpha = 0.35f)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = chipBg,
                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                    shadowElevation = if (isSelected) 2.dp else 1.dp,
                    modifier = Modifier
                        .clickable {
                            if (isSelected) onViewDetails(card.profile.childId)
                            else onSelectChild(card.profile.childId)
                        }
                        .semantics { contentDescription = "Child profile ${card.profile.displayName}" },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Avatar + Active Indicator Dot
                        Box(modifier = Modifier.size(34.dp)) {
                            ChildAvatar(
                                avatar = card.profile.avatar,
                                size = 34.dp,
                                background = if (card.isDeviceActive) {
                                    MeritColors.TertiaryContainer
                                } else {
                                    MeritColors.SecondaryContainer
                                },
                                contentDescription = "${card.profile.displayName} avatar",
                            )
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (card.isDeviceActive) MeritColors.Tertiary else MeritColors.Secondary)
                                    .align(Alignment.BottomEnd),
                            )
                        }

                        // Info & Mini-indicators
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = card.profile.displayName,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MeritColors.OnPrimaryFixed else MeritColors.OnSurface,
                                    maxLines = 1,
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9999.dp))
                                        .background(if (isSelected) MeritColors.Primary.copy(alpha = 0.15f) else MeritColors.SurfaceContainer)
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = card.gradeStandard.ifBlank { card.profile.ageBand.displayLabel },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (isSelected) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "${card.todayMinutes}m AI • ${card.masteryRatePercent}%",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = if (isSelected) MeritColors.OnPrimaryFixedVariant else MeritColors.OnSurfaceVariant,
                                )
                                if (card.batteryPercent != null) {
                                    Icon(
                                        imageVector = Icons.Default.Battery5Bar,
                                        contentDescription = null,
                                        tint = if (isSelected) MeritColors.OnPrimaryFixedVariant else MeritColors.Primary,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = "${card.batteryPercent}%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = if (isSelected) MeritColors.OnPrimaryFixedVariant else MeritColors.OnSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // "+ Add" Action Chip
            if (canAddChild) {
                item(key = "add_child_chip") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MeritColors.SurfaceContainerLowest,
                        border = BorderStroke(1.dp, MeritColors.Primary.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable(onClick = onAddChild),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "Add Child",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                ),
                                color = MeritColors.Primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. AI PEDAGOGICAL INSIGHT & MILESTONE CARD
// ---------------------------------------------------------------------------

@Composable
private fun AiInsightCard(
    recommendation: AiRecommendation,
    milestoneLabel: String,
    milestoneProgress: Float,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.Primary.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MeritColors.PrimaryFixed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = MeritColors.OnPrimaryFixed,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = "AI Pedagogical Coach",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MeritColors.Primary,
                        ),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = MeritColors.SecondaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MeritColors.Secondary.copy(alpha = 0.25f)),
                ) {
                    Text(
                        text = recommendation.priorityLevel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MeritColors.OnSecondaryContainer,
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = recommendation.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = recommendation.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    ),
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            // Milestone Track
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MeritColors.SurfaceContainer.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.2f)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Next Learning Milestone",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MeritColors.OnSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = milestoneLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MeritColors.Primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val animatedMilestone by animateFloatAsState(
                        targetValue = milestoneProgress,
                        animationSpec = tween(600, easing = FastOutSlowInEasing),
                        label = "milestone_prog",
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(MeritColors.PrimaryFixed.copy(alpha = 0.35f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedMilestone)
                                .height(6.dp)
                                .clip(RoundedCornerShape(9999.dp))
                                .background(MeritColors.Primary),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MeritColors.Primary,
                    shadowElevation = 1.dp,
                    modifier = Modifier.clickable(onClick = onAction),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = recommendation.actionLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MeritColors.OnPrimary,
                            ),
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MeritColors.OnPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. COMPACT METRICS GRID (4 MICRO-CARDS)
// ---------------------------------------------------------------------------

@Composable
private fun CompactKpiGrid(analytics: LearningAnalyticsOverview) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactKpiCard(
                modifier = Modifier.weight(1f),
                title = "AI Training Time",
                value = analytics.formattedAiTime,
                subtitle = "Active guided lessons",
                icon = Icons.Default.Psychology,
                iconBg = MeritColors.PrimaryFixed,
                iconTint = MeritColors.OnPrimaryFixed,
            )
            CompactKpiCard(
                modifier = Modifier.weight(1f),
                title = "Modules Mastered",
                value = "${analytics.modulesCompleted}",
                subtitle = "Milestones passed",
                icon = Icons.Default.School,
                iconBg = MeritColors.TertiaryFixed,
                iconTint = MeritColors.OnTertiaryFixed,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactKpiCard(
                modifier = Modifier.weight(1f),
                title = "Mastery Rate",
                value = "${analytics.accuracyPercent}%",
                subtitle = if (analytics.accuracyPercent >= 80) "High retention" else "Skill building",
                icon = Icons.Default.WorkspacePremium,
                iconBg = MeritColors.SecondaryFixed,
                iconTint = MeritColors.OnSecondaryFixed,
            )
            CompactKpiCard(
                modifier = Modifier.weight(1f),
                title = "Adaptive Level",
                value = "${analytics.adaptiveDifficultyLabel.substringBefore(" ·")} (🔥${analytics.currentStreakDays}d)",
                subtitle = analytics.difficultyTrend,
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                iconBg = MeritColors.PrimaryContainer,
                iconTint = MeritColors.OnPrimaryContainer,
            )
        }
    }
}

@Composable
private fun CompactKpiCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    ),
                    color = MeritColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                ),
                color = MeritColors.OnSurface,
                maxLines = 1,
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MeritColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 5. INTERACTIVE WEEKLY AI TRAINING TREND CHART
// ---------------------------------------------------------------------------

@Composable
private fun AiTrainingTrendChartCard(
    dailyPoints: List<DailyLearningPoint>,
    selectedTimeRange: DashboardTimeRange,
    onSelectRange: (DashboardTimeRange) -> Unit,
) {
    var selectedIndex by remember(dailyPoints) { mutableIntStateOf(dailyPoints.lastIndex.coerceAtLeast(0)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header with Integrated Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "AI Training Trend",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = "Interactive daily session pulse",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MeritColors.OnSurfaceVariant,
                    )
                }

                // Compact Time Range Filter Pills
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = MeritColors.SurfaceContainer,
                    border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.2f)),
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        DashboardTimeRange.entries.forEach { range ->
                            val isSelected = range == selectedTimeRange
                            val pillBg by animateColorAsState(
                                targetValue = if (isSelected) MeritColors.SurfaceContainerLowest else Color.Transparent,
                                label = "range_pill_bg",
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9999.dp))
                                    .background(pillBg)
                                    .clickable { onSelectRange(range) }
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = when (range) {
                                        DashboardTimeRange.TODAY -> "Today"
                                        DashboardTimeRange.WEEK -> "7D"
                                        DashboardTimeRange.MONTH -> "30D"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.sp,
                                    ),
                                    color = if (isSelected) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Day Detail Badge
            val selectedPoint = dailyPoints.getOrNull(selectedIndex)
            if (selectedPoint != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeritColors.PrimaryFixed.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MeritColors.Primary.copy(alpha = 0.2f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selectedPoint.dayLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnPrimaryFixed,
                        )
                        Text(
                            text = "${selectedPoint.aiTrainingMinutes}m AI • ${selectedPoint.modulesCount} Quizzes",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnPrimaryFixed,
                        )
                    }
                }
            }

            // Custom Interactive Bar Chart with Background Track
            val maxMinutes = dailyPoints.maxOfOrNull { it.aiTrainingMinutes }?.coerceAtLeast(30) ?: 30

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                dailyPoints.forEachIndexed { index, point ->
                    val isSelected = index == selectedIndex
                    val targetFraction = (point.aiTrainingMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0.08f, 1f)
                    val animatedFraction by animateFloatAsState(
                        targetValue = targetFraction,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                        label = "bar_height_$index",
                    )
                    val barColor by animateColorAsState(
                        targetValue = if (isSelected) MeritColors.Primary else MeritColors.PrimaryFixed.copy(alpha = 0.6f),
                        label = "bar_color_$index",
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectedIndex = index },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isSelected) 0.85f else 0.7f)
                                .fillMaxHeight(animatedFraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor),
                        )
                    }
                }
            }

            // Day Labels Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                dailyPoints.forEachIndexed { index, point ->
                    val isSelected = index == selectedIndex
                    Text(
                        text = point.dayLabel.take(3),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (dailyPoints.size > 14) 8.sp else 10.sp,
                        ),
                        color = if (isSelected) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 6. COMPACT SUBJECT & SKILL MASTERY MATRIX
// ---------------------------------------------------------------------------

@Composable
private fun CompactSubjectMatrix(subjects: List<SubjectProgressItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Subject & Skill Matrix",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                        color = MeritColors.OnSurface,
                    )
                }
                Text(
                    text = "${subjects.size} Core Disciplines",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subjects.forEach { item ->
                    CompactSubjectRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun CompactSubjectRow(item: SubjectProgressItem) {
    val animatedProgress by animateFloatAsState(
        targetValue = item.masteryPercent / 100f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "subject_prog_${item.subject}",
    )

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.subject,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    ),
                    color = MeritColors.OnSurface,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(MeritColors.SurfaceContainer)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = item.levelLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
                if (item.weakConcept != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(MeritColors.SecondaryContainer)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "Focus: ${item.weakConcept}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MeritColors.OnSecondaryContainer,
                            ),
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.streakCount > 1) {
                    Text(
                        text = "🔥 ${item.streakCount} streak",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeritColors.Secondary,
                        ),
                    )
                }
                Text(
                    text = "${item.masteryPercent}%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    ),
                    color = item.color,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(9999.dp))
                .background(item.color.copy(alpha = 0.16f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(item.color),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 7. DEVICE SUPERVISION & SCREEN BALANCE CARD
// ---------------------------------------------------------------------------

private fun formatRemainingTime(minutes: Int): String = when {
    minutes <= 0 -> "0m left"
    minutes < 60 -> "${minutes}m left"
    else -> {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "${h}h left" else "${h}h ${m}m left"
    }
}

private fun formatDeviceDisplayName(rawModel: String?): String {
    if (rawModel.isNullOrBlank()) return "Android Device"
    val trimmed = rawModel.trim()
    if (trimmed.contains("sdk_gphone", ignoreCase = true) ||
        trimmed.contains("emulator", ignoreCase = true) ||
        trimmed.contains("goldfish", ignoreCase = true) ||
        trimmed.contains("ranchu", ignoreCase = true)
    ) {
        return "Pixel Device"
    }
    if (trimmed.startsWith("SM-", ignoreCase = true)) {
        return "Galaxy Phone"
    }
    return trimmed.removePrefix("Google ").removePrefix("Android ").take(16)
}

@Composable
private fun DeviceSupervisionCard(
    child: DashboardChildCard,
    distribution: FocusDistributionData,
    onTogglePause: () -> Unit,
    onAddBonus: () -> Unit,
    onViewDetails: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header with Child Avatar, Device Info & Remaining Time Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left Device Column with weight to ensure space for the badge!
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(modifier = Modifier.size(38.dp)) {
                        ChildAvatar(
                            avatar = child.profile.avatar,
                            size = 38.dp,
                            background = if (child.isDeviceActive) MeritColors.TertiaryContainer else MeritColors.SecondaryContainer,
                            contentDescription = "${child.profile.displayName} avatar",
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        child.isPaused -> MeritColors.Error
                                        child.isDeviceActive -> MeritColors.Tertiary
                                        else -> MeritColors.OnSurfaceVariant.copy(alpha = 0.4f)
                                    }
                                )
                                .align(Alignment.BottomEnd)
                                .border(1.5.dp, MeritColors.SurfaceContainerLowest, CircleShape),
                        )
                    }
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "${child.profile.displayName}'s Device Balance",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            ),
                            color = MeritColors.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val cleanDeviceName = formatDeviceDisplayName(child.deviceModel)
                            val statusLabel = when {
                                child.isPaused -> "Paused"
                                child.isDeviceActive -> "Active on $cleanDeviceName"
                                else -> "Resting • $cleanDeviceName"
                            }
                            val statusTint = when {
                                child.isPaused -> MeritColors.Error
                                child.isDeviceActive -> MeritColors.Tertiary
                                else -> MeritColors.OnSurfaceVariant
                            }

                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = statusTint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (child.batteryPercent != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Battery5Bar,
                                        contentDescription = null,
                                        tint = MeritColors.OnSurfaceVariant,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = "${child.batteryPercent}%",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = MeritColors.OnSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Prominent Remaining Time Badge - Guaranteed Horizontal, Never Wraps
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        child.isPaused -> MeritColors.ErrorContainer.copy(alpha = 0.6f)
                        child.remainingMinutes <= 15 -> MeritColors.SecondaryContainer.copy(alpha = 0.75f)
                        else -> MeritColors.Primary.copy(alpha = 0.12f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            child.isPaused -> MeritColors.Error.copy(alpha = 0.35f)
                            child.remainingMinutes <= 15 -> MeritColors.Secondary.copy(alpha = 0.4f)
                            else -> MeritColors.Primary.copy(alpha = 0.25f)
                        }
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = when {
                                child.isPaused -> Icons.Default.LockClock
                                child.remainingMinutes <= 15 -> Icons.Default.TimerOff
                                else -> Icons.Default.HourglassTop
                            },
                            contentDescription = null,
                            tint = when {
                                child.isPaused -> MeritColors.OnErrorContainer
                                child.remainingMinutes <= 15 -> MeritColors.OnSecondaryContainer
                                else -> MeritColors.Primary
                            },
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = if (child.isPaused) "Paused" else formatRemainingTime(child.remainingMinutes),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            ),
                            color = when {
                                child.isPaused -> MeritColors.OnErrorContainer
                                child.remainingMinutes <= 15 -> MeritColors.OnSecondaryContainer
                                else -> MeritColors.Primary
                            },
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            // Allowance Gauge
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Daily Screen Allowance",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Text(
                        text = "${child.todayMinutes}m / ${child.dailyCeilingMinutes}m",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MeritColors.OnSurface,
                    )
                }

                val allowanceFraction = if (child.dailyCeilingMinutes > 0) {
                    (child.todayMinutes.toFloat() / child.dailyCeilingMinutes.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val animatedAllowance by animateFloatAsState(
                    targetValue = allowanceFraction,
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label = "allowance_prog_${child.profile.childId}",
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(9999.dp))
                        .background(MeritColors.PrimaryFixed.copy(alpha = 0.35f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedAllowance)
                            .height(8.dp)
                            .clip(RoundedCornerShape(9999.dp))
                            .background(
                                when {
                                    child.isPaused -> MeritColors.Error
                                    allowanceFraction >= 0.9f -> MeritColors.Error
                                    allowanceFraction >= 0.75f -> MeritColors.Secondary
                                    else -> MeritColors.Primary
                                }
                            ),
                    )
                }
            }

            // Foreground App Pill
            if (!child.isPaused && child.topAppLabel != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MeritColors.SurfaceContainer,
                    border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.2f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MeritColors.Primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartDisplay,
                                    contentDescription = null,
                                    tint = MeritColors.Primary,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                            Text(
                                text = "Active: ${child.topAppLabel}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                ),
                                color = MeritColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = MeritColors.OnSurfaceVariant,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "${child.topAppRemainingMinutes ?: 15}m block left",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = MeritColors.OnSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            // 1-Tap Quick Action Buttons - Cohesive Modern Action System
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pauseBg by animateColorAsState(
                    targetValue = if (child.isPaused) MeritColors.ErrorContainer else MeritColors.SurfaceContainerHigh,
                    label = "pause_bg_${child.profile.childId}",
                )
                val pauseBorder = if (child.isPaused) MeritColors.Error.copy(alpha = 0.4f) else MeritColors.OutlineVariant.copy(alpha = 0.3f)
                val pauseTextColor = if (child.isPaused) MeritColors.OnErrorContainer else MeritColors.OnSurface

                // Pause / Resume Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clickable(onClick = onTogglePause),
                    shape = RoundedCornerShape(12.dp),
                    color = pauseBg,
                    border = BorderStroke(1.dp, pauseBorder),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (child.isPaused) Icons.Default.PlayArrow else Icons.Default.PauseCircle,
                            contentDescription = null,
                            tint = pauseTextColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (child.isPaused) "Resume" else "Pause",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            ),
                            color = pauseTextColor,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                // Grant Bonus Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clickable(onClick = onAddBonus),
                    shape = RoundedCornerShape(12.dp),
                    color = MeritColors.SecondaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MeritColors.Secondary.copy(alpha = 0.25f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MeritColors.OnSecondaryContainer,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "+15m Bonus",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            ),
                            color = MeritColors.OnSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                // Details Button (Primary Action)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clickable(onClick = onViewDetails),
                    shape = RoundedCornerShape(12.dp),
                    color = MeritColors.Primary,
                    shadowElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MeritColors.OnPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Details",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            ),
                            color = MeritColors.OnPrimary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 8. RECENT LEARNING SESSIONS & ACTIVITY FEED
// ---------------------------------------------------------------------------

@Composable
private fun RecentActivityFeedCard(
    events: List<DashboardActivityEvent>,
    milestones: List<MilestoneHighlight>,
    onOpenReports: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MeritColors.PrimaryFixed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MeritColors.OnPrimaryFixed,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = "Learning Feed & Integrity",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                        color = MeritColors.OnSurface,
                    )
                }
                Text(
                    text = "Full Reports",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.Primary,
                    ),
                    modifier = Modifier.clickable(onClick = onOpenReports),
                )
            }

            events.forEach { event ->
                CompactActivityEventRow(event = event)
            }
        }
    }
}

@Composable
private fun CompactActivityEventRow(event: DashboardActivityEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (bg, fg, icon) = when (event.eventType) {
            ActivityEventType.QUIZ_PASS -> Triple(
                MeritColors.TertiaryFixed,
                MeritColors.OnTertiaryFixed,
                Icons.Default.WorkspacePremium,
            )
            ActivityEventType.LIMIT_REACHED -> Triple(
                MeritColors.SecondaryContainer,
                MeritColors.OnSecondaryContainer,
                Icons.Default.TimerOff,
            )
            ActivityEventType.SYSTEM_CHECK -> Triple(
                MeritColors.SurfaceContainer,
                MeritColors.OnSurfaceVariant,
                Icons.Default.Sync,
            )
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    ),
                    color = MeritColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = event.timeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MeritColors.OnSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = event.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = if (event.eventType == ActivityEventType.QUIZ_PASS) MeritColors.Tertiary else MeritColors.OnSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.scoreBadge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(MeritColors.TertiaryFixed)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = event.scoreBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MeritColors.OnTertiaryFixed,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 9. QUICK CONTROLS
// ---------------------------------------------------------------------------

@Composable
private fun QuickControlsRow(onAddChild: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "QUICK SAFETY CONTROLS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 10.sp,
            ),
            color = MeritColors.OnSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Add Child Pill
            Surface(
                shape = RoundedCornerShape(9999.dp),
                color = MeritColors.Primary,
                shadowElevation = 1.dp,
                modifier = Modifier.clickable(onClick = onAddChild),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MeritColors.OnPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "Add Child",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        ),
                        color = MeritColors.OnPrimary,
                    )
                }
            }

            // Bedtime Schedule Pill
            Surface(
                shape = RoundedCornerShape(9999.dp),
                color = MeritColors.SurfaceContainerLowest,
                border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.3f)),
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.NightsStay,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Row {
                        Text(
                            text = "Bedtime ",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            ),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = AppConfig.DEFAULT_BEDTIME_LABEL,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }

            // Emergency Allowlist Pill
            Surface(
                shape = RoundedCornerShape(9999.dp),
                color = MeritColors.SurfaceContainerLowest,
                border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.3f)),
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Emergency,
                        contentDescription = null,
                        tint = MeritColors.Tertiary,
                        modifier = Modifier.size(15.dp),
                    )
                    Row {
                        Text(
                            text = "Allowlist ",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            ),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "Calls & Maps",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 10. SKELETON LOADER
// ---------------------------------------------------------------------------

@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    return Brush.linearGradient(
        colors = listOf(
            MeritColors.SurfaceContainerHigh,
            MeritColors.SurfaceContainerLowest.copy(alpha = 0.8f),
            MeritColors.SurfaceContainerHigh,
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim),
    )
}

@Composable
private fun DashboardSkeletonLoader(
    onOpenNotifications: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val shimmerBrush = rememberShimmerBrush()

    Scaffold(
        topBar = {
            DashboardTopBar(
                syncStatusTime = "Syncing...",
                onRefresh = {},
                onOpenNotifications = onOpenNotifications,
                onOpenAccount = onOpenAccount,
            )
        },
        containerColor = MeritColors.SurfaceContainerLow,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header skeleton
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush),
            )
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush),
            )

            // Children chips skeleton
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(shimmerBrush),
                    )
                }
            }

            // Insight card skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(shimmerBrush),
            )

            // 4-card metric grid skeleton
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(shimmerBrush),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(shimmerBrush),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(shimmerBrush),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(shimmerBrush),
                    )
                }
            }

            // Chart skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(shimmerBrush),
            )
        }
    }
}

internal fun formatMinutes(minutes: Int): String = when {
    minutes <= 0 -> "0 min"
    minutes < 60 -> "$minutes min"
    else -> {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0) "${h}h" else "${h}h ${m}m"
    }
}
