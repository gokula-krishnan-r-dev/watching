package com.meritscreen.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.components.SyncedAppIcon
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import kotlinx.serialization.Serializable

@Serializable
data class OnboardingAllowlistRoute(
    val childId: String = "",
)

enum class AppCategoryFilter {
    ALL,
    EDUCATIONAL,
    ENTERTAINMENT,
    SYSTEM_LOCKED,
}

@Composable
fun OnboardingAllowlistScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingAllowlistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        UiState.Loading -> LoadingState(message = "Loading apps from child's device…")
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(
            message = current.error.userMessage,
            onRetry = viewModel::refresh,
        )
        is UiState.Success -> OnboardingAllowlistContent(
            ui = current.data,
            onBack = onBack,
            onContinue = onContinue,
            onRefresh = viewModel::refresh,
            onSearchChanged = viewModel::onSearchChanged,
            onToggle = viewModel::toggleAllowed,
            onApplyPreset = viewModel::applyPreset,
            onGlobalLimitChanged = viewModel::setGlobalLimitEnabled,
            modifier = modifier,
        )
    }
}

@Composable
fun OnboardingAllowlistContent(
    ui: OnboardingAllowlistUi,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onRefresh: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onToggle: (packageName: String, allowed: Boolean) -> Unit,
    onApplyPreset: (String) -> Unit,
    onGlobalLimitChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf(AppCategoryFilter.ALL) }
    var searchOpen by remember { mutableStateOf(false) }

    val query = ui.searchQuery.trim()
    val filteredApps = ui.rows
        .asSequence()
        .filter { row ->
            when (selectedFilter) {
                AppCategoryFilter.ALL -> true
                AppCategoryFilter.EDUCATIONAL -> row.category == AppCategoryFilter.EDUCATIONAL
                AppCategoryFilter.ENTERTAINMENT -> row.category == AppCategoryFilter.ENTERTAINMENT
                AppCategoryFilter.SYSTEM_LOCKED -> row.category == AppCategoryFilter.SYSTEM_LOCKED
            }
        }
        .filter { row ->
            query.isBlank() ||
                row.label.contains(query, ignoreCase = true) ||
                row.packageName.contains(query, ignoreCase = true)
        }
        .toList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeritColors.Surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = MeritSpacing.sm, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MeritColors.OnSurface,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MeritColors.Primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "4",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                    Text(
                        text = "Step 4 of 5 • App Rules",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.Primary,
                    )
                }
            }

            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MeritColors.OnSurfaceVariant,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MeritSpacing.margin)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MeritColors.SurfaceContainer),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(4.dp)
                    .background(MeritColors.Primary),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (searchOpen) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MeritSpacing.margin),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MeritColors.OnSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    BasicTextField(
                        value = ui.searchQuery,
                        onValueChange = onSearchChanged,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MeritColors.OnSurface),
                        cursorBrush = SolidColor(MeritColors.Primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (ui.searchQuery.isBlank()) {
                                Text(
                                    text = "Search ${ui.childName}'s apps",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MeritColors.OnSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MeritSpacing.margin),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                label = "All (${ui.rows.size})",
                isSelected = selectedFilter == AppCategoryFilter.ALL,
                icon = Icons.Default.Apps,
                onClick = { selectedFilter = AppCategoryFilter.ALL },
            )
            FilterChip(
                label = "Educational",
                isSelected = selectedFilter == AppCategoryFilter.EDUCATIONAL,
                icon = Icons.Default.School,
                onClick = { selectedFilter = AppCategoryFilter.EDUCATIONAL },
            )
            FilterChip(
                label = "Entertainment",
                isSelected = selectedFilter == AppCategoryFilter.ENTERTAINMENT,
                icon = Icons.Default.SportsEsports,
                onClick = { selectedFilter = AppCategoryFilter.ENTERTAINMENT },
            )
            FilterChip(
                label = "System Locked",
                isSelected = selectedFilter == AppCategoryFilter.SYSTEM_LOCKED,
                icon = Icons.Default.Lock,
                onClick = { selectedFilter = AppCategoryFilter.SYSTEM_LOCKED },
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = MeritSpacing.margin)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (ui.actionError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MeritColors.ErrorContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = ui.actionError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "QUICK PRESETS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Applied to synced apps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.Outline,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PresetButton(
                            title = "Balanced",
                            subtitle = "Learn first",
                            isSelected = ui.selectedPreset == "Balanced",
                            enabled = ui.rows.isNotEmpty() && !ui.isSaving,
                            modifier = Modifier.weight(1f),
                            onClick = { onApplyPreset("Balanced") },
                        )
                        PresetButton(
                            title = "Strict",
                            subtitle = "School hrs",
                            isSelected = ui.selectedPreset == "Strict",
                            enabled = ui.rows.isNotEmpty() && !ui.isSaving,
                            modifier = Modifier.weight(1f),
                            onClick = { onApplyPreset("Strict") },
                        )
                        PresetButton(
                            title = "Weekend",
                            subtitle = "All open",
                            isSelected = ui.selectedPreset == "Weekend",
                            enabled = ui.rows.isNotEmpty() && !ui.isSaving,
                            modifier = Modifier.weight(1f),
                            onClick = { onApplyPreset("Weekend") },
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MeritColors.SurfaceContainerLowest,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Global Screen-Time Limit",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "Enforce overarching daily cap across all apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = ui.globalLimitEnabled,
                        onCheckedChange = onGlobalLimitChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MeritColors.Primary,
                        ),
                    )
                }
            }

            when {
                ui.waitingForInventory -> {
                    WaitingForInventoryCard(
                        childName = ui.childName,
                        onRefresh = onRefresh,
                    )
                }
                filteredApps.isEmpty() -> {
                    EmptyFilterCard(onClearSearch = { onSearchChanged("") })
                }
                else -> {
                    filteredApps.forEach { app ->
                        AppRuleCard(
                            app = app,
                            enabled = app.packageName !in ui.togglingPackages && !ui.isSaving,
                            onToggle = { checked -> onToggle(app.packageName, checked) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = MeritColors.Surface,
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MeritSpacing.margin, vertical = 16.dp),
            ) {
                Button(
                    onClick = onContinue,
                    enabled = !ui.isSaving,
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeritColors.PrimaryContainer,
                        contentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (ui.isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = "Save Rules & Set PIN",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            ),
                            color = Color.White,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingForInventoryCard(
    childName: String,
    onRefresh: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MeritColors.SurfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeritSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = MeritColors.Primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Waiting for $childName's device",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MeritColors.OnSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Installed apps appear here automatically once the child device finishes pairing and uploads its inventory. Keep both devices online.",
                style = MaterialTheme.typography.bodySmall,
                color = MeritColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onRefresh,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MeritColors.Primary),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Refresh inventory")
            }
        }
    }
}

@Composable
private fun EmptyFilterCard(onClearSearch: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MeritColors.SurfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(MeritSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MeritColors.OnSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No apps match this filter",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurface,
            )
            TextButtonLike(text = "Clear search", onClick = onClearSearch)
        }
    }
}

