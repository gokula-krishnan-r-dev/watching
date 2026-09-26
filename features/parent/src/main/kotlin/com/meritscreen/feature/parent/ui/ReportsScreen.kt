package com.meritscreen.feature.parent.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.meritscreen.core.analytics.AnalyticsEvent
import com.meritscreen.core.analytics.AnalyticsTracker
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AppInventoryCategorizer
import com.meritscreen.core.common.domain.AppMinutesRollup
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.ChildReportsSnapshot
import com.meritscreen.core.common.domain.DailyMinutesPoint
import com.meritscreen.core.common.domain.ReportsAggregator
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorMapper
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.core.ui.components.ChildAvatar
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.MeritPullToRefreshBox
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritStaticColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppCategoryType {
    LEARN,
    MEDIA,
    LANGUAGE,
    LOGIC,
    GENERAL,
}

@Immutable
data class ChildReportProfile(
    val childId: String,
    val displayName: String,
    val ageLabel: String,
    val avatar: AvatarPreset,
)

@Immutable
data class AppAllocationItem(
    val packageName: String,
    val displayName: String,
    val minutes: Int,
    val percentage: Int,
    val categoryLabel: String,
    val categoryType: AppCategoryType,
    val segmentColor: Color,
)

@Immutable
data class FocusSessionItem(
    val attemptId: String,
    val title: String,
    val passed: Boolean,
    val scoreLabel: String,
    val extraMinutes: Int,
)

@Immutable
data class AiTopicInsight(
    val topic: String,
    val levelLabel: String,
    val tierLabel: String,
    val weak: Boolean,
    val masteredCount: Int,
)

