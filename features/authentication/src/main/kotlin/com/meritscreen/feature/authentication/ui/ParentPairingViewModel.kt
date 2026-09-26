package com.meritscreen.feature.authentication.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.DeviceSummary
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.firebase.error.FirebaseErrorMapper
import com.meritscreen.core.firebase.family.FamilyStore
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.core.firebase.pairing.PairingClient
import com.meritscreen.core.firebase.pairing.PairingOffer
import com.meritscreen.core.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParentPairingChildInfo(
    val childId: String,
    val displayName: String,
    val ageBand: AgeBand,
    val avatar: AvatarPreset,
) {
    val ageBandLabel: String get() = "Ages ${ageBand.displayLabel}"
    val explorationLabel: String get() = when (ageBand) {
        AgeBand.AGE_3_TO_6 -> "Early Years Quest"
        AgeBand.AGE_7_TO_9 -> "Elementary Quest"
        AgeBand.AGE_10_TO_12 -> "Middle Years Quest"
    }
}

data class ParentPairingDeviceInfo(
    val displayName: String,
    val batteryPercent: Int?,
    val isOnline: Boolean,
    val pairedLabel: String,
)

data class ParentPairingRulesInfo(
    val dailyLimitSubtitle: String,
    val allowedAppsSubtitle: String,
    val emergencySubtitle: String,
    val activeRuleCount: Int,
    val totalRuleSlots: Int = 3,
)

data class ParentPairingUiState(
    val childId: String = "",
    val child: ParentPairingChildInfo? = null,
    val offer: PairingOffer? = null,
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val devicePaired: Boolean = false,
    val pairedDevice: ParentPairingDeviceInfo? = null,
    val rules: ParentPairingRulesInfo? = null,
)