@Composable
private fun TextButtonLike(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MeritColors.Primary,
        modifier = Modifier
            .padding(top = 8.dp)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MeritColors.Primary else MeritColors.SurfaceContainer,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else MeritColors.OnSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = if (isSelected) Color.White else MeritColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PresetButton(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MeritColors.SurfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = when {
                        !enabled -> MeritColors.Outline
                        isSelected -> MeritColors.Primary
                        else -> MeritColors.OnSurface
                    },
                ),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MeritColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppRuleCard(
    app: AllowlistAppRow,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MeritColors.SurfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                SyncedAppIcon(
                    packageName = app.packageName,
                    iconBase64 = app.iconBase64,
                    iconHash = app.iconHash,
                    size = 46.dp,
                    cornerRadius = 14.dp,
                    fallbackIcon = when (app.category) {
                        AppCategoryFilter.EDUCATIONAL -> Icons.Default.Translate
                        AppCategoryFilter.ENTERTAINMENT -> Icons.Default.SportsEsports
                        AppCategoryFilter.SYSTEM_LOCKED -> Icons.Default.Lock
                        else -> Icons.Default.Apps
                    },
                )

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnSurface,
                        )
                        if (app.isVerifiedSafe) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Safe",
                                tint = MeritColors.Tertiary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    Text(
                        text = app.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }

            if (!enabled && app.packageName.isNotBlank()) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MeritColors.Primary,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Switch(
                    checked = app.isAllowed,
                    onCheckedChange = onToggle,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MeritColors.Primary,
                    ),
                )
            }
        }
    }
}