@Immutable
data class ReportsUi(
    val selectedChildId: String,
    val selectedDays: Int,
    val children: List<ChildReportProfile>,
    val report: ChildReportsSnapshot,
    val selectedChildName: String,
    val periodLabel: String,
    val dailyAverageFormatted: String,
    val totalScreenTimeFormatted: String,
    val totalAiFocusTimeFormatted: String,
    val trendPercentage: Int?,
    val streakDays: Int,
    val masteryRatePercent: Int?,
    val balancedScore: Int?,
    val balancedScoreLabel: String,
    val balancedScoreDescription: String,
    val educationalPercent: Int?,
    val appAllocations: List<AppAllocationItem>,
    val quizPassPercent: Int?,
    val focusSessionCount: Int,
    val focusSessionsPassed: Int,
    val extraMinutesEarned: Int,
    val focusSessions: List<FocusSessionItem>,
    val aiTopics: List<AiTopicInsight>,
    val demonstratedStrengths: List<String>,
    val growthRecommended: List<String>,
    val masteredConceptCount: Int,
    val cooldownIncidentsCount: Int,
    val avgCooldownRestMinutes: Int,
    val hasUsageData: Boolean,
    val hasQuizData: Boolean,
    val hasSkillData: Boolean,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val parentSessionRepository: ParentSessionRepository,
    private val parentControlStore: ParentControlStore,
    private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {

    private val initialChildId: String = savedStateHandle.get<String>("childId")
        ?: runCatching { savedStateHandle.toRoute<ReportsRoute>().childId }.getOrDefault("")

    private val selectedChildIdFlow = MutableStateFlow(initialChildId)
    private val selectedDaysFlow = MutableStateFlow(AppConfig.REPORTS_DEFAULT_DAYS)
    private val refreshTrigger = MutableStateFlow(0)
    private val isRefreshing = MutableStateFlow(false)

    private val _uiState = MutableStateFlow<UiState<ReportsUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<ReportsUi>> = _uiState.asStateFlow()

    private var observationJob: kotlinx.coroutines.Job? = null

    init {
        analyticsTracker.track(AnalyticsEvent.ReportsViewed)
        startObserving()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun startObserving() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            if (_uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }
            val session = parentSessionRepository.current()
            if (session == null || session.familyId.isBlank()) {
                _uiState.value = UiState.Error(AppError.Auth())
                return@launch
            }
            val familyId = session.familyId

            combine(
                parentControlStore.observeChildren(familyId),
                selectedChildIdFlow,
                selectedDaysFlow,
                refreshTrigger,
            ) { rawChildren, currentSelectedId, currentDays, _ ->
                Triple(rawChildren, currentSelectedId, currentDays)
            }.flatMapLatest { (rawChildren, currentSelectedId, currentDays) ->
                val activeChildId = when {
                    currentSelectedId.isNotBlank() && rawChildren.any { it.childId == currentSelectedId } -> currentSelectedId
                    rawChildren.isNotEmpty() -> rawChildren.first().childId
                    else -> currentSelectedId
                }

                if (currentSelectedId != activeChildId && activeChildId.isNotBlank()) {
                    selectedChildIdFlow.value = activeChildId
                }

                val childProfiles = rawChildren.map { child ->
                    ChildReportProfile(
                        childId = child.childId,
                        displayName = child.displayName,
                        ageLabel = "Ages ${child.ageBand.displayLabel}",
                        avatar = child.avatar,
                    )
                }

                if (activeChildId.isBlank()) {
                    isRefreshing.map { refreshing ->
                        UiState.Success(emptyReportsUi(childProfiles, currentDays).copy(isRefreshing = refreshing))
                    }
                } else {
                    combine(
                        parentControlStore.observeUsageDays(familyId, activeChildId, currentDays),
                        parentControlStore.observeQuizAttempts(familyId, activeChildId, AppConfig.REPORTS_MAX_ATTEMPTS),
                        parentControlStore.observeSkillState(familyId, activeChildId),
                        parentControlStore.observeInstalledApps(familyId, activeChildId),
                        combine(
                            parentControlStore.observeAppRules(familyId, activeChildId),
                            parentControlStore.observePolicy(familyId, activeChildId),
                            ::Pair,
                        ),
                    ) { usage, attempts, skills, installedApps, (appRules, policy) ->
                        val labels = buildMap {
                            installedApps.forEach { put(it.packageName, it.label) }
                            appRules.forEach { rule ->
                                if (rule.displayName.isNotBlank()) {
                                    put(rule.packageOrBundleId, rule.displayName)
                                }
                            }
                        }
                        val snapshot = ReportsAggregator.build(
                            days = currentDays,
                            usage = usage,
                            attempts = attempts,
                            skills = skills,
                            appLabels = labels,
                        )
                        val cooldownMinutes = policy?.defaultCooldownMinutes ?: 15
                        val activeChild = rawChildren.find { it.childId == activeChildId }
                        val childName = activeChild?.displayName ?: "Child"
                        mapToReportsUi(
                            activeChildId = activeChildId,
                            selectedDays = currentDays,
                            children = childProfiles,
                            snapshot = snapshot,
                            childName = childName,
                            cooldownMinutes = cooldownMinutes,
                        )
                    }.combine(isRefreshing) { reportsUi, refreshing ->
                        UiState.Success(reportsUi.copy(isRefreshing = refreshing)) as UiState<ReportsUi>
                    }
                }
            }.catch { error ->
                emit(UiState.Error(AppErrorMapper.from(error)))
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectChild(childId: String) {
        if (selectedChildIdFlow.value != childId) {
            selectedChildIdFlow.value = childId
        }
    }

    fun selectDays(days: Int) {
        val target = when {
            days <= AppConfig.REPORTS_TODAY_DAYS -> AppConfig.REPORTS_TODAY_DAYS
            days >= AppConfig.REPORTS_EXTENDED_DAYS -> AppConfig.REPORTS_EXTENDED_DAYS
            else -> AppConfig.REPORTS_DEFAULT_DAYS
        }
        if (selectedDaysFlow.value != target) {
            selectedDaysFlow.value = target
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            refreshTrigger.value += 1
            kotlinx.coroutines.delay(400)
            isRefreshing.value = false
        }
    }

    fun retry() = startObserving()

    fun load() = startObserving()

    private fun mapToReportsUi(
        activeChildId: String,
        selectedDays: Int,
        children: List<ChildReportProfile>,
        snapshot: ChildReportsSnapshot,
        childName: String,
        cooldownMinutes: Int,
        isRefreshing: Boolean = false,
    ): ReportsUi {
        val allocations = buildAppAllocations(snapshot.byApp, snapshot.totalMinutes)
        val eduPct = snapshot.educationalPercent
        val score = calculateBalancedScore(snapshot, eduPct)
        val (scoreLabel, scoreDesc) = balancedScoreCopy(score, snapshot)
        val strengths = buildStrengths(snapshot)
        val growth = buildGrowth(snapshot)

        val eduAppMinutes = snapshot.byApp
            .filter { AppInventoryCategorizer.classify(it.packageName, it.displayName).category == AppInventoryCategorizer.Category.EDUCATIONAL }
            .sumOf { it.minutes }
        val quizEstimatedMinutes = snapshot.quiz.passedCount * 3
        val aiFocusMinutes = (eduAppMinutes + quizEstimatedMinutes).coerceAtLeast(0)

        return ReportsUi(
            selectedChildId = activeChildId,
            selectedDays = selectedDays,
            children = children,
            report = snapshot,
            selectedChildName = childName,
            periodLabel = periodLabel(selectedDays),
            dailyAverageFormatted = formatDuration(snapshot.averageMinutesPerDay),
            totalScreenTimeFormatted = formatDuration(snapshot.totalMinutes),
            totalAiFocusTimeFormatted = formatDuration(aiFocusMinutes),
            trendPercentage = snapshot.trendPercent,
            streakDays = snapshot.streakDays,
            masteryRatePercent = snapshot.masteryRatePercent,
            balancedScore = score,
            balancedScoreLabel = scoreLabel,
            balancedScoreDescription = scoreDesc,
            educationalPercent = eduPct,
            appAllocations = allocations,
            quizPassPercent = snapshot.quiz.accuracyPercent,
            focusSessionCount = snapshot.quiz.attemptCount,
            focusSessionsPassed = snapshot.quiz.passedCount,
            extraMinutesEarned = snapshot.quiz.extraMinutesEarned,
            focusSessions = snapshot.recentAttempts.map { attempt ->
                FocusSessionItem(
                    attemptId = attempt.attemptId,
                    title = attempt.topics.firstOrNull()
                        ?.let(ReportsAggregator::topicLabel)
                        ?: "Quiz session",
                    passed = attempt.passed,
                    scoreLabel = "${attempt.score}/${attempt.total}",
                    extraMinutes = attempt.extraMinutesGranted,
                )
            },
            aiTopics = snapshot.topics.take(6).map { topic ->
                AiTopicInsight(
                    topic = ReportsAggregator.topicLabel(topic.topic),
                    levelLabel = ReportsAggregator.levelLabel(topic.level),
                    tierLabel = topic.tierLabel.replaceFirstChar { it.uppercase() },
                    weak = topic.weak,
                    masteredCount = topic.masteredConcepts.size,
                )
            },
            demonstratedStrengths = strengths,
            growthRecommended = growth,
            masteredConceptCount = snapshot.topics.sumOf { it.masteredConcepts.size },
            cooldownIncidentsCount = snapshot.quiz.failedCount,
            avgCooldownRestMinutes = cooldownMinutes,
            hasUsageData = snapshot.totalMinutes > 0,
            hasQuizData = snapshot.quiz.attemptCount > 0,
            hasSkillData = snapshot.topics.isNotEmpty(),
            isRefreshing = isRefreshing,
        )
    }

    private fun emptyReportsUi(children: List<ChildReportProfile>, days: Int): ReportsUi {
        val emptyReport = ReportsAggregator.build(days, emptyList(), emptyList(), emptyList())
        return ReportsUi(
            selectedChildId = "",
            selectedDays = days,
            children = children,
            report = emptyReport,
            selectedChildName = children.firstOrNull()?.displayName ?: "Your Child",
            periodLabel = periodLabel(days),
            dailyAverageFormatted = "0m",
            totalScreenTimeFormatted = "0m",
            totalAiFocusTimeFormatted = "0m",
            trendPercentage = null,
            streakDays = 0,
            masteryRatePercent = null,
            balancedScore = null,
            balancedScoreLabel = "Ready",
            balancedScoreDescription = "Activity insights appear once device usage syncs",
            educationalPercent = null,
            appAllocations = emptyList(),
            quizPassPercent = null,
            focusSessionCount = 0,
            focusSessionsPassed = 0,
            extraMinutesEarned = 0,
            focusSessions = emptyList(),
            aiTopics = emptyList(),
            demonstratedStrengths = emptyList(),
            growthRecommended = emptyList(),
            masteredConceptCount = 0,
            cooldownIncidentsCount = 0,
            avgCooldownRestMinutes = 0,
            hasUsageData = false,
            hasQuizData = false,
            hasSkillData = false,
        )
    }

    private fun periodLabel(days: Int): String = when (days) {
        AppConfig.REPORTS_TODAY_DAYS -> "Today"
        AppConfig.REPORTS_EXTENDED_DAYS -> "Last 30 days"
        else -> "Last 7 days"
    }

    private fun formatDuration(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val h = safe / 60
        val m = safe % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    private fun buildAppAllocations(
        apps: List<AppMinutesRollup>,
        totalMinutes: Int,
    ): List<AppAllocationItem> {
        if (apps.isEmpty() || totalMinutes <= 0) return emptyList()
        val colors = listOf(
            MeritStaticColors.Primary,
            MeritStaticColors.Secondary,
            MeritStaticColors.Tertiary,
            MeritStaticColors.PrimaryFixedDim,
            MeritStaticColors.SecondaryFixedDim,
        )
        val pctBase = apps.sumOf { it.minutes }.coerceAtLeast(1)
        return apps.take(8).mapIndexed { index, app ->
            val pct = ((app.minutes * 100) / pctBase).coerceIn(0, 100)
            val classification = AppInventoryCategorizer.classify(app.packageName, app.displayName)
            val (type, label) = when {
                app.packageName == "_other" -> AppCategoryType.GENERAL to "Other"
                classification.category == AppInventoryCategorizer.Category.EDUCATIONAL ->
                    categorizeEducational(app) to "Learn"
                classification.category == AppInventoryCategorizer.Category.ENTERTAINMENT ->
                    AppCategoryType.MEDIA to "Media"
                classification.category == AppInventoryCategorizer.Category.SYSTEM ->
                    AppCategoryType.GENERAL to "System"
                else -> AppCategoryType.GENERAL to "App"
            }
            AppAllocationItem(
                packageName = app.packageName,
                displayName = app.displayName,
                minutes = app.minutes,
                percentage = pct.coerceAtLeast(if (app.minutes > 0) 1 else 0),
                categoryLabel = label,
                categoryType = type,
                segmentColor = colors[index % colors.size],
            )
        }
    }

    private fun categorizeEducational(app: AppMinutesRollup): AppCategoryType {
        val lower = (app.displayName + " " + app.packageName).lowercase()
        return when {
            lower.contains("duo") || lower.contains("spell") || lower.contains("read") ||
                lower.contains("abc") || lower.contains("language") -> AppCategoryType.LANGUAGE
            lower.contains("scratch") || lower.contains("code") || lower.contains("logic") ||
                lower.contains("puzzle") -> AppCategoryType.LOGIC
            else -> AppCategoryType.LEARN
        }
    }

    private fun calculateBalancedScore(report: ChildReportsSnapshot, eduPercent: Int?): Int? {
        if (!report.hasAnyData) return null
        val accuracy = report.quiz.accuracyPercent
        val edu = eduPercent
        return when {
            accuracy != null && edu != null ->
                ((accuracy * 50 + edu * 35) / 100 + 15).coerceIn(0, 100)
            accuracy != null ->
                ((accuracy * 70) / 100 + 20).coerceIn(0, 100)
            edu != null ->
                ((edu * 70) / 100 + 15).coerceIn(0, 100)
            report.totalMinutes > 0 -> 50
            else -> null
        }
    }

    private fun balancedScoreCopy(score: Int?, report: ChildReportsSnapshot): Pair<String, String> {
        if (score == null) {
            return "—" to "No quiz or usage data in this period yet"
        }
        val label = when {
            score >= 80 -> "Great!"
            score >= 65 -> "Good"
            score >= 45 -> "Fair"
            else -> "Needs attention"
        }
        val desc = when {
            report.quiz.accuracyPercent != null && report.educationalPercent != null ->
                "$score/100 · ${report.educationalPercent}% learning apps · ${report.quiz.accuracyPercent}% quiz pass"
            report.educationalPercent != null ->
                "$score/100 · ${report.educationalPercent}% educational screen time"
            report.quiz.accuracyPercent != null ->
                "$score/100 · ${report.quiz.accuracyPercent}% quiz pass rate"
            else -> "$score/100 · Based on recorded activity"
        }
        return label to desc
    }

    private fun buildStrengths(report: ChildReportsSnapshot): List<String> {
        val fromTopics = report.topics
            .filter { !it.weak && it.level >= 3 }
            .map { "${ReportsAggregator.topicLabel(it.topic)} · ${ReportsAggregator.levelLabel(it.level)}" }
        val fromMastered = report.topics
            .flatMap { it.masteredConcepts }
            .distinct()
            .take(2)
            .map { ReportsAggregator.humanizeToken(it) }
        return (fromTopics + fromMastered).distinct().take(4)
    }

    private fun buildGrowth(report: ChildReportsSnapshot): List<String> {
        val hints = report.practiceHints.map { it.title }
        val weakTopics = report.topics.filter { it.weak }.map {
            "${ReportsAggregator.topicLabel(it.topic)} · needs practice"
        }
        return (hints + weakTopics).distinct().take(4)
    }
}

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    onAdjustQuizFocus: (childId: String) -> Unit = {},
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        UiState.Loading -> ReportsSkeletonContent(onBack = onBack)
        UiState.Empty -> ReportsSkeletonContent(onBack = onBack)
        is UiState.Error -> ErrorState(message = current.error.userMessage, onRetry = viewModel::retry)
        is UiState.Success -> ReportsContent(
            data = current.data,
            onBack = onBack,
            onSelectChild = viewModel::selectChild,
            onSelectDays = viewModel::selectDays,
            onAdjustQuizFocus = onAdjustQuizFocus,
            onRefresh = viewModel::refresh,
        )
    }
}

@Composable
fun ReportsContent(
    data: ReportsUi,
    onBack: () -> Unit,
    onSelectChild: (String) -> Unit,
    onSelectDays: (Int) -> Unit,
    onAdjustQuizFocus: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    var selectedDayIndex by remember(data.selectedChildId, data.selectedDays) {
        mutableIntStateOf(data.report.daily.lastIndex.coerceAtLeast(0))
    }
    val selectedDay: DailyMinutesPoint? = data.report.daily.getOrNull(selectedDayIndex)

    val dayAllocations = remember(selectedDay, data.appAllocations, data.report.totalMinutes) {
        val dayApps = selectedDay?.byApp.orEmpty()
        if (dayApps.isNotEmpty() && (selectedDay?.minutes ?: 0) > 0) {
            dayApps.mapIndexed { index, app ->
                val colors = listOf(
                    MeritStaticColors.Primary,
                    MeritStaticColors.Secondary,
                    MeritStaticColors.Tertiary,
                    MeritStaticColors.PrimaryFixedDim,
                    MeritStaticColors.SecondaryFixedDim,
                )
                val base = dayApps.sumOf { it.minutes }.coerceAtLeast(1)
                val classification = AppInventoryCategorizer.classify(app.packageName, app.displayName)
                val type = when (classification.category) {
                    AppInventoryCategorizer.Category.EDUCATIONAL -> AppCategoryType.LEARN
                    AppInventoryCategorizer.Category.ENTERTAINMENT -> AppCategoryType.MEDIA
                    else -> AppCategoryType.GENERAL
                }
                AppAllocationItem(
                    packageName = app.packageName,
                    displayName = app.displayName,
                    minutes = app.minutes,
                    percentage = ((app.minutes * 100) / base).coerceAtLeast(1),
                    categoryLabel = when (type) {
                        AppCategoryType.LEARN -> "Learn"
                        AppCategoryType.MEDIA -> "Media"
                        else -> "App"
                    },
                    categoryType = type,
                    segmentColor = colors[index % colors.size],
                )
            }
        } else {
            data.appAllocations
        }
    }

    Scaffold(
        containerColor = MeritColors.SurfaceContainerLow,
        topBar = {
            ReportsTopBar(
                title = "Reports & Analytics",
                childName = data.selectedChildName,
                onBack = onBack,
                onRefresh = onRefresh,
            )
        },
    ) { paddingValues ->
        MeritPullToRefreshBox(
            isRefreshing = data.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ChildSelectorAndFilterBar(
                    children = data.children,
                    selectedChildId = data.selectedChildId,
                    selectedDays = data.selectedDays,
                    onSelectChild = onSelectChild,
                    onSelectDays = onSelectDays,
                )

                if (!data.report.hasAnyData) {
                    ReportsEmptyActivityCard(
                        childName = data.selectedChildName,
                        periodLabel = data.periodLabel,
                    )
                } else {
                    SummaryMetricCardsGrid(
                        totalAiFocusTime = data.totalAiFocusTimeFormatted,
                        masteryRatePercent = data.masteryRatePercent,
                        masteredConceptCount = data.masteredConceptCount,
                        streakDays = data.streakDays,
                        educationalPercent = data.educationalPercent,
                        trendPercentage = data.trendPercentage,
                        periodLabel = data.periodLabel,
                    )

                    BalancedScoreCard(
                        score = data.balancedScore,
                        label = data.balancedScoreLabel,
                        description = data.balancedScoreDescription,
                        dailyAverage = data.dailyAverageFormatted,
                        totalScreenTime = data.totalScreenTimeFormatted,
                        periodLabel = data.periodLabel,
                        showPerDayAverage = data.selectedDays > 1,
                    )

                    ActivityTrendBarChart(
                        daily = data.report.daily,
                        selectedIndex = selectedDayIndex,
                        onSelectIndex = { selectedDayIndex = it },
                        periodLabel = data.periodLabel,
                    )

                    LearningTrendLineChart(
                        daily = data.report.daily,
                        periodLabel = data.periodLabel,
                    )

                    AppTimeAllocationCard(
                        educationalPercent = data.educationalPercent,
                        allocations = dayAllocations,
                        selectedDayLabel = selectedDay?.let { ReportsAggregator.friendlyDayLabel(it.day) },
                        windowTotalLabel = data.totalScreenTimeFormatted,
                        hasUsageData = data.hasUsageData,
                    )

                    AiLearningSummaryCard(
                        childName = data.selectedChildName,
                        topics = data.aiTopics,
                        strengths = data.demonstratedStrengths,
                        growthTopics = data.growthRecommended,
                        masteredCount = data.masteredConceptCount,
                        onAdjustFocus = {
                            if (data.selectedChildId.isNotBlank()) {
                                onAdjustQuizFocus(data.selectedChildId)
                            }
                        },
                    )

                    FocusSessionsCard(
                        sessionCount = data.focusSessionCount,
                        passedCount = data.focusSessionsPassed,
                        passPercent = data.quizPassPercent,
                        extraMinutes = data.extraMinutesEarned,
                        sessions = data.focusSessions,
                    )

                    BoundaryCooldownLogsCard(
                        cooldownCount = data.cooldownIncidentsCount,
                        avgRestMinutes = data.avgCooldownRestMinutes,
                        periodLabel = data.periodLabel,
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun ReportsTopBar(
    title: String,
    childName: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(
        color = MeritColors.SurfaceContainerLow.copy(alpha = 0.98f),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MeritColors.SurfaceContainer),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MeritColors.OnSurface,
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )
                    if (!childName.isNullOrBlank()) {
                        Text(
                            text = childName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.Primary,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(9999.dp),
                    color = MeritColors.PrimaryContainer.copy(alpha = 0.2f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MeritColors.Primary),
                        )
                        Text(
                            text = "Live",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.Primary,
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MeritColors.SurfaceContainer),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MeritColors.OnSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChildSelectorAndFilterBar(
    children: List<ChildReportProfile>,
    selectedChildId: String,
    selectedDays: Int,
    onSelectChild: (String) -> Unit,
    onSelectDays: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (children.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9999.dp))
                    .background(MeritColors.SurfaceContainer)
                    .padding(4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                children.forEach { child ->
                    val isSelected = child.childId == selectedChildId
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MeritColors.Primary else Color.Transparent,
                        label = "child_pill_bg",
                    )
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .clickable { onSelectChild(child.childId) },
                        color = bgColor,
                        shape = RoundedCornerShape(9999.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ChildAvatar(
                                avatar = child.avatar,
                                size = 24.dp,
                                background = if (isSelected) {
                                    MeritColors.PrimaryContainer
                                } else {
                                    MeritColors.SurfaceContainerHigh
                                },
                                contentDescription = "${child.displayName} avatar",
                            )
                            Column {
                                Text(
                                    text = child.displayName,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isSelected) MeritColors.OnPrimary else MeritColors.OnSurfaceVariant,
                                    maxLines = 1,
                                )
                                Text(
                                    text = child.ageLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (isSelected) MeritColors.OnPrimary.copy(alpha = 0.85f) else MeritColors.OnSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9999.dp))
                .background(MeritColors.SurfaceContainer)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PeriodFilterChip(
                label = "Today",
                selected = selectedDays == AppConfig.REPORTS_TODAY_DAYS,
                onClick = { onSelectDays(AppConfig.REPORTS_TODAY_DAYS) },
                modifier = Modifier.weight(1f),
            )
            PeriodFilterChip(
                label = "7 Days",
                selected = selectedDays == AppConfig.REPORTS_DEFAULT_DAYS,
                onClick = { onSelectDays(AppConfig.REPORTS_DEFAULT_DAYS) },
                modifier = Modifier.weight(1f),
            )
            PeriodFilterChip(
                label = "30 Days",
                selected = selectedDays == AppConfig.REPORTS_EXTENDED_DAYS,
                onClick = { onSelectDays(AppConfig.REPORTS_EXTENDED_DAYS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PeriodFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MeritColors.SurfaceContainerLowest else Color.Transparent,
        label = "chip_bg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MeritColors.Primary else MeritColors.OnSurfaceVariant,
        label = "chip_text",
    )
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp))
            .clickable(onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(9999.dp),
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = textColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReportsEmptyActivityCard(
    childName: String,
    periodLabel: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MeritColors.PrimaryFixed.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Spa,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(32.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Ready for $childName's Activity",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Screen time trends, AI focus sessions, and concept mastery will automatically appear here once $childName starts activity in $periodLabel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EmptyStateHighlightRow(
                    icon = Icons.Default.School,
                    title = "AI Focus Time",
                    subtitle = "Automatically measures educational apps & quiz practice",
                )
                EmptyStateHighlightRow(
                    icon = Icons.Default.Psychology,
                    title = "Concept Mastery Tracking",
                    subtitle = "Dynamic skill progression across math, logic, & reading",
                )
                EmptyStateHighlightRow(
                    icon = Icons.Default.AutoGraph,
                    title = "Daily Learning Streaks",
                    subtitle = "Encourages positive digital habits with active streak streaks",
                )
            }
        }
    }
}

@Composable
private fun EmptyStateHighlightRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MeritColors.SurfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MeritColors.SurfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(20.dp),
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
                )
            }
        }
    }
}

@Composable
private fun SummaryMetricCardsGrid(
    totalAiFocusTime: String,
    masteryRatePercent: Int?,
    masteredConceptCount: Int,
    streakDays: Int,
    educationalPercent: Int?,
    trendPercentage: Int?,
    periodLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.School,
                iconTint = MeritColors.Primary,
                iconBg = MeritColors.PrimaryFixed.copy(alpha = 0.5f),
                title = "AI Focus Time",
                value = totalAiFocusTime,
                caption = periodLabel,
                trendPercentage = trendPercentage,
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Psychology,
                iconTint = MeritColors.Tertiary,
                iconBg = MeritColors.TertiaryFixed.copy(alpha = 0.5f),
                title = "Mastery Rate",
                value = if (masteryRatePercent != null) "$masteryRatePercent%" else "—",
                caption = "$masteredConceptCount mastered",
                progressFraction = masteryRatePercent?.let { it / 100f },
                progressColor = MeritColors.Tertiary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LocalFireDepartment,
                iconTint = Color(0xFFD84315),
                iconBg = Color(0xFFFFCCBC),
                title = "Daily Streak",
                value = "$streakDays ${if (streakDays == 1) "day" else "days"}",
                caption = if (streakDays > 0) "Active streak!" else "Start a streak",
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.PieChart,
                iconTint = MeritColors.Secondary,
                iconBg = MeritColors.SecondaryFixed.copy(alpha = 0.5f),
                title = "Activity Ratio",
                value = if (educationalPercent != null) "$educationalPercent%" else "—",
                caption = "Learning apps",
                progressFraction = educationalPercent?.let { it / 100f },
                progressColor = MeritColors.Secondary,
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    value: String,
    caption: String,
    trendPercentage: Int? = null,
    progressFraction: Float? = null,
    progressColor: Color = MeritColors.Primary,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp),
                    )
                }

                if (trendPercentage != null) {
                    TrendBadge(trendPercentage = trendPercentage)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                    maxLines = 1,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MeritColors.OnSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }

            if (progressFraction != null) {
                val animatedFraction by animateFloatAsState(
                    targetValue = progressFraction.coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    label = "metric_progress",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(9999.dp))
                        .background(MeritColors.SurfaceContainerHigh),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(9999.dp))
                            .background(progressColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendBadge(trendPercentage: Int) {
    val isDown = trendPercentage <= 0
    val bg = if (isDown) MeritColors.TertiaryFixed else MeritColors.SecondaryContainer
    val fg = if (isDown) MeritColors.OnTertiaryFixed else MeritColors.OnSecondaryContainer
    Surface(
        shape = RoundedCornerShape(9999.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = if (isDown) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp),
            )
            val sign = if (trendPercentage > 0) "+" else ""
            Text(
                text = "$sign$trendPercentage%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = fg,
            )
        }
    }
}

