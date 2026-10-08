package com.meritscreen.feature.child.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.ExplorerProgress
import com.meritscreen.core.common.domain.SessionPhase
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.feature.applications.AppIconLoader
import com.meritscreen.feature.applications.InstalledAppsRepository
import com.meritscreen.feature.child.data.ChildDeviceLifecycleCoordinator
import com.meritscreen.feature.child.data.ChildLocalProfile
import com.meritscreen.feature.child.data.ChildPolicyRepository
import com.meritscreen.feature.child.data.ChildPolicySyncCoordinator
import com.meritscreen.feature.child.data.StickerRepository
import com.meritscreen.feature.child.domain.ChildSessionController
import com.meritscreen.feature.child.domain.SessionEngine
import com.meritscreen.feature.child.domain.SessionSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import javax.inject.Inject

enum class TileBadgeType {
    TIME_ALERT,
    FREE,
    OPEN,
    INFO,
    NEUTRAL,
}

@Immutable
data class HomeAppTile(
    val rule: AppRule,
    val label: String,
    val subtitle: String = "",
    val badgeLabel: String? = null,
    val badgeType: TileBadgeType = TileBadgeType.NEUTRAL,
    val category: String = "App",
    val remainingMinutes: Int? = null,
    val installed: Boolean = true,
    val launchable: Boolean = true,
)

@Immutable
data class ChildHomeUi(
    val greetingName: String,
    val profile: ChildLocalProfile?,
    val policy: ChildPolicy,
    val session: SessionSnapshot,
    val apps: List<HomeAppTile>,
    val remainingBlockMinutes: Int?,
    val remainingCooldownSeconds: Int,
    val dailyRemainingMinutes: Int?,
    val usedTodayMinutes: Int = 0,
    val dailyCeilingMinutes: Int = 120,
    val balanceStatus: String = "Good balance",
    val quietTimeHint: String = "Next quiet time starts at 7:30 PM",
    val explorerLevel: Int = 4,
    val syncHint: String?,
    val isDefaultHome: Boolean,
)

