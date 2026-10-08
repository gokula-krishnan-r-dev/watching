package com.meritscreen.feature.parent.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.CustomPromptSanitizer
import com.meritscreen.core.common.domain.DeviceSummary
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.common.error.AppErrorMapper
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.firebase.family.FamilyChildProfile
import com.meritscreen.core.firebase.family.FamilyDraftChild
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.core.ui.components.AppUsageSegment
import com.meritscreen.core.ui.theme.MeritStaticColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DeviceConnectionStatus {
    Connected,
    Disconnected,
    Paused,
    Unpaired,
}

@Immutable
data class ChildDetailUi(
    val profile: FamilyChildProfile,
    val policy: ChildPolicy,
    val devices: List<DeviceSummary>,
    val primaryDevice: DeviceSummary?,
    val connectionStatus: DeviceConnectionStatus,
    val connectionStatusLabel: String,
    val deviceModelAndOs: String,
    val batteryPercent: Int?,
    val lastSyncedLabel: String,
    val launcherStatusLabel: String,
    val explorationTierLabel: String,
    val todayMinutes: Int,
    val dailyCeilingMinutes: Int,
    val remainingMinutes: Int,
    val isPaused: Boolean,
    val hasUsageBreakdown: Boolean,
    val appUsageBreakdown: List<AppUsageSegment>,
    val hasQuizData: Boolean,
    val quizScorePercent: Int,
    val quizScoreFraction: String,
    val quizFeedbackNote: String,
    val strengthsText: String,
    val practiceGoalText: String,
    val curriculumLevelText: String,
    val allowedAppCount: Int,
    val emergencyContactCount: Int,
    val stickersUnlockedTotal: Int = 0,
    val stickersUnlockedThisWeek: Int = 0,
    val explorerLevel: Int = 1,
    val bonusToastMessage: String? = null,
    val isRefreshing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class ChildDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val parentSessionRepository: ParentSessionRepository,
    private val parentControlStore: ParentControlStore,
) : ViewModel() {

    private val childId: String = savedStateHandle.get<String>("childId")?.takeIf { it.isNotBlank() }
        ?: runCatching { savedStateHandle.toRoute<ChildDetailRoute>().childId }.getOrDefault("")

    private val refreshTrigger = MutableStateFlow(0)
    private val isRefreshing = MutableStateFlow(false)
    private val optimisticPause = MutableStateFlow<Boolean?>(null)
    private val optimisticBonus = MutableStateFlow<BonusOverlay?>(null)

    private val _uiState = MutableStateFlow<UiState<ChildDetailUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<ChildDetailUi>> = _uiState.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _savingProfile = MutableStateFlow(false)
    val savingProfile: StateFlow<Boolean> = _savingProfile.asStateFlow()

    private val _profileSaved = MutableStateFlow(false)
    val profileSaved: StateFlow<Boolean> = _profileSaved.asStateFlow()

    init {
        viewModelScope.launch {
            parentSessionRepository.session
                .flatMapLatest { session ->
                    val familyId = session?.familyId
                    when {
                        familyId.isNullOrBlank() -> flowOf(UiState.Error(AppError.Auth()))
                        childId.isBlank() -> flowOf(
                            UiState.Error(AppError.NotFound("We couldn't find that child.")),
                        )
                        else -> {
                            // Keep a single devices listener for this screen; combine local
                            // overlays + clock ticks without re-attaching Firestore.
                            combine(
                                parentControlStore.observeDevices(familyId, childId)
                                    .debounce(AppConfig.CHILD_DETAIL_DEVICES_DEBOUNCE_MS),
                                refreshTrigger,
                                optimisticPause,
                                optimisticBonus,
                                isRefreshing,
                            ) { devices, _, pauseOverride, bonus, refreshing ->
                                DetailStreamInput(
                                    familyId = familyId,
                                    devices = devices.filter { !it.revoked },
                                    pauseOverride = pauseOverride,
                                    bonus = bonus,
                                    nowMs = System.currentTimeMillis(),
                                    isRefreshing = refreshing,
                                )
                            }.flatMapLatest { input ->
                                flow {
                                    if (_uiState.value !is UiState.Success && !input.isRefreshing) {
                                        emit(UiState.Loading)
                                    }
                                    emit(
                                        UiState.Success(
                                            loadDetail(
                                                familyId = input.familyId,
                                                devices = input.devices,
                                                nowMs = input.nowMs,
                                                pauseOverride = input.pauseOverride,
                                                bonus = input.bonus,
                                                isRefreshing = input.isRefreshing,
                                            ),
                                        ),
                                    )
                                }
                            }.catch { emit(UiState.Error(AppErrorMapper.from(it))) }
                        }
                    }
                }
                .collect { _uiState.value = it }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                optimisticPause.value = null
                optimisticBonus.value = null
                refreshTrigger.value++
                kotlinx.coroutines.delay(500)
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun togglePause() {
        viewModelScope.launch {
            val familyId = parentSessionRepository.current()?.familyId ?: return@launch
            val current = (_uiState.value as? UiState.Success)?.data ?: return@launch
            val newPaused = !current.isPaused
            optimisticPause.value = newPaused
            runCatching {
                parentControlStore.setChildPaused(familyId, childId, newPaused)
            }.onFailure {
                optimisticPause.value = current.isPaused
            }
        }
    }

    fun grantBonus(minutes: Int = AppConfig.BONUS_TIME_MINUTES_DEFAULT) {
        viewModelScope.launch {
            val familyId = parentSessionRepository.current()?.familyId ?: return@launch
            val current = (_uiState.value as? UiState.Success)?.data ?: return@launch
            val overlay = BonusOverlay(
                extraCeilingMinutes = minutes + (optimisticBonus.value?.extraCeilingMinutes ?: 0),
                toastMessage = "✨ $minutes minutes added to ${current.profile.displayName}'s daily ceiling!",
            )
            optimisticBonus.value = overlay
            runCatching {
                parentControlStore.addBonusTime(familyId, childId, minutes)
            }.onFailure {
                optimisticBonus.value = null
            }
        }
    }

    fun dismissBonusToast() {
        val current = optimisticBonus.value ?: return
        optimisticBonus.value = current.copy(toastMessage = null)
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun clearProfileSaved() {
        _profileSaved.value = false
    }

    fun updateProfile(
        name: String,
        ageBand: AgeBand,
        avatar: AvatarPreset,
        language: String,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _actionError.value = "Enter your child's name."
            return
        }
        viewModelScope.launch {
            val familyId = parentSessionRepository.current()?.familyId ?: return@launch
            _savingProfile.value = true
            _actionError.value = null
            runCatching {
                parentControlStore.updateChild(
                    familyId,
                    childId,
                    FamilyDraftChild(
                        localId = childId,
                        name = trimmed,
                        ageBand = ageBand,
                        avatar = avatar,
                        language = language.ifBlank { "en" },
                    ),
                )
            }.onSuccess {
                _profileSaved.value = true
                refresh()
            }.onFailure {
                _actionError.value = AppErrorMapper.from(it).userMessage
            }
            _savingProfile.value = false
        }
    }

    fun deleteChild() {
        viewModelScope.launch {
            val session = parentSessionRepository.current() ?: return@launch
            _deleting.value = true
            _actionError.value = null
            runCatching {
                parentControlStore.deleteChild(session.familyId, childId)
                parentSessionRepository.set(
                    session.copy(childIds = session.childIds.filterNot { it == childId }),
                )
            }.onSuccess {
                _deleted.value = true
            }.onFailure {
                _actionError.value = AppErrorMapper.from(it).userMessage
            }
            _deleting.value = false
        }
    }

    private suspend fun loadDetail(
        familyId: String,
        devices: List<DeviceSummary>,
        nowMs: Long,
        pauseOverride: Boolean?,
        bonus: BonusOverlay?,
        isRefreshing: Boolean = false,
    ): ChildDetailUi {
        val profile = parentControlStore.listChildren(familyId)
            .firstOrNull { it.childId == childId }
            ?: throw AppErrorException(AppError.NotFound("We couldn't find that child."))

        val policy = parentControlStore.getPolicy(familyId, childId)
        val apps = runCatching {
            parentControlStore.listAppRules(familyId, childId)
        }.getOrDefault(emptyList())
        val appRulesMap = apps.associate { it.packageOrBundleId to it.displayName }

        val usage = runCatching {
            parentControlStore.listUsageDays(familyId, childId, 1)
        }.getOrDefault(emptyList())
        val usageDay = usage.firstOrNull()
        val minutesByApp = usageDay?.minutesByApp.orEmpty()
        val todayMinutes = usageDay?.minutesUsed
            ?: minutesByApp.values.sum().takeIf { minutesByApp.isNotEmpty() }
            ?: 0

        val lastQuiz = runCatching {
            parentControlStore.listQuizAttempts(familyId, childId, 1).firstOrNull()
        }.getOrNull()

        val skillState = runCatching {
            parentControlStore.getSkillState(familyId, childId)
        }.getOrDefault(emptyList())

        val practiceHint = skillState.flatMap { it.weakConcepts }.firstOrNull()?.title

        val baseCeiling = policy.dailyCeilingMinutes ?: AppConfig.DEFAULT_DAILY_CEILING_MINUTES
        val ceiling = baseCeiling + (bonus?.extraCeilingMinutes ?: 0)
        val remaining = (ceiling - todayMinutes).coerceAtLeast(0)
        val isPaused = pauseOverride ?: policy.paused

        val primaryDevice = devices.maxByOrNull { it.lastSeenAtEpochMs ?: 0L }
        val connection = resolveConnectionStatus(
            devices = devices,
            primaryDevice = primaryDevice,
            isPaused = isPaused,
            nowMs = nowMs,
        )

        val appColors = listOf(
            MeritStaticColors.Primary,
            MeritStaticColors.Secondary,
            MeritStaticColors.Tertiary,
            MeritStaticColors.PrimaryContainer,
        )

        val segments = minutesByApp.entries
            .sortedByDescending { it.value }
            .take(4)
            .mapIndexed { index, entry ->
                AppUsageSegment(
                    label = resolveAppLabel(entry.key, appRulesMap[entry.key]),
                    minutes = entry.value,
                    color = appColors.getOrElse(index) { MeritStaticColors.Primary },
                )
            }

        val hasQuizData = lastQuiz != null
        val quizScorePercent = if (lastQuiz != null && lastQuiz.total > 0) {
            (lastQuiz.score * 100) / lastQuiz.total
        } else {
            0
        }
        val quizScoreFraction = if (lastQuiz != null) {
            "${lastQuiz.score} / ${lastQuiz.total} Correct"
        } else {
            "No quizzes yet"
        }
        val quizFeedbackNote = when {
            lastQuiz == null -> "Results appear after the next unlock check"
            lastQuiz.passed -> "Passed this unlock check"
            else -> "Rest needed before next retry"
        }

        val strengthsText = skillState
            .filter { !it.weak }
            .map { it.topic.replaceFirstChar { c -> c.uppercase() } }
            .ifEmpty {
                if (hasQuizData) listOf("Building strengths…") else listOf("Not enough data yet")
            }
            .joinToString(", ")

        val practiceGoalText = practiceHint
            ?: skillState.firstOrNull { it.weak }?.topic?.replaceFirstChar { it.uppercase() }
            ?: if (hasQuizData) "Keep practicing" else "Waiting for first quiz"

        val weekAgoMs = nowMs - 7L * 24 * 60 * 60 * 1000
        val stickerUnlocks = runCatching {
            parentControlStore.listStickerUnlocks(familyId, childId, 50)
        }.getOrDefault(emptyList())
        val explorerProgress = runCatching {
            parentControlStore.getExplorerProgress(familyId, childId)
        }.getOrNull()
        val stickersThisWeek = stickerUnlocks.count { it.unlockedAtEpochMs >= weekAgoMs }

        return ChildDetailUi(
            profile = profile,
            policy = policy.copy(
                paused = isPaused,
                dailyCeilingMinutes = ceiling,
                bonusMinutesToday = policy.bonusMinutesToday + (bonus?.extraCeilingMinutes ?: 0),
            ),
            devices = devices,
            primaryDevice = primaryDevice,
            connectionStatus = connection.status,
            connectionStatusLabel = connection.label,
            deviceModelAndOs = formatDeviceModelAndOs(primaryDevice),
            batteryPercent = primaryDevice?.batteryPercent,
            lastSyncedLabel = formatLastSyncedLabel(primaryDevice?.lastSeenAtEpochMs, nowMs),
            launcherStatusLabel = formatLauncherStatus(primaryDevice, devices.isEmpty()),
            explorationTierLabel = formatExplorationTier(profile.ageBand),
            todayMinutes = todayMinutes,
            dailyCeilingMinutes = ceiling,
            remainingMinutes = remaining,
            isPaused = isPaused,
            hasUsageBreakdown = segments.isNotEmpty(),
            appUsageBreakdown = segments,
            hasQuizData = hasQuizData,
            quizScorePercent = quizScorePercent,
            quizScoreFraction = quizScoreFraction,
            quizFeedbackNote = quizFeedbackNote,
            strengthsText = strengthsText,
            practiceGoalText = practiceGoalText,
            curriculumLevelText = formatCurriculumLevel(profile.ageBand),
            allowedAppCount = apps.count { it.allowed },
            emergencyContactCount = policy.emergencyApps.size,
            stickersUnlockedTotal = stickerUnlocks.size,
            stickersUnlockedThisWeek = stickersThisWeek,
            explorerLevel = explorerProgress?.explorerLevel ?: 1,
            bonusToastMessage = bonus?.toastMessage,
            isRefreshing = isRefreshing,
        )
    }

    private data class DetailStreamInput(
        val familyId: String,
        val devices: List<DeviceSummary>,
        val pauseOverride: Boolean?,
        val bonus: BonusOverlay?,
        val nowMs: Long,
        val isRefreshing: Boolean = false,
    )

    private data class BonusOverlay(
        val extraCeilingMinutes: Int,
        val toastMessage: String?,
    )

    private data class ConnectionResolved(
        val status: DeviceConnectionStatus,
        val label: String,
    )

    private companion object {
        fun resolveConnectionStatus(
            devices: List<DeviceSummary>,
            primaryDevice: DeviceSummary?,
            isPaused: Boolean,
            nowMs: Long,
        ): ConnectionResolved {
            if (isPaused) {
                return ConnectionResolved(DeviceConnectionStatus.Paused, "Device Paused")
            }
            if (devices.isEmpty()) {
                return ConnectionResolved(DeviceConnectionStatus.Unpaired, "Not paired")
            }
            val lastSeen = primaryDevice?.lastSeenAtEpochMs
            val onlineMs = AppConfig.DEVICE_ONLINE_THRESHOLD_MINUTES * 60_000L
            val connected = lastSeen != null && nowMs - lastSeen <= onlineMs
            return if (connected) {
                ConnectionResolved(DeviceConnectionStatus.Connected, "Connected")
            } else {
                ConnectionResolved(DeviceConnectionStatus.Disconnected, "Disconnected")
            }
        }

        fun formatDeviceModelAndOs(device: DeviceSummary?): String {
            if (device == null) return "No device paired"
            val model = device.model?.takeIf { it.isNotBlank() } ?: "Android device"
            val os = device.osVersion?.takeIf { it.isNotBlank() }
            return if (os != null) "$model • $os" else model
        }

        fun formatLastSyncedLabel(lastSeenAtEpochMs: Long?, nowMs: Long): String {
            if (lastSeenAtEpochMs == null) return "Never synced"
            val diff = (nowMs - lastSeenAtEpochMs).coerceAtLeast(0L)
            return when {
                diff < 60_000L -> "Synced just now"
                diff < 3_600_000L -> "Synced ${diff / 60_000L}m ago"
                diff < 86_400_000L -> "Synced ${diff / 3_600_000L}h ago"
                else -> "Synced ${diff / 86_400_000L}d ago"
            }
        }

        fun formatLauncherStatus(device: DeviceSummary?, unpaired: Boolean): String = when {
            unpaired -> "Pair a device to begin"
            device?.launcherDefault == true -> "Merit Launcher Active"
            device?.launcherDefault == false -> "Merit Launcher not set"
            else -> "Launcher status unknown"
        }

        fun formatExplorationTier(ageBand: AgeBand): String =
            "Exploration Tier • Age ${ageBand.displayLabel}"

        fun formatCurriculumLevel(ageBand: AgeBand): String = when (ageBand) {
            AgeBand.AGE_3_TO_6 -> "Adaptive Early Years"
            AgeBand.AGE_7_TO_9 -> "Adaptive Grade 3 Band"
            AgeBand.AGE_10_TO_12 -> "Adaptive Middle Band"
        }

        fun resolveAppLabel(packageName: String, displayName: String?): String {
            displayName?.takeIf { it.isNotBlank() }?.let { return it }
            return packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                .ifBlank { packageName }
        }
    }
}

/** Shared editor for P14/P15/P16 — routes all carry `childId` in the SavedStateHandle. */
@HiltViewModel
class PolicyEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val parentSessionRepository: ParentSessionRepository,
    private val parentControlStore: ParentControlStore,
) : ViewModel() {

    private val childId: String = checkNotNull(savedStateHandle.get<String>("childId")) {
        "Policy screens require childId"
    }

    private val _policy = MutableStateFlow<UiState<ChildPolicy>>(UiState.Loading)
    val policy: StateFlow<UiState<ChildPolicy>> = _policy.asStateFlow()

    private val _ageBand = MutableStateFlow(AgeBand.AGE_7_TO_9)
    /** Child age band for age-appropriate Quick add chips on adaptive settings. */
    val ageBand: StateFlow<AgeBand> = _ageBand.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val familyId = parentSessionRepository.current()?.familyId ?: return
            val profile = parentControlStore.listChildren(familyId)
                .firstOrNull { it.childId == childId }
            if (profile != null) {
                _ageBand.value = profile.ageBand
            }
            _policy.value = UiState.Success(parentControlStore.getPolicy(familyId, childId))
        } catch (error: Throwable) {
            _policy.value = UiState.Error(AppErrorMapper.from(error))
        }
    }

    fun update(transform: (ChildPolicy) -> ChildPolicy) {
        val current = (_policy.value as? UiState.Success)?.data ?: return
        _policy.value = UiState.Success(transform(current))
        _saved.value = false
        _error.value = null
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun save() {
        val current = (_policy.value as? UiState.Success)?.data ?: return
        val rawGuidelines = current.customPromptGuidelines
        val sanitized = CustomPromptSanitizer.sanitize(rawGuidelines)
        if (rawGuidelines.isNotBlank() && sanitized.isEmpty()) {
            _error.value =
                "That guidance looks like an instruction override. Rephrase as learning focus only (topics, style, curriculum)."
            return
        }
        val toSave = current.copy(customPromptGuidelines = sanitized)
        if (sanitized != rawGuidelines) {
            _policy.value = UiState.Success(toSave)
        }
        viewModelScope.launch {
            _saving.value = true
            try {
                val familyId = parentSessionRepository.current()?.familyId ?: return@launch
                parentControlStore.updatePolicy(familyId, childId, toSave)
                _saved.value = true
            } catch (error: Throwable) {
                _error.value = AppErrorMapper.from(error).userMessage
            } finally {
                _saving.value = false
            }
        }
    }

    fun consumeSaved(): Boolean {
        if (!_saved.value) return false
        _saved.value = false
        return true
    }
}