@Composable
private fun BalancedScoreCard(
    score: Int?,
    label: String,
    description: String,
    dailyAverage: String,
    totalScreenTime: String,
    periodLabel: String,
    showPerDayAverage: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (showPerDayAverage) {
                            "DAILY AVERAGE · $periodLabel".uppercase()
                        } else {
                            "SCREEN TIME · $periodLabel".uppercase()
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (showPerDayAverage) dailyAverage else totalScreenTime,
                            style = TextStyle(
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-1).sp,
                            ),
                            color = MeritColors.OnSurface,
                        )
                        if (showPerDayAverage) {
                            Text(
                                text = "/ day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                    if (showPerDayAverage) {
                        Text(
                            text = "Total $totalScreenTime in period",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MeritColors.TertiaryFixedDim),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Spa,
                                contentDescription = null,
                                tint = MeritColors.OnTertiaryFixed,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Balanced Screen Score",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MeritColors.OnSurface,
                            )
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MeritColors.SurfaceContainerLowest,
                        shadowElevation = 1.dp,
                    ) {
                        Text(
                            text = if (score != null) label else "—",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.Tertiary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTrendBarChart(
    daily: List<DailyMinutesPoint>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    periodLabel: String,
) {
    if (daily.isEmpty()) return
    val maxMinutes = daily.maxOf { it.minutes }.coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Activity Trend",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = "Tap a day to filter app usage · $periodLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                daily.forEachIndexed { index, point ->
                    val selected = index == selectedIndex
                    val fraction = (point.minutes.toFloat() / maxMinutes).coerceIn(0.04f, 1f)
                    val animatedFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(durationMillis = 400),
                        label = "bar_anim",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelectIndex(index) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .fillMaxHeight(animatedFraction)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (selected) MeritColors.Primary else MeritColors.Primary.copy(alpha = 0.35f),
                                ),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                daily.forEachIndexed { index, point ->
                    val selected = index == selectedIndex
                    Text(
                        text = ReportsAggregator.shortDayLabel(point.day),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (daily.size > 10) 9.sp else 11.sp,
                        ),
                        color = if (selected) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Clip,
                    )
                }
            }

            val selected = daily.getOrNull(selectedIndex)
            if (selected != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MeritColors.SurfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = ReportsAggregator.friendlyDayLabel(selected.day),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = formatMinutes(selected.minutes),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.Primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningTrendLineChart(
    daily: List<DailyMinutesPoint>,
    periodLabel: String,
) {
    if (daily.size < 2) return
    val maxMinutes = daily.maxOf { it.minutes }.coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Learning Momentum",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = "Trajectory of active focus · $periodLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.AutoGraph,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            val primaryColor = MeritColors.Primary
            val primaryAlpha = MeritColors.Primary.copy(alpha = 0.25f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(vertical = 8.dp),
            ) {
                val width = size.width
                val height = size.height
                val stepX = width / (daily.size - 1).coerceAtLeast(1)

                val points = daily.mapIndexed { index, point ->
                    val x = index * stepX
                    val y = height - (point.minutes.toFloat() / maxMinutes) * (height * 0.85f)
                    Offset(x, y)
                }

                val strokePath = Path().apply {
                    if (points.isNotEmpty()) {
                        moveTo(points[0].x, points[0].y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val controlX1 = p0.x + (p1.x - p0.x) / 2
                            val controlX2 = p0.x + (p1.x - p0.x) / 2
                            cubicTo(controlX1, p0.y, controlX2, p1.y, p1.x, p1.y)
                        }
                    }
                }

                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryAlpha, Color.Transparent),
                        startY = 0f,
                        endY = height,
                    ),
                )

                drawPath(
                    path = strokePath,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )

                points.forEach { pt ->
                    drawCircle(
                        color = primaryColor,
                        radius = 3.5.dp.toPx(),
                        center = pt,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTimeAllocationCard(
    educationalPercent: Int?,
    allocations: List<AppAllocationItem>,
    selectedDayLabel: String?,
    windowTotalLabel: String,
    hasUsageData: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "App Time Allocation",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = when {
                            !hasUsageData -> "No screen time recorded in this period"
                            educationalPercent != null ->
                                "Educational apps are $educationalPercent% of attributed time" +
                                    (selectedDayLabel?.let { " · $it" } ?: "")
                            else -> "Breakdown for ${selectedDayLabel ?: windowTotalLabel}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.PieChart,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            if (allocations.isEmpty()) {
                Text(
                    text = "App-level minutes appear after the child device uploads usage with per-app totals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(9999.dp))
                        .background(MeritColors.SurfaceContainer),
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        allocations.forEach { app ->
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(app.percentage.coerceAtLeast(1).toFloat())
                                    .background(app.segmentColor),
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allocations.forEach { app -> AppAllocationRow(app = app) }
                }
            }
        }
    }
}

@Composable
private fun AppAllocationRow(app: AppAllocationItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            val icon: ImageVector = when (app.categoryType) {
                AppCategoryType.LEARN -> Icons.Default.AutoStories
                AppCategoryType.MEDIA -> Icons.Default.PlayCircle
                AppCategoryType.LANGUAGE -> Icons.Default.Spellcheck
                AppCategoryType.LOGIC -> Icons.Default.Extension
                AppCategoryType.GENERAL -> Icons.Default.Apps
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(app.segmentColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MeritColors.OnPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MeritColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${app.percentage}% · ${formatMinutes(app.minutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(9999.dp),
            color = MeritColors.SurfaceContainer,
        ) {
            Text(
                text = app.categoryLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun FocusSessionsCard(
    sessionCount: Int,
    passedCount: Int,
    passPercent: Int?,
    extraMinutes: Int,
    sessions: List<FocusSessionItem>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                            .clip(CircleShape)
                            .background(MeritColors.PrimaryFixed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MeritColors.OnPrimaryFixed,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Focus Sessions",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = if (sessionCount == 0) {
                                "No quiz sessions in this period"
                            } else {
                                "$passedCount of $sessionCount passed · +${extraMinutes}m earned"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
                if (passPercent != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$passPercent%",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.Primary,
                        )
                        Text(
                            text = "Pass rate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }

            if (sessions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sessions.take(5).forEach { session ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MeritColors.SurfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MeritColors.OnSurface,
                                    )
                                    Text(
                                        text = "Score ${session.scoreLabel}" +
                                            if (session.extraMinutes > 0) " · +${session.extraMinutes}m" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MeritColors.OnSurfaceVariant,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(9999.dp),
                                    color = if (session.passed) {
                                        MeritColors.TertiaryFixed
                                    } else {
                                        MeritColors.SecondaryContainer
                                    },
                                ) {
                                    Text(
                                        text = if (session.passed) "Passed" else "Failed",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (session.passed) {
                                            MeritColors.OnTertiaryFixed
                                        } else {
                                            MeritColors.OnSecondaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiLearningSummaryCard(
    childName: String,
    topics: List<AiTopicInsight>,
    strengths: List<String>,
    growthTopics: List<String>,
    masteredCount: Int,
    onAdjustFocus: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MeritColors.PrimaryFixed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MeritColors.OnPrimaryFixed,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "AI Learning Summary",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = if (topics.isEmpty()) {
                                "Learning progress updates after completed quiz sessions"
                            } else {
                                "$masteredCount concepts mastered · ${topics.size} topics tracked"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }

            if (topics.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    topics.take(4).forEach { topic ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MeritColors.SurfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = topic.topic,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MeritColors.OnSurface,
                                    )
                                    Text(
                                        text = "${topic.levelLabel} · ${topic.tierLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MeritColors.OnSurfaceVariant,
                                    )
                                }
                                if (topic.weak) {
                                    Surface(
                                        shape = RoundedCornerShape(9999.dp),
                                        color = MeritColors.SecondaryContainer,
                                    ) {
                                        Text(
                                            text = "Practice",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MeritColors.OnSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (strengths.isNotEmpty()) {
                InsightChipSection(
                    title = "Demonstrated Strengths",
                    icon = Icons.Default.Verified,
                    iconTint = MeritColors.Tertiary,
                    items = strengths,
                    chipColor = MeritColors.SurfaceContainerLow,
                    textColor = MeritColors.Tertiary,
                    leading = Icons.Default.AddCircle,
                )
            }

            if (growthTopics.isNotEmpty()) {
                InsightChipSection(
                    title = "Growth / Practice Recommended",
                    icon = Icons.Default.Flag,
                    iconTint = MeritColors.Secondary,
                    items = growthTopics,
                    chipColor = MeritColors.SecondaryContainer,
                    textColor = MeritColors.OnSecondaryContainer,
                    leading = Icons.AutoMirrored.Filled.MenuBook,
                )
            }

            Button(
                onClick = onAdjustFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeritColors.Primary,
                    contentColor = MeritColors.OnPrimary,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Adjust quiz focus for $childName",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightChipSection(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    items: List<String>,
    chipColor: Color,
    textColor: Color,
    leading: ImageVector,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MeritColors.OnSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                Surface(shape = RoundedCornerShape(9999.dp), color = chipColor) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = leading,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = textColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoundaryCooldownLogsCard(
    cooldownCount: Int,
    avgRestMinutes: Int,
    periodLabel: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MeritColors.SurfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.LockClock,
                    contentDescription = null,
                    tint = MeritColors.OnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Screen Time & Rest Break Logs",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSurface,
                    )
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = MeritColors.SurfaceContainer,
                    ) {
                        Text(
                            text = if (cooldownCount == 0) "Clear" else "$cooldownCount rest periods",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = if (cooldownCount == 0) {
                        "No rest breaks were triggered in $periodLabel. Your child is on track!"
                    } else {
                        "$cooldownCount rest break${if (cooldownCount == 1) "" else "s"} in $periodLabel. " +
                            "Average break duration: ${avgRestMinutes}m. Emergency calls remain available at all times."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun ReportsSkeletonContent(onBack: () -> Unit) {
    Scaffold(
        containerColor = MeritColors.SurfaceContainerLow,
        topBar = {
            ReportsTopBar(
                title = "Reports & Analytics",
                childName = "Loading...",
                onBack = onBack,
                onRefresh = {},
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(9999.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(9999.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShimmerBox(modifier = Modifier.weight(1f).height(110.dp), shape = RoundedCornerShape(18.dp))
                ShimmerBox(modifier = Modifier.weight(1f).height(110.dp), shape = RoundedCornerShape(18.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShimmerBox(modifier = Modifier.weight(1f).height(110.dp), shape = RoundedCornerShape(18.dp))
                ShimmerBox(modifier = Modifier.weight(1f).height(110.dp), shape = RoundedCornerShape(18.dp))
            }
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(20.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(180.dp), shape = RoundedCornerShape(20.dp))
        }
    }
}

@Composable
private fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
) {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer_alpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MeritColors.SurfaceContainerHigh.copy(alpha = alpha)),
    )
}
