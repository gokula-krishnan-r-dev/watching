package com.meritscreen.feature.parent.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.core.common.error.AppErrorMapper
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.components.MeritPrimaryButton
import com.meritscreen.core.ui.components.MeritPullToRefreshBox
import com.meritscreen.core.ui.components.MeritSecondaryButton
import com.meritscreen.core.ui.components.SyncedAppIcon
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AllowlistUi(
    val rules: List<AppRule>,
    val installedApps: List<InstalledAppSummary> = emptyList(),
    val inventoryReady: Boolean = false,
    val packageInput: String = "",
    val nameInput: String = "",
    val formError: String? = null,
    val isRefreshing: Boolean = false,
) {
    /** Installed apps not already covered by a rule — what the picker should offer. */
    val pickableApps: List<InstalledAppSummary>
        get() {
            val existing = rules.map { it.packageOrBundleId.lowercase() }.toSet()
            return installedApps.filter { it.packageName.lowercase() !in existing }
        }
}

@HiltViewModel
class AllowlistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val parentSessionRepository: ParentSessionRepository,
    private val parentControlStore: ParentControlStore,
) : ViewModel() {

    private val childId = savedStateHandle.toRoute<AllowlistRoute>().childId

    private val _uiState = MutableStateFlow<UiState<AllowlistUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<AllowlistUi>> = _uiState.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private var inventoryJob: Job? = null

    init {
        refresh()
        observeInventory()
    }

    fun refresh() {
        viewModelScope.launch {
            val previous = (_uiState.value as? UiState.Success)?.data
            if (previous != null) {
                _uiState.value = UiState.Success(previous.copy(isRefreshing = true))
            }
            try {
                val familyId = parentSessionRepository.current()?.familyId ?: return@launch
                val rules = parentControlStore.listAppRules(familyId, childId)
                val installed = runCatching {
                    parentControlStore.listInstalledApps(familyId, childId)
                }.getOrDefault(emptyList())
                val prev = (_uiState.value as? UiState.Success)?.data
                _uiState.value = UiState.Success(
                    AllowlistUi(
                        rules = rules,
                        installedApps = installed,
                        inventoryReady = prev?.inventoryReady == true || installed.isNotEmpty(),
                        packageInput = prev?.packageInput.orEmpty(),
                        nameInput = prev?.nameInput.orEmpty(),
                        isRefreshing = false,
                    ),
                )
            } catch (error: Throwable) {
                if (previous == null) {
                    _uiState.value = UiState.Error(AppErrorMapper.from(error))
                } else {
                    _uiState.value = UiState.Success(previous.copy(isRefreshing = false))
                }
            }
        }
    }

    private fun observeInventory() {
        inventoryJob?.cancel()
        inventoryJob = viewModelScope.launch {
            val familyId = parentSessionRepository.current()?.familyId ?: return@launch
            parentControlStore.observeInstalledApps(familyId, childId)
                .catch { /* keep last one-shot list; manual refresh still works */ }
                .collect { installed ->
                    _uiState.update { state ->
                        when (state) {
                            is UiState.Success -> state.copy(
                                data = state.data.copy(
                                    installedApps = installed,
                                    inventoryReady = state.data.inventoryReady || installed.isNotEmpty(),
                                ),
                            )
                            else -> state
                        }
                    }
                }
        }
    }

    fun addFromInstalled(app: InstalledAppSummary) {
        toggleInstalled(app, allowed = true)
    }

    /** Toggle allow/deny for an installed package (creates a rule if missing). */
    fun toggleInstalled(app: InstalledAppSummary, allowed: Boolean) {
        viewModelScope.launch {
            _saving.value = true
            try {
                val familyId = parentSessionRepository.current()?.familyId ?: return@launch
                val existing = (_uiState.value as? UiState.Success)?.data?.rules
                    ?.firstOrNull { it.packageOrBundleId.equals(app.packageName, ignoreCase = true) }
                parentControlStore.upsertAppRule(
                    familyId,
                    childId,
                    (existing ?: AppRule(
                        appId = app.packageName.replace('.', '_'),
                        packageOrBundleId = app.packageName,
                        displayName = app.label,
                        allowed = allowed,
                        blockMinutes = AppConfig.DEFAULT_BLOCK_MINUTES,
                        grantOnPassMinutes = AppConfig.DEFAULT_BLOCK_MINUTES,
                        cooldownMinutes = AppConfig.DEFAULT_COOLDOWN_MINUTES,
                    )).copy(
                        allowed = allowed,
                        displayName = existing?.displayName?.ifBlank { app.label } ?: app.label,
                    ),
                )
                refresh()
            } catch (error: Throwable) {
                updateForm { it.copy(formError = AppErrorMapper.from(error).userMessage) }
            } finally {
                _saving.value = false
            }
        }
    }

    fun onPackageChanged(value: String) = updateForm { it.copy(packageInput = value, formError = null) }
    fun onNameChanged(value: String) = updateForm { it.copy(nameInput = value) }

    fun addManualApp() {
        val current = (_uiState.value as? UiState.Success)?.data ?: return
        val pkg = current.packageInput.trim()
        if (pkg.length < 3 || !pkg.contains('.')) {
            updateForm {
                it.copy(formError = "Enter a valid app package identifier (such as com.example.app).")
            }
            return
        }
        viewModelScope.launch {
            _saving.value = true
            try {
                val familyId = parentSessionRepository.current()?.familyId ?: return@launch
                parentControlStore.upsertAppRule(
                    familyId,
                    childId,
                    AppRule(
                        appId = pkg.replace('.', '_'),
                        packageOrBundleId = pkg,
                        displayName = current.nameInput.ifBlank { pkg.substringAfterLast('.') },
                        allowed = true,
                        blockMinutes = AppConfig.DEFAULT_BLOCK_MINUTES,
                        grantOnPassMinutes = AppConfig.DEFAULT_BLOCK_MINUTES,
                        cooldownMinutes = AppConfig.DEFAULT_COOLDOWN_MINUTES,
                    ),
                )
                _uiState.update { state ->
                    if (state is UiState.Success) {
                        state.copy(data = state.data.copy(packageInput = "", nameInput = "", formError = null))
                    } else {
                        state
                    }
                }
                refresh()
            } catch (error: Throwable) {
                updateForm { it.copy(formError = AppErrorMapper.from(error).userMessage) }
            } finally {
                _saving.value = false
            }
        }
    }

    fun toggleAllowed(rule: AppRule, allowed: Boolean) {
        viewModelScope.launch {
            val familyId = parentSessionRepository.current()?.familyId ?: return@launch
            parentControlStore.upsertAppRule(familyId, childId, rule.copy(allowed = allowed))
            refresh()
        }
    }

    fun remove(rule: AppRule) {
        viewModelScope.launch {
            val familyId = parentSessionRepository.current()?.familyId ?: return@launch
            parentControlStore.deleteAppRule(familyId, childId, rule.appId)
            refresh()
        }
    }

    fun updateBlockMinutes(rule: AppRule, minutes: Int) {
        viewModelScope.launch {
            val familyId = parentSessionRepository.current()?.familyId ?: return@launch
            parentControlStore.upsertAppRule(familyId, childId, rule.copy(blockMinutes = minutes))
            refresh()
        }
    }

    private fun updateForm(transform: (AllowlistUi) -> AllowlistUi) {
        _uiState.update { state ->
            if (state is UiState.Success) state.copy(data = transform(state.data)) else state
        }
    }
}