@HiltViewModel
class ParentPairingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pairingClient: PairingClient,
    private val familyStore: FamilyStore,
    private val parentControlStore: ParentControlStore,
    private val parentSessionRepository: ParentSessionRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ParentPairingRoute>()

    private val _uiState = MutableStateFlow(ParentPairingUiState(childId = route.childId))
    val uiState: StateFlow<ParentPairingUiState> = _uiState.asStateFlow()

    private var watchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { mint(resetPaired = false) }
    }

    /** Leaves the success screen and mints a fresh pairing code for another device. */
    fun pairAnotherDevice() {
        viewModelScope.launch { mint(resetPaired = true) }
    }

    private suspend fun mint(resetPaired: Boolean) {
        if (!networkMonitor.isCurrentlyOnline()) {
            _uiState.update { it.copy(isLoading = false, error = AppError.Network()) }
            return
        }
        val childId = _uiState.value.childId.ifBlank {
            parentSessionRepository.current()?.childIds?.firstOrNull().orEmpty()
        }
        if (childId.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = AppError.Validation("Add a child profile before pairing a device."),
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                childId = childId,
                devicePaired = if (resetPaired) false else it.devicePaired,
                pairedDevice = if (resetPaired) null else it.pairedDevice,
                rules = if (resetPaired) null else it.rules,
            )
        }
        try {
            val offer = pairingClient.createToken(childId)
            val child = loadChildInfo(offer.familyId, childId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    offer = offer,
                    childId = childId,
                    child = child,
                )
            }
            watchForDevice(offer.familyId, childId)
        } catch (error: AppErrorException) {
            _uiState.update { it.copy(isLoading = false, error = error.error) }
        } catch (error: Throwable) {
            _uiState.update { it.copy(isLoading = false, error = FirebaseErrorMapper.from(error)) }
        }
    }

    private fun watchForDevice(familyId: String, childId: String) {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            familyStore.observeDeviceCount(familyId, childId).collect { count ->
                if (count > 0) {
                    val child = _uiState.value.child ?: loadChildInfo(familyId, childId)
                    val device = loadDeviceInfo(familyId, childId, child?.displayName.orEmpty())
                    val rules = loadRulesInfo(familyId, childId)
                    _uiState.update {
                        it.copy(
                            devicePaired = true,
                            child = child ?: it.child,
                            pairedDevice = device,
                            rules = rules,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadChildInfo(familyId: String, childId: String): ParentPairingChildInfo? {
        val profile = runCatching {
            familyStore.listChildren(familyId).firstOrNull { it.childId == childId }
        }.getOrNull() ?: return null
        return ParentPairingChildInfo(
            childId = profile.childId,
            displayName = profile.displayName,
            ageBand = profile.ageBand,
            avatar = profile.avatar,
        )
    }

    private suspend fun loadDeviceInfo(
        familyId: String,
        childId: String,
        childName: String,
    ): ParentPairingDeviceInfo? {
        val devices = runCatching {
            parentControlStore.listDevices(familyId, childId)
                .filterNot { it.revoked }
        }.getOrDefault(emptyList())
        val primary = devices.maxByOrNull { it.lastSeenAtEpochMs ?: 0L } ?: return null
        return formatDeviceInfo(primary, childName)
    }

    private suspend fun loadRulesInfo(familyId: String, childId: String): ParentPairingRulesInfo {
        val policy = runCatching { parentControlStore.getPolicy(familyId, childId) }.getOrNull()
        val rules = runCatching { parentControlStore.listAppRules(familyId, childId) }.getOrDefault(emptyList())
        val allowed = rules.count { it.allowed && !it.isEmergency }
        val emergency = rules.count { it.isEmergency } + (policy?.emergencyApps?.size ?: 0)
        val ceiling = policy?.dailyCeilingMinutes
        val dailySubtitle = when {
            ceiling == null -> "No daily ceiling set yet — edit anytime in Time Limits"
            ceiling <= 0 -> "Daily ceiling is off for this profile"
            else -> "${formatMinutes(ceiling)} allowance scheduled for today"
        }
        val appsSubtitle = when {
            allowed <= 0 -> "No apps unlocked yet — finish the allowlist when ready"
            allowed == 1 -> "1 approved app ready on this device"
            else -> "$allowed approved apps ready on this device"
        }
        val emergencySubtitle = when {
            emergency <= 0 -> "Add emergency apps anytime from Rules"
            emergency == 1 -> "1 emergency app stays reachable"
            else -> "$emergency emergency apps stay reachable"
        }
        var active = 0
        if (ceiling != null && ceiling > 0) active++
        if (allowed > 0) active++
        if (emergency > 0) active++
        return ParentPairingRulesInfo(
            dailyLimitSubtitle = dailySubtitle,
            allowedAppsSubtitle = appsSubtitle,
            emergencySubtitle = emergencySubtitle,
            activeRuleCount = active,
            totalRuleSlots = 3,
        )
    }

    companion object {
        private const val ONLINE_WINDOW_MS = 5 * 60_000L

        fun formatDeviceInfo(device: DeviceSummary, childName: String): ParentPairingDeviceInfo {
            val model = device.model?.takeIf { it.isNotBlank() } ?: "Android device"
            val displayName = when {
                childName.isBlank() -> model
                childName.endsWith("s", ignoreCase = true) -> "$childName' $model"
                else -> "$childName's $model"
            }
            val now = System.currentTimeMillis()
            val lastSeen = device.lastSeenAtEpochMs
            val isOnline = lastSeen != null && (now - lastSeen) in 0..ONLINE_WINDOW_MS
            val pairedLabel = when {
                lastSeen == null -> "Paired just now"
                now - lastSeen < 60_000L -> "Paired just now"
                now - lastSeen < 3_600_000L -> "Paired ${(now - lastSeen) / 60_000L}m ago"
                else -> "Paired today"
            }
            return ParentPairingDeviceInfo(
                displayName = displayName,
                batteryPercent = device.batteryPercent,
                isOnline = isOnline,
                pairedLabel = pairedLabel,
            )
        }

        fun formatMinutes(totalMinutes: Int): String {
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            return when {
                hours <= 0 -> "${mins}m"
                mins == 0 -> "${hours}h"
                else -> "${hours}h ${mins}m"
            }
        }
    }
}