enum class AppTapResult {
    Launched,
    QuizDue,
    Shielded,
    Blocked,
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChildHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pairingStore: ChildPairingStore,
    private val policyRepository: ChildPolicyRepository,
    private val sessionController: ChildSessionController,
    private val syncCoordinator: ChildPolicySyncCoordinator,
    private val installedAppsRepository: InstalledAppsRepository,
    private val deviceLifecycle: ChildDeviceLifecycleCoordinator,
    private val stickerRepository: StickerRepository,
    val iconLoader: AppIconLoader,
) : ViewModel() {

    private val childId = MutableStateFlow<String?>(null)
    private val clock = MutableStateFlow(SystemClock.elapsedRealtime())
    private val _launchError = MutableStateFlow<String?>(null)
    val launchError: StateFlow<String?> = _launchError
    private val _isDefaultHome = MutableStateFlow(false)
    val forceSignedOut: StateFlow<Boolean> = deviceLifecycle.forceSignedOut

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isCallingEmergency = MutableStateFlow(false)
    val isCallingEmergency: StateFlow<Boolean> = _isCallingEmergency

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startEmergencyCall() {
        _isCallingEmergency.value = true
    }

    fun endEmergencyCall() {
        _isCallingEmergency.value = false
    }

    private val pairingReady = MutableStateFlow(false)

    private val policyBundle = childId.flatMapLatest { id ->
        if (id == null) {
            flowOf(Triple(ChildPolicy(), emptyList<AppRule>(), null as ChildLocalProfile?))
        } else {
            combine(
                policyRepository.observePolicy(id),
                policyRepository.observeAppRules(id),
                policyRepository.observeProfile(id),
            ) { policy, rules, profile -> Triple(policy, rules, profile) }
        }
    }

    private val explorerProgress = childId.flatMapLatest { id ->
        if (id == null) {
            flowOf(ExplorerProgress())
        } else {
            stickerRepository.observeProgress(id)
        }
    }

    val uiState: StateFlow<UiState<ChildHomeUi>> = combine(
        childId,
        pairingReady,
        sessionController.snapshot,
        policyBundle,
        clock,
    ) { id, ready, session, bundle, now ->
        HomeGate(id, ready, session, bundle, now)
    }.combine(_isDefaultHome) { gate, isDefaultHome ->
        gate to isDefaultHome
    }.combine(installedAppsRepository.observeInstalledPackageNames()) { (gate, isDefaultHome), installedPackages ->
        Triple(gate, isDefaultHome, installedPackages)
    }.combine(explorerProgress) { (gate, isDefaultHome, installedPackages), progress ->
        if (!gate.ready) {
            return@combine UiState.Loading
        }
        val id = gate.id
        if (id == null) {
            return@combine UiState.Error(AppError.Auth("This device is not paired."))
        }
        val (policy, rules, profile) = gate.bundle
        val tiles = rules
            .filter { it.allowed || it.isEmergency }
            .map { rule ->
                val installed = installedAppsRepository.isInstalledCached(
                    rule.packageOrBundleId,
                    installedPackages,
                )
                val label = rule.displayName.ifBlank {
                    rule.packageOrBundleId.substringAfterLast('.')
                }
                val launchable = installed && SessionEngine.canLaunch(gate.session, rule, policy)
                buildTile(
                    rule = rule,
                    label = label,
                    installed = installed,
                    launchable = launchable,
                    session = gate.session,
                    nowElapsedMs = gate.now,
                    defaultBlockMinutes = policy.defaultBlockMinutes,
                )
            }

        val ceiling = (policy.dailyCeilingMinutes ?: com.meritscreen.core.common.config.AppConfig.DEFAULT_DAILY_CEILING_MINUTES).coerceAtLeast(30)
        val usedMinutes = gate.session.minutesUsedToday.coerceAtLeast(0)
        val dailyRemaining = gate.session.dailyRemainingMinutes(policy)
        val balance = if (usedMinutes <= ceiling * 0.7) "Good balance" else "Pacing check"

        UiState.Success(
            ChildHomeUi(
                greetingName = profile?.displayName?.ifBlank { null }
                    ?: "Explorer",
                profile = profile,
                policy = policy,
                session = gate.session,
                apps = tiles,
                remainingBlockMinutes = if (gate.session.phase == SessionPhase.InBlock) {
                    gate.session.remainingBlockMinutes(gate.now)
                } else {
                    null
                },
                remainingCooldownSeconds = gate.session.remainingCooldownSeconds(gate.now),
                dailyRemainingMinutes = dailyRemaining,
                usedTodayMinutes = usedMinutes,
                dailyCeilingMinutes = ceiling,
                balanceStatus = balance,
                quietTimeHint = "Next quiet time starts at 7:30 PM",
                explorerLevel = progress.explorerLevel,
                syncHint = if (rules.isEmpty()) {
                    "Ask a parent to allow apps, then refresh."
                } else {
                    null
                },
                isDefaultHome = isDefaultHome,
            ),
        )
    }.catch { emit(UiState.Error(AppError.Unknown())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private fun buildTile(
        rule: AppRule,
        label: String,
        installed: Boolean,
        launchable: Boolean,
        session: SessionSnapshot,
        nowElapsedMs: Long,
        defaultBlockMinutes: Int,
    ): HomeAppTile {
        val pkg = rule.packageOrBundleId.lowercase()
        val isActiveBlock = session.phase == SessionPhase.InBlock &&
            session.activePackage.equals(rule.packageOrBundleId, ignoreCase = true)
        val liveRemaining = if (isActiveBlock) {
            session.remainingBlockMinutes(nowElapsedMs)
        } else {
            null
        }
        // Only treat a per-app cap as badge-worthy when the parent customized it away
        // from the child-wide default. Shared default blocks are covered by the global
        // "remaining today" card — repeating them on every icon is noise.
        val hasAppSpecificLimit = rule.blockMinutes > 0 &&
            rule.blockMinutes != defaultBlockMinutes
        val displayRemaining = liveRemaining
            ?: rule.blockMinutes.takeIf { hasAppSpecificLimit }
        val timeBadge = displayRemaining?.let { mins -> "${mins}m left" }

        val (subtitle, badgeLabel, badgeType, category) = when {
            rule.isEmergency -> {
                Quad("Emergency", "Always Free", TileBadgeType.FREE, "Emergency")
            }
            pkg.contains("youtube") || pkg.contains("video") -> {
                timeBadge.toTimedOrNeutral("Videos", "Media")
            }
            pkg.contains("instagram") || pkg.contains("safegram") || pkg.contains("photo") -> {
                timeBadge.toTimedOrNeutral("Photos", "Media")
            }
            pkg.contains("chat") || pkg.contains("message") || pkg.contains("messenger") -> {
                Quad("Family Safe", "Friends", TileBadgeType.INFO, "Social")
            }
            pkg.contains("minecraft") || pkg.contains("game") || pkg.contains("roblox") -> {
                Quad(
                    "Build",
                    if (isActiveBlock && timeBadge != null) timeBadge else "✓ Open",
                    if (isActiveBlock && timeBadge != null) TileBadgeType.TIME_ALERT else TileBadgeType.OPEN,
                    "Game",
                )
            }
            pkg.contains("duo") || pkg.contains("read") || pkg.contains("abc") -> {
                Quad(
                    "Reading",
                    timeBadge ?: "∞ Free",
                    if (timeBadge != null) TileBadgeType.TIME_ALERT else TileBadgeType.FREE,
                    "Learn",
                )
            }
            pkg.contains("disney") || pkg.contains("movie") || pkg.contains("netflix") -> {
                timeBadge.toTimedOrInfo("Movies", "Weekend", "Media")
            }
            pkg.contains("scratch") || pkg.contains("code") -> {
                timeBadge.toTimedOrInfo("Coding", "Creative", "Logic")
            }
            pkg.contains("classroom") || pkg.contains("school") -> {
                timeBadge.toTimedOrInfo("Homework", "School", "Learn")
            }
            timeBadge != null -> {
                Quad("App", timeBadge, TileBadgeType.TIME_ALERT, "General")
            }
            else -> {
                Quad("App", null, TileBadgeType.NEUTRAL, "General")
            }
        }

        return HomeAppTile(
            rule = rule,
            label = label,
            subtitle = subtitle,
            badgeLabel = badgeLabel,
            badgeType = badgeType,
            category = category,
            remainingMinutes = displayRemaining,
            installed = installed,
            launchable = launchable,
        )
    }

    /** Timed badge when present; otherwise no badge (global default / daily budget only). */
    private fun String?.toTimedOrNeutral(subtitle: String, category: String): Quad<String, String?, TileBadgeType, String> =
        if (this != null) {
            Quad(subtitle, this, TileBadgeType.TIME_ALERT, category)
        } else {
            Quad(subtitle, null, TileBadgeType.NEUTRAL, category)
        }

    /** Timed badge when present; otherwise a non-timer status chip. */
    private fun String?.toTimedOrInfo(
        subtitle: String,
        infoBadge: String,
        category: String,
    ): Quad<String, String?, TileBadgeType, String> =
        if (this != null) {
            Quad(subtitle, this, TileBadgeType.TIME_ALERT, category)
        } else {
            Quad(subtitle, infoBadge, TileBadgeType.INFO, category)
        }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private var tickerJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            // Paint from Room first; start background sync/inventory after the first frame.
            sessionController.hydrate()
            val credential = pairingStore.get()
            childId.value = credential?.childId
            pairingReady.value = true
            _isDefaultHome.value = deviceLifecycle.isDefaultHome()
            // Seed a local profile so Home never waits on Firestore for the greeting.
            if (credential != null && credential.displayName.isNotBlank()) {
                val existing = policyRepository.getProfile(credential.childId)
                if (existing == null) {
                    policyRepository.saveProfile(
                        ChildLocalProfile(
                            childId = credential.childId,
                            familyId = credential.familyId,
                            displayName = credential.displayName,
                            ageBand = "AGE_7_TO_9",
                            avatarId = com.meritscreen.core.common.domain.AvatarPreset.Default.name,
                            language = "en",
                        ),
                    )
                }
            }
            yield()
            // Defer network / AI pack work so the first Home frame stays local-only.
            deviceLifecycle.start()
            syncCoordinator.start()
        }
        startPeriodicTicker()
    }

    internal fun startPeriodicTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                // The foreground service is the single owner of app usage accrual.
                // Home only refreshes clock-driven labels here.
                clock.value = SystemClock.elapsedRealtime()
                delay(1_000)
            }
        }
    }

    internal fun stopPeriodicTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun refreshRules() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncCoordinator.refreshNow()
                deviceLifecycle.refreshInventoryNow()
                childId.value = pairingStore.get()?.childId
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Called on Home resume — accrue any time spent in another app, then restart the 1 Hz ticker. */
    fun onResumed() {
        _isDefaultHome.value = deviceLifecycle.isDefaultHome()
        deviceLifecycle.checkOnResume()
        clock.value = SystemClock.elapsedRealtime()
        // Shared-tablet: pick up an active-child switch performed from Parent menu.
        viewModelScope.launch {
            val activeId = pairingStore.get()?.childId
            if (activeId != null && activeId != childId.value) {
                childId.value = activeId
            }
        }
        startPeriodicTicker()
    }

    /** Flush accrual and pause the ticker while Home is not visible (battery). */
    fun onPaused() {
        stopPeriodicTicker()
        clock.value = SystemClock.elapsedRealtime()
    }

    fun clearLaunchError() {
        _launchError.value = null
    }

    suspend fun onAppTapped(tile: HomeAppTile): AppTapResult {
        _launchError.value = null
        if (!tile.installed) {
            _launchError.value = "That app isn’t installed on this phone."
            return AppTapResult.Blocked
        }
        val policy = sessionController.currentPolicy()
        val isEmergency = tile.rule.isEmergency ||
            SessionEngine.isEmergencyPackage(tile.rule.packageOrBundleId, policy)
        if (!tile.rule.allowed && !isEmergency) {
            return AppTapResult.Blocked
        }
        if (policy.paused && !isEmergency) {
            return AppTapResult.Shielded
        }
        val session = sessionController.openApp(tile.rule)
        return when (session.phase) {
            SessionPhase.QuizDue -> AppTapResult.QuizDue
            SessionPhase.Shielded -> {
                if (isEmergency) {
                    if (launchPackage(tile.rule.packageOrBundleId)) AppTapResult.Launched else AppTapResult.Blocked
                } else {
                    AppTapResult.Shielded
                }
            }
            SessionPhase.InBlock, SessionPhase.Idle -> {
                if (launchPackage(tile.rule.packageOrBundleId)) {
                    AppTapResult.Launched
                } else {
                    _launchError.value = "Couldn’t open that app."
                    AppTapResult.Blocked
                }
            }
        }
    }

    private fun launchPackage(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}

private data class HomeGate(
    val id: String?,
    val ready: Boolean,
    val session: SessionSnapshot,
    val bundle: Triple<ChildPolicy, List<AppRule>, ChildLocalProfile?>,
    val now: Long,
)