enum class AllowlistFilter {
    ALL,
    ALLOWED,
    BLOCKED,
    EMERGENCY,
}

@Composable
fun AllowlistScreen(
    onBack: () -> Unit,
    viewModel: AllowlistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    when (val current = state) {
        UiState.Loading -> LoadingState()
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(message = current.error.userMessage, onRetry = viewModel::refresh)
        is UiState.Success -> {
            AllowlistContent(
                uiState = current.data,
                saving = saving,
                onBack = onBack,
                onToggleAllowed = viewModel::toggleAllowed,
                onUpdateBlockMinutes = viewModel::updateBlockMinutes,
                onRemoveRule = viewModel::remove,
                onAddFromInstalled = viewModel::addFromInstalled,
                onToggleInstalled = viewModel::toggleInstalled,
                onPackageChanged = viewModel::onPackageChanged,
                onNameChanged = viewModel::onNameChanged,
                onAddManualApp = viewModel::addManualApp,
                onRefresh = viewModel::refresh,
            )
        }
    }
}

@Composable
fun AllowlistContent(
    modifier: Modifier = Modifier,
    uiState: AllowlistUi,
    saving: Boolean = false,
    childName: String = "Child's Device",
    onBack: () -> Unit = {},
    onToggleAllowed: (AppRule, Boolean) -> Unit = { _, _ -> },
    onUpdateBlockMinutes: (AppRule, Int) -> Unit = { _, _ -> },
    onRemoveRule: (AppRule) -> Unit = {},
    onAddFromInstalled: (InstalledAppSummary) -> Unit = {},
    onToggleInstalled: (InstalledAppSummary, Boolean) -> Unit = { _, _ -> },
    onPackageChanged: (String) -> Unit = {},
    onNameChanged: (String) -> Unit = {},
    onAddManualApp: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    var selectedFilter by remember { mutableStateOf(AllowlistFilter.ALL) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showBanner by remember { mutableStateOf(true) }

    val allRules = uiState.rules
    val allowedCount = allRules.count { it.allowed }
    val blockedCount = allRules.count { !it.allowed }
    val emergencyCount = 1 // Phone & Contacts emergency override

    val filteredRules = when (selectedFilter) {
        AllowlistFilter.ALL -> allRules
        AllowlistFilter.ALLOWED -> allRules.filter { it.allowed }
        AllowlistFilter.BLOCKED -> allRules.filter { !it.allowed }
        AllowlistFilter.EMERGENCY -> emptyList()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MeritColors.SurfaceContainerLow,
    ) {
        MeritPullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MeritSpacing.lg, vertical = MeritSpacing.xs),
            ) {
            // Top App Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MeritColors.OnSurface,
                        )
                    }
                    Column {
                        Text(
                            text = "Approved Apps & Rules",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "Set up app access and learning quiz rules",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MeritColors.SecondaryContainer)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MeritColors.OnSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(MeritSpacing.sm))

            // Educational Banner: Quiz-to-Unlock Protection
            AnimatedVisibility(visible = showBanner) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MeritColors.PrimaryFixed,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(MeritSpacing.md),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MeritColors.SurfaceContainerLowest.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quiz-to-Unlock Protection",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MeritColors.Primary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "When an app time block expires, your child completes an adaptive micro-quiz to unlock bonus time. Emergency apps stay unlocked at all times.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { showBanner = false },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MeritColors.OnSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.md))

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AllowlistFilterChip(
                    text = "All Apps (${allRules.size + emergencyCount})",
                    selected = selectedFilter == AllowlistFilter.ALL,
                    onClick = { selectedFilter = AllowlistFilter.ALL },
                )
                AllowlistFilterChip(
                    text = "Allowed ($allowedCount)",
                    selected = selectedFilter == AllowlistFilter.ALLOWED,
                    onClick = { selectedFilter = AllowlistFilter.ALLOWED },
                )
                AllowlistFilterChip(
                    text = "Blocked ($blockedCount)",
                    selected = selectedFilter == AllowlistFilter.BLOCKED,
                    onClick = { selectedFilter = AllowlistFilter.BLOCKED },
                )
                AllowlistFilterChip(
                    text = "Emergency ($emergencyCount)",
                    selected = selectedFilter == AllowlistFilter.EMERGENCY,
                    isEmergency = true,
                    onClick = { selectedFilter = AllowlistFilter.EMERGENCY },
                )
            }

            Spacer(Modifier.height(MeritSpacing.md))

            // Emergency Whitelisted Card (Shown on ALL and EMERGENCY filters)
            if (selectedFilter == AllowlistFilter.ALL || selectedFilter == AllowlistFilter.EMERGENCY) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MeritColors.SurfaceContainerLowest,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(MeritSpacing.md),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MeritColors.TertiaryFixed),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = MeritColors.OnTertiaryFixed,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "Phone & Contacts",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.OnSurface,
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MeritColors.TertiaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "Emergency Override",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MeritColors.OnTertiary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Always accessible even during bedtime lock, zero tokens, or failed quizzes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = MeritColors.Tertiary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "Safety Whitelisted (Emergency calls & messages pinned)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MeritColors.Tertiary,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(MeritSpacing.sm))
            }

            // App Rules Cards
            if (filteredRules.isEmpty() && selectedFilter != AllowlistFilter.EMERGENCY) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MeritColors.SurfaceContainerLowest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MeritSpacing.md),
                ) {
                    Column(
                        modifier = Modifier.padding(MeritSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "No apps in this category",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MeritColors.OnSurfaceVariant,
                        )
                        Spacer(Modifier.height(MeritSpacing.sm))
                        MeritSecondaryButton(
                            text = "Show All Apps",
                            onClick = { selectedFilter = AllowlistFilter.ALL },
                        )
                    }
                }
            } else {
                filteredRules.forEach { rule ->
                    val installed = uiState.installedApps.firstOrNull {
                        it.packageName.equals(rule.packageOrBundleId, ignoreCase = true)
                    }
                    AppRuleCard(
                        rule = rule,
                        iconBase64 = installed?.iconBase64,
                        iconHash = installed?.iconHash,
                        onToggleAllowed = { allowed -> onToggleAllowed(rule, allowed) },
                        onUpdateBlockMinutes = { minutes -> onUpdateBlockMinutes(rule, minutes) },
                        onRemove = { onRemoveRule(rule) },
                    )
                    Spacer(Modifier.height(MeritSpacing.sm))
                }
            }

            // Live inventory from child's device (screen-scoped Firestore listener)
            if (uiState.installedApps.isNotEmpty()) {
                Spacer(Modifier.height(MeritSpacing.md))
                Text(
                    text = "On Child's Device (${uiState.installedApps.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Spacer(Modifier.height(MeritSpacing.xs))
                if (!uiState.inventoryReady && uiState.installedApps.isEmpty()) {
                    Text(
                        text = "Waiting for the child device to upload installed apps…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
                val allowedPackages = uiState.rules
                    .filter { it.allowed }
                    .map { it.packageOrBundleId.lowercase() }
                    .toSet()
                uiState.installedApps.forEach { app ->
                    val isAllowed = app.packageName.lowercase() in allowedPackages
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MeritColors.SurfaceContainerLowest,
                        shadowElevation = 0.5.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = MeritSpacing.md, vertical = MeritSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                            ) {
                                SyncedAppIcon(
                                    packageName = app.packageName,
                                    iconBase64 = app.iconBase64,
                                    iconHash = app.iconHash,
                                    size = 40.dp,
                                    cornerRadius = 12.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MeritColors.OnSurface,
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MeritColors.OnSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = isAllowed,
                                onCheckedChange = { checked -> onToggleInstalled(app, checked) },
                                enabled = !saving,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MeritColors.Primary,
                                ),
                            )
                        }
                    }
                }
            } else if (uiState.inventoryReady.not()) {
                Spacer(Modifier.height(MeritSpacing.md))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MeritColors.SurfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(MeritSpacing.md)) {
                        Text(
                            text = "Waiting for child device inventory",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "Apps appear here automatically after the paired device uploads its list. Pull refresh if needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                        Spacer(Modifier.height(MeritSpacing.sm))
                        MeritSecondaryButton(text = "Refresh", onClick = onRefresh)
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.lg))

            // "+ Add App from Device or Store" Button
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MeritColors.Primary,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable { showAddDialog = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MeritColors.OnPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Add App from Device or Store",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnPrimary,
                    )
                }
            }

            Spacer(Modifier.height(MeritSpacing.xl))
        }
    }

        // Add App Manual Dialog
        if (showAddDialog) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MeritColors.SurfaceContainerLowest,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MeritSpacing.lg),
            ) {
                Column(
                    modifier = Modifier.padding(MeritSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Add App Rule",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        IconButton(onClick = { showAddDialog = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = "Enter the app name and package identifier to set up customized learning rules.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = uiState.nameInput,
                        onValueChange = onNameChanged,
                        label = { Text("App Name") },
                        placeholder = { Text("Enter app name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.packageInput,
                        onValueChange = onPackageChanged,
                        label = { Text("App Package Identifier") },
                        placeholder = { Text("Enter package identifier (e.g. com.example.app)") },
                        singleLine = true,
                        isError = uiState.formError != null,
                        supportingText = uiState.formError?.let { { Text(it, color = MeritColors.Error) } },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    MeritPrimaryButton(
                        text = "Add App",
                        onClick = {
                            onAddManualApp()
                            if (uiState.formError == null && uiState.packageInput.isNotBlank()) {
                                showAddDialog = false
                            }
                        },
                        loading = saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AllowlistFilterChip(
    text: String,
    selected: Boolean,
    isEmergency: Boolean = false,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) {
        MeritColors.PrimaryContainer
    } else {
        MeritColors.SurfaceContainerHigh
    }
    val contentColor = if (selected) {
        MeritColors.OnPrimaryContainer
    } else {
        MeritColors.OnSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isEmergency) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MeritColors.Tertiary),
                )
            } else if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = contentColor,
            )
        }
    }
}

