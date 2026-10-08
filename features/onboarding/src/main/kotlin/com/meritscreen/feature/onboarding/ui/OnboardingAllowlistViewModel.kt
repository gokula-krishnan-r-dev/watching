package com.meritscreen.feature.onboarding.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AppInventoryCategorizer
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildAllowlistDefaults
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorMapper
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.core.network.NetworkMonitor
import com.meritscreen.feature.onboarding.domain.EnsureFamilyReadyForPairingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AllowlistAppRow(
    val packageName: String,
    val label: String,
    val category: AppCategoryFilter,
    val subtitle: String,
    val isVerifiedSafe: Boolean,
    val isAllowed: Boolean,
    val blockMinutes: Int,
    val iconBase64: String? = null,
    val iconHash: String? = null,
)

data class OnboardingAllowlistUi(
    val childName: String = "your child",
    val rows: List<AllowlistAppRow> = emptyList(),
    val inventoryReady: Boolean = false,
    val waitingForInventory: Boolean = true,
    val selectedPreset: String? = null,
    val globalLimitEnabled: Boolean = true,
    val searchQuery: String = "",
    val isSaving: Boolean = false,
    val togglingPackages: Set<String> = emptySet(),
    val actionError: String? = null,
)

@HiltViewModel
class OnboardingAllowlistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ensureFamilyReady: EnsureFamilyReadyForPairingUseCase,
    private val parentControlStore: ParentControlStore,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val preferredChildId: String =
        savedStateHandle.get<String>("childId").orEmpty()

    private val _uiState = MutableStateFlow<UiState<OnboardingAllowlistUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<OnboardingAllowlistUi>> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""
    private var rulesByPackage: Map<String, AppRule> = emptyMap()
    private var installedApps: List<InstalledAppSummary> = emptyList()
    private var inventoryJob: Job? = null
    private var policyJob: Job? = null
    private var seedJob: Job? = null

    init {
        bootstrap()
    }

    fun refresh() {
        viewModelScope.launch { loadRulesAndInventory(showLoading = installedApps.isEmpty()) }
    }

    fun onSearchChanged(query: String) {
        updateSuccess { it.copy(searchQuery = query, actionError = null) }
    }

    fun toggleAllowed(packageName: String, allowed: Boolean) {
        val row = currentUi()?.rows?.firstOrNull { it.packageName == packageName } ?: return
        optimisticToggle(packageName, allowed)
        viewModelScope.launch {
            markToggling(packageName, true)
            try {
                ensureIds()
                val existing = rulesByPackage[packageName.lowercase()]
                val rule = (existing ?: defaultRule(row)).copy(allowed = allowed)
                parentControlStore.upsertAppRule(familyId, childId, rule)
                rulesByPackage = rulesByPackage + (packageName.lowercase() to rule)
                publishRows()
            } catch (error: Throwable) {
                optimisticToggle(packageName, !allowed)
                updateSuccess {
                    it.copy(actionError = AppErrorMapper.from(error).userMessage)
                }
            } finally {
                markToggling(packageName, false)
            }
        }
    }

    fun applyPreset(preset: String) {
        val rows = currentUi()?.rows.orEmpty()
        if (rows.isEmpty()) return
        updateSuccess { it.copy(selectedPreset = preset, isSaving = true, actionError = null) }
        viewModelScope.launch {
            try {
                ensureIds()
                val updates = rows.map { row ->
                    val allowed = when (preset) {
                        "Strict" -> row.category == AppCategoryFilter.EDUCATIONAL ||
                            row.category == AppCategoryFilter.SYSTEM_LOCKED
                        "Weekend" -> true
                        else -> row.category != AppCategoryFilter.ENTERTAINMENT // Balanced
                    }
                    row to allowed
                }
                // Optimistic batch paint
                updateSuccess { ui ->
                    ui.copy(
                        rows = ui.rows.map { row ->
                            val match = updates.firstOrNull { it.first.packageName == row.packageName }
                            if (match != null) row.copy(isAllowed = match.second) else row
                        },
                        selectedPreset = preset,
                    )
                }
                updates.forEach { (row, allowed) ->
                    val existing = rulesByPackage[row.packageName.lowercase()]
                    val rule = (existing ?: defaultRule(row)).copy(allowed = allowed)
                    parentControlStore.upsertAppRule(familyId, childId, rule)
                    rulesByPackage = rulesByPackage + (row.packageName.lowercase() to rule)
                }
                publishRows()
            } catch (error: Throwable) {
                updateSuccess {
                    it.copy(actionError = AppErrorMapper.from(error).userMessage)
                }
                loadRulesAndInventory(showLoading = false)
            } finally {
                updateSuccess { it.copy(isSaving = false) }
            }
        }
    }

    fun setGlobalLimitEnabled(enabled: Boolean) {
        updateSuccess { it.copy(globalLimitEnabled = enabled, actionError = null) }
        viewModelScope.launch {
            try {
                ensureIds()
                val policy = parentControlStore.getPolicy(familyId, childId)
                val updated = if (enabled) {
                    policy.copy(dailyCeilingMinutes = policy.dailyCeilingMinutes ?: 120)
                } else {
                    policy.copy(dailyCeilingMinutes = null)
                }
                parentControlStore.updatePolicy(familyId, childId, updated)
            } catch (error: Throwable) {
                updateSuccess {
                    it.copy(
                        globalLimitEnabled = !enabled,
                        actionError = AppErrorMapper.from(error).userMessage,
                    )
                }
            }
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            if (!networkMonitor.isCurrentlyOnline()) {
                _uiState.value = UiState.Error(AppError.Network())
                return@launch
            }
            try {
                val context = ensureFamilyReady(preferredChildId.takeIf { it.isNotBlank() })
                familyId = context.familyId
                childId = context.childId
                _uiState.value = UiState.Success(
                    OnboardingAllowlistUi(
                        childName = context.childName,
                        waitingForInventory = true,
                    ),
                )
                loadRulesAndInventory(showLoading = false)
                observeInventory()
                loadGlobalLimit()
            } catch (error: Throwable) {
                _uiState.value = UiState.Error(AppErrorMapper.from(error))
            }
        }
    }

    private suspend fun loadRulesAndInventory(showLoading: Boolean) {
        if (showLoading) _uiState.value = UiState.Loading
        try {
            ensureIds()
            rulesByPackage = parentControlStore.listAppRules(familyId, childId)
                .associateBy { it.packageOrBundleId.lowercase() }
            installedApps = parentControlStore.listInstalledApps(familyId, childId)
            publishRows()
        } catch (error: Throwable) {
            if (_uiState.value !is UiState.Success) {
                _uiState.value = UiState.Error(AppErrorMapper.from(error))
            } else {
                updateSuccess { it.copy(actionError = AppErrorMapper.from(error).userMessage) }
            }
        }
    }

    private fun observeInventory() {
        inventoryJob?.cancel()
        inventoryJob = viewModelScope.launch {
            ensureIds()
            parentControlStore.observeInstalledApps(familyId, childId)
                .catch { /* keep last snapshot; refresh still works */ }
                .collect { apps ->
                    installedApps = apps
                    // Refresh rules occasionally when inventory changes (cheap one-shot).
                    runCatching {
                        rulesByPackage = parentControlStore.listAppRules(familyId, childId)
                            .associateBy { it.packageOrBundleId.lowercase() }
                    }
                    publishRows()
                }
        }
    }

    private fun loadGlobalLimit() {
        policyJob?.cancel()
        policyJob = viewModelScope.launch {
            runCatching {
                ensureIds()
                val policy = parentControlStore.getPolicy(familyId, childId)
                updateSuccess {
                    it.copy(globalLimitEnabled = policy.dailyCeilingMinutes != null)
                }
            }
        }
    }

    private fun publishRows() {
        val rows = mergeRows(installedApps, rulesByPackage)
        updateSuccess { ui ->
            ui.copy(
                rows = rows,
                inventoryReady = rows.isNotEmpty(),
                waitingForInventory = rows.isEmpty(),
                actionError = null,
            )
        }
        seedMissingAllowRules()
    }

    private fun optimisticToggle(packageName: String, allowed: Boolean) {
        updateSuccess { ui ->
            ui.copy(
                rows = ui.rows.map {
                    if (it.packageName == packageName) it.copy(isAllowed = allowed) else it
                },
                selectedPreset = null,
                actionError = null,
            )
        }
    }

    private fun markToggling(packageName: String, active: Boolean) {
        updateSuccess { ui ->
            ui.copy(
                togglingPackages = if (active) {
                    ui.togglingPackages + packageName
                } else {
                    ui.togglingPackages - packageName
                },
            )
        }
    }

    private fun defaultRule(row: AllowlistAppRow): AppRule =
        ChildAllowlistDefaults.ruleForInstalledApp(
            InstalledAppSummary(packageName = row.packageName, label = row.label),
        ).copy(allowed = row.isAllowed)

    /**
     * Persist allow-all + 15m rules for installed packages that have no rule yet.
     * Never overwrites a parent toggle (existing rules are sticky).
     */
    private fun seedMissingAllowRules() {
        val missing = ChildAllowlistDefaults.missingRulesToSeed(installedApps, rulesByPackage)
        if (missing.isEmpty()) return
        if (seedJob?.isActive == true) return
        seedJob = viewModelScope.launch {
            try {
                ensureIds()
                // Optimistic paint: show every missing package as allowed @ default block.
                val optimistic = rulesByPackage.toMutableMap()
                missing.forEach { rule ->
                    optimistic[rule.packageOrBundleId.lowercase()] = rule
                }
                rulesByPackage = optimistic
                publishRows()
                missing.forEach { rule ->
                    parentControlStore.upsertAppRule(familyId, childId, rule)
                }
                rulesByPackage = parentControlStore.listAppRules(familyId, childId)
                    .associateBy { it.packageOrBundleId.lowercase() }
                publishRows()
            } catch (_: Throwable) {
                // Keep optimistic rows; parent can still toggle. Refresh recovers.
            }
        }
    }

    private suspend fun ensureIds() {
        if (familyId.isNotBlank() && childId.isNotBlank()) return
        val context = ensureFamilyReady(preferredChildId.takeIf { it.isNotBlank() })
        familyId = context.familyId
        childId = context.childId
    }

    private fun currentUi(): OnboardingAllowlistUi? =
        (_uiState.value as? UiState.Success)?.data

    private fun updateSuccess(transform: (OnboardingAllowlistUi) -> OnboardingAllowlistUi) {
        _uiState.update { state ->
            if (state is UiState.Success) state.copy(data = transform(state.data)) else state
        }
    }

    override fun onCleared() {
        super.onCleared()
        inventoryJob?.cancel()
        policyJob?.cancel()
        seedJob?.cancel()
    }

    companion object {
        fun mergeRows(
            installed: List<InstalledAppSummary>,
            rulesByPackage: Map<String, AppRule>,
        ): List<AllowlistAppRow> =
            installed
                .distinctBy { it.packageName.lowercase() }
                .map { app ->
                    val classification = AppInventoryCategorizer.classify(app.packageName, app.label)
                    val rule = rulesByPackage[app.packageName.lowercase()]
                    AllowlistAppRow(
                        packageName = app.packageName,
                        label = app.label.ifBlank { app.packageName.substringAfterLast('.') },
                        category = classification.category.toFilter(),
                        subtitle = when {
                            rule != null && rule.blockMinutes > 0 && rule.allowed ->
                                "${classification.subtitle} • ${rule.blockMinutes}m Cap"
                            else -> classification.subtitle
                        },
                        isVerifiedSafe = classification.verifiedSafe,
                        // No rule yet → allowed + default block (seeded to Firestore async).
                        isAllowed = ChildAllowlistDefaults.effectiveAllowed(rule),
                        blockMinutes = ChildAllowlistDefaults.effectiveBlockMinutes(rule),
                        iconBase64 = app.iconBase64,
                        iconHash = app.iconHash,
                    )
                }
                .sortedBy { it.label.lowercase() }

        private fun AppInventoryCategorizer.Category.toFilter(): AppCategoryFilter = when (this) {
            AppInventoryCategorizer.Category.EDUCATIONAL -> AppCategoryFilter.EDUCATIONAL
            AppInventoryCategorizer.Category.ENTERTAINMENT -> AppCategoryFilter.ENTERTAINMENT
            AppInventoryCategorizer.Category.SYSTEM -> AppCategoryFilter.SYSTEM_LOCKED
            AppInventoryCategorizer.Category.OTHER -> AppCategoryFilter.ALL
        }
    }
}
