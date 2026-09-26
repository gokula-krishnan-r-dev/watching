package com.meritscreen.feature.parent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.ui.components.ChildAvatar
import com.meritscreen.core.ui.components.EmptyState
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.components.MeritPullToRefreshBox
import com.meritscreen.core.ui.components.PulsingStatusDot
import com.meritscreen.core.ui.theme.MeritColors

private enum class ChildrenFilterMode(val label: String) {
    ALL("All Children"),
    ACTIVE("Active Now"),
    NEEDS_ATTENTION("Needs Attention"),
}

@Composable
fun ChildrenAnalyticsScreen(
    onOpenChild: (String) -> Unit,
    onAddChild: () -> Unit,
    onOpenReports: (String?) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenHome: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        UiState.Loading -> LoadingState()
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
            ChildrenAnalyticsContent(
                data = current.data,
                onOpenChild = onOpenChild,
                onAddChild = onAddChild,
                onOpenReports = onOpenReports,
                onOpenAccount = onOpenAccount,
                onOpenHome = onOpenHome,
                onTogglePause = viewModel::togglePause,
                onAddBonus = { childId -> viewModel.grantBonus(childId, 15) },
                onRefresh = viewModel::refresh,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChildrenAnalyticsContent(
    data: DashboardUi,
    onOpenChild: (String) -> Unit,
    onAddChild: () -> Unit,
    onOpenReports: (String?) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenHome: () -> Unit,
    onTogglePause: (String) -> Unit,
    onAddBonus: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ChildrenFilterMode.ALL) }

    // Filter children based on search and selected filter mode
    val filteredChildren = remember(data.children, searchQuery, selectedFilter) {
        data.children.filter { child ->
            val matchesQuery = searchQuery.isBlank() ||
                child.profile.displayName.contains(searchQuery, ignoreCase = true) ||
                (child.deviceModel?.contains(searchQuery, ignoreCase = true) == true)

            val matchesFilter = when (selectedFilter) {
                ChildrenFilterMode.ALL -> true
                ChildrenFilterMode.ACTIVE -> child.isDeviceActive
                ChildrenFilterMode.NEEDS_ATTENTION -> child.weakConcepts.isNotEmpty() ||
                    child.remainingMinutes <= 15 ||
                    child.masteryRatePercent < 60
            }

            matchesQuery && matchesFilter
        }
    }

    val totalActiveDevices = remember(data.children) {
        data.children.count { it.isDeviceActive }
    }
    val totalAiMinutes = remember(data.children) {
        data.children.sumOf { it.aiTrainingMinutes }
    }
    val avgMastery = remember(data.children) {
        if (data.children.isNotEmpty()) {
            data.children.sumOf { it.masteryRatePercent } / data.children.size
        } else 0
    }

    Scaffold(
        topBar = {
            ChildrenHeaderTopBar(
                childrenCount = data.children.size,
                activeDevicesCount = totalActiveDevices,
                onAddChild = onAddChild,
                onRefresh = onRefresh,
            )
        },
        bottomBar = {
            ParentBottomNavBar(
                selectedTab = ParentNavTab.CHILDREN,
                childrenCount = data.children.size,
                onOpenHome = onOpenHome,
                onOpenChildren = { /* Already here */ },
                onOpenReports = {
                    val targetId = data.children.firstOrNull()?.profile?.childId
                    onOpenReports(targetId)
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Family Overview 4-Stat Metric Banner
                    FamilySupervisionRollupBanner(
                        totalChildren = data.children.size,
                        activeDevices = totalActiveDevices,
                        totalAiMinutes = totalAiMinutes,
                        avgMasteryPercent = avgMastery,
                    )
                }

                item {
                    // Search & Filter Bar
                    ChildrenSearchAndFilterSection(
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        selectedFilter = selectedFilter,
                        onFilterSelected = { selectedFilter = it },
                        totalCount = data.children.size,
                        activeCount = totalActiveDevices,
                        needsAttentionCount = data.children.count {
                            it.weakConcepts.isNotEmpty() || it.remainingMinutes <= 15
                        },
                    )
                }

                if (filteredChildren.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Face,
                                    contentDescription = null,
                                    tint = MeritColors.OnSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp),
                                )
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No child matching \"$searchQuery\"" else "No children in this filter",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                                if (searchQuery.isNotBlank()) {
                                    OutlinedButton(onClick = { searchQuery = "" }) {
                                        Text("Clear search")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filteredChildren, key = { it.profile.childId }) { childCard ->
                        ChildAnalyticsOverviewCard(
                            child = childCard,
                            onOpenChild = { onOpenChild(childCard.profile.childId) },
                            onOpenReports = { onOpenReports(childCard.profile.childId) },
                            onTogglePause = { onTogglePause(childCard.profile.childId) },
                            onAddBonus = { onAddBonus(childCard.profile.childId) },
                        )
                    }
                }

                item {
                    // Add Child Action Card
                    AddChildPromptCard(onAddChild = onAddChild)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 1. TOP APP BAR
// ---------------------------------------------------------------------------

@Composable
private fun ChildrenHeaderTopBar(
    childrenCount: Int,
    activeDevicesCount: Int,
    onAddChild: () -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(
        color = MeritColors.SurfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "WATCHING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 10.sp,
                        ),
                        color = MeritColors.Primary,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MeritColors.Primary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "$childrenCount ${if (childrenCount == 1) "Child" else "Children"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                            ),
                            color = MeritColors.Primary,
                        )
                    }
                }
                Text(
                    text = "Children & Analytics",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = MeritColors.OnSurface,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MeritColors.SurfaceContainerLowest),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MeritColors.OnSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MeritColors.Primary,
                    modifier = Modifier
                        .height(36.dp)
                        .clickable(onClick = onAddChild),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            tint = MeritColors.OnPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnPrimary,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. FAMILY SUPERVISION ROLLUP BANNER (4 STATS)
// ---------------------------------------------------------------------------

@Composable
private fun FamilySupervisionRollupBanner(
    totalChildren: Int,
    activeDevices: Int,
    totalAiMinutes: Int,
    avgMasteryPercent: Int,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Household Supervision Overview",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )
                }
                Text(
                    text = "Live Sync",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeritColors.Primary,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatPillCard(
                    title = "Kids",
                    value = "$totalChildren",
                    subtitle = if (totalChildren == 1) "Profile" else "Profiles",
                    icon = Icons.Rounded.Face,
                    tint = MeritColors.Primary,
                    modifier = Modifier.weight(1f),
                )
                StatPillCard(
                    title = "Live",
                    value = "$activeDevices",
                    subtitle = "Online",
                    icon = Icons.Default.Smartphone,
                    tint = if (activeDevices > 0) Color(0xFF10B981) else MeritColors.OnSurfaceVariant,
                    hasPulse = activeDevices > 0,
                    modifier = Modifier.weight(1f),
                )
                StatPillCard(
                    title = "AI Time",
                    value = "${totalAiMinutes}m",
                    subtitle = "Today",
                    icon = Icons.Rounded.AutoStories,
                    tint = MeritColors.Tertiary,
                    modifier = Modifier.weight(1f),
                )
                StatPillCard(
                    title = "Score",
                    value = "$avgMasteryPercent%",
                    subtitle = "Quiz rate",
                    icon = Icons.Default.MilitaryTech,
                    tint = MeritColors.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatPillCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    hasPulse: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MeritColors.SurfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (hasPulse) {
                    PulsingStatusDot(color = tint, dotSize = 6.dp)
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MeritColors.OnSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
                color = MeritColors.OnSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MeritColors.OnSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. SEARCH & FILTER SECTION
// ---------------------------------------------------------------------------

@Composable
private fun ChildrenSearchAndFilterSection(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: ChildrenFilterMode,
    onFilterSelected: (ChildrenFilterMode) -> Unit,
    totalCount: Int,
    activeCount: Int,
    needsAttentionCount: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search child by name or device...", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MeritColors.OnSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MeritColors.OnSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MeritColors.SurfaceContainerLowest,
                unfocusedContainerColor = MeritColors.SurfaceContainerLowest,
                focusedBorderColor = MeritColors.Primary,
                unfocusedBorderColor = MeritColors.OutlineVariant.copy(alpha = 0.4f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChipItem(
                label = "All ($totalCount)",
                selected = selectedFilter == ChildrenFilterMode.ALL,
                onClick = { onFilterSelected(ChildrenFilterMode.ALL) },
                modifier = Modifier.weight(1f),
            )
            FilterChipItem(
                label = "Active ($activeCount)",
                selected = selectedFilter == ChildrenFilterMode.ACTIVE,
                onClick = { onFilterSelected(ChildrenFilterMode.ACTIVE) },
                modifier = Modifier.weight(1f),
            )
            FilterChipItem(
                label = "Attention ($needsAttentionCount)",
                selected = selectedFilter == ChildrenFilterMode.NEEDS_ATTENTION,
                onClick = { onFilterSelected(ChildrenFilterMode.NEEDS_ATTENTION) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MeritColors.Primary else MeritColors.SurfaceContainerLowest,
        animationSpec = tween(180),
        label = "chip_bg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MeritColors.OnPrimary else MeritColors.OnSurfaceVariant,
        animationSpec = tween(180),
        label = "chip_text",
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        border = BorderStroke(
            1.dp,
            if (selected) MeritColors.Primary else MeritColors.OutlineVariant.copy(alpha = 0.35f),
        ),
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                ),
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 4. DETAILED CHILD ANALYTICS CARD
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChildAnalyticsOverviewCard(
    child: DashboardChildCard,
    onOpenChild: () -> Unit,
    onOpenReports: () -> Unit,
    onTogglePause: () -> Unit,
    onAddBonus: () -> Unit,
) {
    val usedFraction = if (child.dailyCeilingMinutes > 0) {
        (child.todayMinutes.toFloat() / child.dailyCeilingMinutes).coerceIn(0f, 1f)
    } else 0f

    val progressColor = when {
        usedFraction > 0.90f -> Color(0xFFEF4444) // Red alert
        usedFraction > 0.70f -> Color(0xFFF59E0B) // Amber
        else -> MeritColors.Primary // Teal / brand
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Child Identity & Device Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box {
                        ChildAvatar(
                            avatar = child.profile.avatar,
                            size = 46.dp,
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 2.dp, y = 2.dp),
                        ) {
                            if (child.isDeviceActive) {
                                PulsingStatusDot(color = Color(0xFF10B981), dotSize = 10.dp)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MeritColors.OnSurfaceVariant.copy(alpha = 0.4f))
                                        .border(1.5.dp, Color.White, CircleShape),
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = child.profile.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                ),
                                color = MeritColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MeritColors.SurfaceContainerLow,
                            ) {
                                Text(
                                    text = "Age ${child.profile.ageBand.displayLabel}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MeritColors.OnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Device Status Line
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = MeritColors.OnSurfaceVariant,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = child.deviceModel ?: "Protected Device",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MeritColors.OnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(text = "•", color = MeritColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 10.sp)
                            Text(
                                text = "${child.batteryPercent ?: 95}%",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MeritColors.OnSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(text = "•", color = MeritColors.OnSurfaceVariant.copy(alpha = 0.5f), fontSize = 10.sp)
                            Text(
                                text = if (child.isDeviceActive) "Active" else "Offline",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = if (child.isDeviceActive) Color(0xFF10B981) else MeritColors.OnSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Quick Pause/Resume Toggle
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (child.isPaused) MeritColors.Primary.copy(alpha = 0.12f) else MeritColors.SurfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (child.isPaused) MeritColors.Primary else MeritColors.OutlineVariant.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier.clickable(onClick = onTogglePause),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (child.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (child.isPaused) "Resume" else "Pause",
                            tint = if (child.isPaused) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (child.isPaused) "Resume" else "Pause",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            ),
                            color = if (child.isPaused) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MeritColors.OutlineVariant.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(12.dp))

            // Screen Time Progress Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Daily Screen Balance",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${child.todayMinutes}m",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            ),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "/ ${child.dailyCeilingMinutes}m limit",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MeritColors.OnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 1.dp),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (child.remainingMinutes <= 15) Color(0xFFFEE2E2) else MeritColors.SurfaceContainerLow,
                    ) {
                        Text(
                            text = "${child.remainingMinutes}m left",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            ),
                            color = if (child.remainingMinutes <= 15) Color(0xFFDC2626) else MeritColors.Primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    // +15m Bonus Button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MeritColors.Primary.copy(alpha = 0.12f),
                        modifier = Modifier.clickable(onClick = onAddBonus),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "15m Bonus",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                ),
                                color = MeritColors.Primary,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { usedFraction },
                color = progressColor,
                trackColor = MeritColors.SurfaceContainerLow,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // AI Education & Performance Section
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
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
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "AI Adaptive Learning Progress",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MeritColors.OnSurface,
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MeritColors.SurfaceContainerLowest,
                        ) {
                            Text(
                                text = child.levelLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                ),
                                color = MeritColors.Primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3 KPI Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MiniKpiTile(
                            label = "AI Training",
                            value = "${child.aiTrainingMinutes} min",
                            modifier = Modifier.weight(1f),
                        )
                        MiniKpiTile(
                            label = "Mastery Rate",
                            value = "${child.masteryRatePercent}%",
                            modifier = Modifier.weight(1f),
                        )
                        MiniKpiTile(
                            label = "Streak",
                            value = "${child.streakDays} Days 🔥",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Weak Concepts / Recommended Review
                    if (child.weakConcepts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "Recommended Review: ${child.weakConcepts.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = Color(0xFFB45309),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions Row: Full Reports & Manage Rules
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenReports,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MeritColors.OnSurface),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Insights,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MeritColors.Primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Full Analytics",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                        ),
                    )
                }

                Button(
                    onClick = onOpenChild,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeritColors.Primary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = MeritColors.OnPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Manage Profile",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = MeritColors.OnPrimary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniKpiTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MeritColors.SurfaceContainerLowest,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = MeritColors.OnSurfaceVariant,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                ),
                color = MeritColors.OnSurface,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 5. ADD CHILD PROMPT CARD
// ---------------------------------------------------------------------------

@Composable
private fun AddChildPromptCard(onAddChild: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLowest,
        border = BorderStroke(1.dp, MeritColors.Primary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddChild),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MeritColors.Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Add another child profile",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = "Pair another tablet or phone to set educational AI and bedtime schedules",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MeritColors.OnSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