@Composable
private fun AppRuleCard(
    rule: AppRule,
    iconBase64: String? = null,
    iconHash: String? = null,
    onToggleAllowed: (Boolean) -> Unit,
    onUpdateBlockMinutes: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val fallbackIcon: ImageVector = when {
        rule.displayName.contains("Khan", ignoreCase = true) || rule.packageOrBundleId.contains("khan", ignoreCase = true) -> Icons.Default.School
        rule.displayName.contains("YouTube", ignoreCase = true) || rule.packageOrBundleId.contains("youtube", ignoreCase = true) -> Icons.Default.SmartDisplay
        rule.displayName.contains("Duo", ignoreCase = true) || rule.packageOrBundleId.contains("duo", ignoreCase = true) -> Icons.Default.Spellcheck
        rule.displayName.contains("Roblox", ignoreCase = true) || rule.packageOrBundleId.contains("game", ignoreCase = true) -> Icons.Default.SportsEsports
        else -> Icons.Default.Apps
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MeritSpacing.md),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
            ) {
                SyncedAppIcon(
                    packageName = rule.packageOrBundleId,
                    iconBase64 = iconBase64,
                    iconHash = iconHash,
                    size = 48.dp,
                    cornerRadius = 12.dp,
                    fallbackIcon = fallbackIcon,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = rule.displayName.ifBlank { rule.packageOrBundleId.substringAfterLast('.') },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (rule.allowed) MeritColors.TertiaryFixedDim.copy(alpha = 0.4f) else MeritColors.SurfaceContainerHighest,
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = if (rule.allowed) "Active Rule" else "Blocked",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (rule.allowed) MeritColors.OnTertiaryFixedVariant else MeritColors.OnSurfaceVariant,
                            )
                        }
                    }

                    Text(
                        text = if (rule.allowed) {
                            "Complete 3-question quiz • ${rule.cooldownMinutes}m rest break if failed"
                        } else {
                            "Blocked • Complete a learning quiz to unlock"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )

                    Spacer(Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MeritColors.SurfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MeritColors.Primary,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "${rule.blockMinutes}m limit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.OnSurface,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MeritColors.PrimaryFixed.copy(alpha = 0.6f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircle,
                                    contentDescription = null,
                                    tint = MeritColors.Primary,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "+${rule.grantOnPassMinutes}m grant",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeritColors.Primary,
                                )
                            }
                        }
                    }
                }

                Switch(
                    checked = rule.allowed,
                    onCheckedChange = onToggleAllowed,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MeritColors.OnPrimary,
                        checkedTrackColor = MeritColors.PrimaryContainer,
                        uncheckedThumbColor = MeritColors.Outline,
                        uncheckedTrackColor = MeritColors.SurfaceContainerHighest,
                    ),
                )
            }

            // Quick Rule Config Drawer inside card
            if (rule.allowed) {
                Surface(
                    color = MeritColors.SurfaceContainerLow.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = MeritSpacing.md, vertical = MeritSpacing.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Session Block Length:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MeritColors.OnSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${rule.blockMinutes} Minutes",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MeritColors.Primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove rule",
                                        tint = MeritColors.OnSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                val isSelected = rule.blockMinutes == mins
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MeritColors.Primary else MeritColors.SurfaceContainerLowest,
                                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onUpdateBlockMinutes(mins) },
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "${mins}m",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            ),
                                            color = if (isSelected) MeritColors.OnPrimary else MeritColors.OnSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
