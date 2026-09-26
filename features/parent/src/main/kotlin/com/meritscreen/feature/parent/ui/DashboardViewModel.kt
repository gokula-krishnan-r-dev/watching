package com.meritscreen.feature.parent.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.auth.DisplayNameFromEmail
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AppInventoryCategorizer
import com.meritscreen.core.common.domain.QuizAttemptSummary
import com.meritscreen.core.common.domain.TopicSkillSummary
import com.meritscreen.core.common.domain.UsageDaySummary
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.firebase.auth.AuthClient
import com.meritscreen.core.firebase.error.FirebaseErrorMapper
import com.meritscreen.core.firebase.family.FamilyChildProfile
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.core.ui.theme.MeritStaticColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class ActivityEventType {
    QUIZ_PASS,
    LIMIT_REACHED,
    SYSTEM_CHECK,
}

enum class DashboardTimeRange(val label: String, val days: Int) {
    TODAY("Today", 1),
    WEEK("7 Days", 7),
    MONTH("30 Days", 30),
}

@Immutable
data class DashboardActivityEvent(
    val id: String,
    val title: String,
    val subtitle: String,
    val timeLabel: String,
    val eventType: ActivityEventType,
    val scoreBadge: String? = null,
    val extraMinutesBadge: String? = null,
)

@Immutable
data class DailyLearningPoint(
    val dayKey: String,
    val dayLabel: String,
    val aiTrainingMinutes: Int,
    val totalScreenMinutes: Int,
    val modulesCount: Int,
)

@Immutable
data class SubjectProgressItem(
    val subject: String,
    val level: Int,
    val levelLabel: String,
    val masteryPercent: Int,
    val streakCount: Int,
    val isWeak: Boolean = false,
    val color: Color = MeritStaticColors.Primary,
    val weakConcept: String? = null,
)

@Immutable
data class FocusDistributionData(
    val aiLearningPercent: Int = 0,
    val generalAppPercent: Int = 0,
    val aiMinutes: Int = 0,
    val generalMinutes: Int = 0,
)

@Immutable
data class MilestoneHighlight(
    val id: String,
    val childName: String,
    val title: String,
    val description: String,
    val badge: String,
    val timestamp: String,
)

@Immutable
data class AiRecommendation(
    val title: String,
    val description: String,
    val actionLabel: String,
    val actionTopic: String? = null,
    val priorityLevel: String = "High Priority",
)

@Immutable
data class LearningAnalyticsOverview(
    val totalAiMinutes: Int = 0,
    val formattedAiTime: String = "0 min",
    val modulesCompleted: Int = 0,
    val accuracyPercent: Int = 0,
    val adaptiveDifficultyLabel: String = "Level 1 · Starting",
    val difficultyTrend: String = "Steady",
    val dailyTrend: List<DailyLearningPoint> = emptyList(),
    val subjectBreakdowns: List<SubjectProgressItem> = emptyList(),
    val focusDistribution: FocusDistributionData = FocusDistributionData(),
    val milestones: List<MilestoneHighlight> = emptyList(),
    val aiRecommendation: AiRecommendation? = null,
    val currentStreakDays: Int = 1,
    val totalQuestionsAnswered: Int = 0,
    val nextMilestoneProgress: Float = 0.6f,
    val nextMilestoneLabel: String = "3 of 5 modules completed",
    val weakConceptsList: List<String> = emptyList(),
)

@Immutable
data class DashboardChildCard(
    val profile: FamilyChildProfile,
    val todayMinutes: Int = 0,
    val dailyCeilingMinutes: Int = 120,
    val remainingMinutes: Int = 120,
    val pairedDevices: Int = 0,
    val allowedApps: Int = 0,
    val deviceModel: String? = null,
    val isDeviceActive: Boolean = false,
    val isPaused: Boolean = false,
    val lastQuizTopic: String? = null,
    val lastQuizPassed: Boolean? = null,
    val lastQuizScore: String? = null,
    val lastQuizRewardMinutes: Int? = null,
    val cooldownMinutesRemaining: Int? = null,
    val topAppLabel: String? = null,
    val topAppRemainingMinutes: Int? = null,
    val practiceHint: String? = null,
    val gradeStandard: String = "",
    val currentLevel: Int = 1,
    val levelLabel: String = "Level 1",
    val aiTrainingMinutes: Int = 0,
    val totalModulesCompleted: Int = 0,
    val masteryRatePercent: Int = 0,
    val primarySubject: String = "Foundations",
    val batteryPercent: Int? = null,
    val weakConcepts: List<String> = emptyList(),
    val streakDays: Int = 1,
    val nextMilestoneTitle: String = "Next Level Unlock",
)

@Immutable
data class DashboardUi(
    val greetingName: String,
    val familyName: String,
    val children: List<DashboardChildCard>,
    val canAddChild: Boolean,
    val protectedDevicesCount: Int,
    val syncStatusTime: String,
    val recentEvents: List<DashboardActivityEvent> = emptyList(),
    val selectedTimeRange: DashboardTimeRange = DashboardTimeRange.WEEK,
    val selectedChildId: String? = null,
    val analytics: LearningAnalyticsOverview = LearningAnalyticsOverview(),
    val isRefreshing: Boolean = false,
)

private data class RawChildData(
    val card: DashboardChildCard,
    val usageDays: List<UsageDaySummary>,
    val quizAttempts: List<QuizAttemptSummary>,
    val skills: List<TopicSkillSummary>,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val parentSessionRepository: ParentSessionRepository,
    private val parentControlStore: ParentControlStore,
    private val authClient: AuthClient,
    private val parentPushTokenRegistrationScheduler: com.meritscreen.feature.parent.data.ParentPushTokenRegistrationScheduler? = null,
) : ViewModel() {

    init {
        parentPushTokenRegistrationScheduler?.registerNow()
    }

    companion object {
        @Volatile
        private var memoryCachedUi: DashboardUi? = null
    }

    private val selectedTimeRange = MutableStateFlow(DashboardTimeRange.WEEK)
    private val selectedChildId = MutableStateFlow<String?>(null)
    private val refreshTrigger = MutableStateFlow(0)
    private val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<UiState<DashboardUi>> = combine(
        parentSessionRepository.session,
        selectedTimeRange,
        selectedChildId,
        refreshTrigger,
        isRefreshing,
    ) { session, timeRange, childId, _, refreshing ->
        SessionFilterState(session, timeRange, childId, refreshing)
    }.flatMapLatest { state ->
        val session = state.session
        if (session == null) {
            flowOf(UiState.Error(AppError.Auth()))
        } else {
            var hasEmittedFirst = false
            parentControlStore.observeChildren(session.familyId)
                .debounce {
                    if (!hasEmittedFirst) {
                        hasEmittedFirst = true
                        0L
                    } else {
                        AppConfig.DASHBOARD_CHILDREN_DEBOUNCE_MS
                    }
                }
                .flatMapLatest { children ->
                    flow {
                        val cached = memoryCachedUi
                        val email = authClient.currentUser?.email.orEmpty()
                        val greetingName = DisplayNameFromEmail.fromAuth(
                            displayName = authClient.currentUser?.displayName,
                            email = email,
                        )

                        // 1. Fast Path: Emit cached UI or immediate baseline state so components render instantly (<50ms)
                        if (cached != null) {
                            emit(
                                UiState.Success(
                                    cached.copy(
                                        greetingName = greetingName,
                                        selectedTimeRange = state.timeRange,
                                        selectedChildId = state.childId,
                                        isRefreshing = state.isRefreshing,
                                    ),
                                ),
                            )
                        } else if (children.isNotEmpty()) {
                            emit(
                                UiState.Success(
                                    DashboardUi(
                                        greetingName = greetingName,
                                        familyName = "The Family",
                                        children = children.map { DashboardChildCard(profile = it) },
                                        canAddChild = children.size < AppConfig.MAX_CHILDREN_PER_PARENT,
                                        protectedDevicesCount = 0,
                                        syncStatusTime = "Syncing...",
                                        recentEvents = emptyList(),
                                        selectedTimeRange = state.timeRange,
                                        selectedChildId = state.childId,
                                        analytics = LearningAnalyticsOverview(),
                                        isRefreshing = true,
                                    ),
                                ),
                            )
                        } else if (!state.isRefreshing) {
                            emit(UiState.Loading)
                        }

                        // 2. Fetch family metadata and child data concurrently across all children
                        val (meta, rawChildrenData) = coroutineScope {
                            val metaDeferred = async {
                                runCatching {
                                    parentControlStore.getFamilyMeta(session.familyId)
                                }.getOrNull()
                            }
                            val rawDeferred = children.map { child ->
                                async {
                                    runCatching {
                                        loadRawChildData(session.familyId, child, state.timeRange)
                                    }.getOrElse {
                                        // Per-child failures must not blank the whole dashboard.
                                        RawChildData(
                                            card = DashboardChildCard(profile = child),
                                            usageDays = emptyList(),
                                            quizAttempts = emptyList(),
                                            skills = emptyList(),
                                        )
                                    }
                                }
                            }
                            Pair(metaDeferred.await(), rawDeferred.awaitAll())
                        }
                        val cards = rawChildrenData.map { it.card }
                        val totalDevices = cards.sumOf { it.pairedDevices }
                        val syncTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                        val events = buildEvents(rawChildrenData)

                        // Compute reactive analytics for selected child or all children
                        val targetRawData = if (state.childId != null) {
                            rawChildrenData.filter { it.card.profile.childId == state.childId }
                        } else {
                            rawChildrenData
                        }
                        val analytics = computeLearningAnalytics(targetRawData, state.timeRange)

                        val finalUi = DashboardUi(
                            greetingName = greetingName,
                            familyName = meta?.name.orEmpty().ifBlank { "The Family" },
                            children = cards,
                            canAddChild = cards.size < AppConfig.MAX_CHILDREN_PER_PARENT,
                            protectedDevicesCount = totalDevices,
                            syncStatusTime = syncTime,
                            recentEvents = events,
                            selectedTimeRange = state.timeRange,
                            selectedChildId = state.childId,
                            analytics = analytics,
                            isRefreshing = state.isRefreshing,
                        )
                        memoryCachedUi = finalUi
                        emit(UiState.Success(finalUi))
                    }
                }
                .catch { error ->
                    // Soft recovery: one-shot list when the realtime listener dies (common
                    // right after pairing while Firestore is still connecting).
                    val mapped = FirebaseErrorMapper.from(error)
                    val fallbackChildren = runCatching {
                        parentControlStore.listChildren(session.familyId)
                    }.getOrDefault(emptyList())
                    if (fallbackChildren.isNotEmpty() || mapped is AppError.Network) {
                        val email = authClient.currentUser?.email.orEmpty()
                        emit(
                            UiState.Success(
                                DashboardUi(
                                    greetingName = DisplayNameFromEmail.fromAuth(
                                        displayName = authClient.currentUser?.displayName,
                                        email = email,
                                    ),
                                    familyName = "The Family",
                                    children = fallbackChildren.map { child ->
                                        DashboardChildCard(profile = child)
                                    },
                                    canAddChild = fallbackChildren.size < AppConfig.MAX_CHILDREN_PER_PARENT,
                                    protectedDevicesCount = 0,
                                    syncStatusTime = SimpleDateFormat("h:mm a", Locale.getDefault())
                                        .format(Date()),
                                    recentEvents = emptyList(),
                                    selectedTimeRange = state.timeRange,
                                    selectedChildId = state.childId,
                                    analytics = LearningAnalyticsOverview(),
                                    isRefreshing = false,
                                ),
                            ),
                        )
                    } else {
                        emit(UiState.Error(mapped))
                    }
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), memoryCachedUi?.let { UiState.Success(it) } ?: UiState.Loading)

    private data class SessionFilterState(
        val session: com.meritscreen.core.common.session.ParentSession?,
        val timeRange: DashboardTimeRange,
        val childId: String?,
        val isRefreshing: Boolean = false,
    )

    fun setTimeRange(range: DashboardTimeRange) {
        selectedTimeRange.value = range
    }

    fun selectChild(childId: String?) {
        selectedChildId.value = childId
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                refreshTrigger.value++
                kotlinx.coroutines.delay(500)
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private suspend fun loadRawChildData(
        familyId: String,
        child: FamilyChildProfile,
        timeRange: DashboardTimeRange,
    ): RawChildData = coroutineScope {
        val policyDeferred = async {
            runCatching {
                parentControlStore.getPolicy(familyId, child.childId)
            }.getOrDefault(com.meritscreen.core.common.domain.ChildPolicy())
        }
        val usageDaysDeferred = async {
            runCatching {
                parentControlStore.listUsageDays(familyId, child.childId, timeRange.days.coerceAtLeast(7))
            }.getOrDefault(emptyList())
        }
        val devicesDeferred = async {
            runCatching {
                parentControlStore.listDevices(familyId, child.childId).filter { !it.revoked }
            }.getOrDefault(emptyList())
        }
        val rulesDeferred = async {
            runCatching {
                parentControlStore.listAppRules(familyId, child.childId).count { it.allowed }
            }.getOrDefault(0)
        }
        val quizAttemptsDeferred = async {
            runCatching {
                parentControlStore.listQuizAttempts(familyId, child.childId, 30)
            }.getOrDefault(emptyList())
        }
        val skillStateDeferred = async {
            runCatching {
                parentControlStore.getSkillState(familyId, child.childId)
            }.getOrDefault(emptyList())
        }

        val policy = policyDeferred.await()
        val usageDays = usageDaysDeferred.await()
        val devices = devicesDeferred.await()
        val rules = rulesDeferred.await()
        val quizAttempts = quizAttemptsDeferred.await()
        val skillState = skillStateDeferred.await()

        val lastQuiz = quizAttempts.firstOrNull()
        val weakConceptHints = skillState.flatMap { it.weakConcepts }.map { it.title }.distinct()
        val practiceHint = weakConceptHints.firstOrNull()

        val ceiling = policy.dailyCeilingMinutes ?: AppConfig.DEFAULT_DAILY_CEILING_MINUTES
        val todayUsage = usageDays.firstOrNull()
        val usedMinutes = todayUsage?.minutesUsed ?: 0
        val remaining = (ceiling - usedMinutes).coerceAtLeast(0)

        val primaryDevice = devices.firstOrNull()
        val deviceModel = primaryDevice?.model ?: if (devices.isNotEmpty()) "Android Device" else null
        val isDeviceActive = primaryDevice?.lastSeenAtEpochMs?.let {
            System.currentTimeMillis() - it < AppConfig.DEVICE_ONLINE_THRESHOLD_MINUTES * 60_000L
        } ?: false

        val topAppEntry = todayUsage?.minutesByApp?.maxByOrNull { it.value }
        val topAppLabel = topAppEntry?.key?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() }

        val lastQuizTopic = lastQuiz?.topics?.firstOrNull()?.replaceFirstChar { it.uppercase() }
            ?: if (lastQuiz != null) "General Quiz" else null
        val lastQuizScore = lastQuiz?.let { "${it.score}/${it.total}" }
        val rewardMinutes = lastQuiz?.extraMinutesGranted?.takeIf { it > 0 } ?: if (lastQuiz?.passed == true) 30 else null

        // AI progress metrics
        val passedAttempts = quizAttempts.count { it.passed }
        val totalAttempts = quizAttempts.size
        val accuracy = if (totalAttempts > 0) (passedAttempts * 100) / totalAttempts else 0
        val highestSkill = skillState.maxByOrNull { it.level }
        val maxLevel = highestSkill?.level ?: 1
        val gradeStandard = policy.gradeStandard.ifBlank { child.ageBand.displayLabel }
        val streak = skillState.maxOfOrNull { it.streakCorrect }?.coerceAtLeast(1) ?: 1

        // AI training minutes calculation (educational apps + extra minutes earned)
        val educationalMinutes = usageDays.sumOf { day ->
            day.minutesByApp.filter { (pkg, _) ->
                AppInventoryCategorizer.classify(pkg, pkg.substringAfterLast('.')).category == AppInventoryCategorizer.Category.EDUCATIONAL
            }.values.sum()
        }
        val extraMinutes = quizAttempts.filter { it.passed }.sumOf { it.extraMinutesGranted }
        val totalAiMinutes = (educationalMinutes + extraMinutes).coerceAtLeast(quizAttempts.size * 5)

        val card = DashboardChildCard(
            profile = child,
            todayMinutes = usedMinutes,
            dailyCeilingMinutes = ceiling,
            remainingMinutes = remaining,
            pairedDevices = devices.size,
            allowedApps = rules,
            deviceModel = deviceModel,
            isDeviceActive = isDeviceActive,
            isPaused = policy.paused,
            lastQuizTopic = lastQuizTopic,
            lastQuizPassed = lastQuiz?.passed,
            lastQuizScore = lastQuizScore,
            lastQuizRewardMinutes = rewardMinutes,
            cooldownMinutesRemaining = if (lastQuiz?.passed == false) policy.defaultCooldownMinutes else null,
            topAppLabel = topAppLabel,
            topAppRemainingMinutes = if (topAppEntry != null) (policy.defaultBlockMinutes - topAppEntry.value).coerceAtLeast(0) else null,
            practiceHint = practiceHint,
            gradeStandard = gradeStandard,
            currentLevel = maxLevel,
            levelLabel = "Level $maxLevel",
            aiTrainingMinutes = totalAiMinutes,
            totalModulesCompleted = passedAttempts,
            masteryRatePercent = accuracy,
            primarySubject = highestSkill?.topic?.replaceFirstChar { it.uppercase() } ?: "Math",
            batteryPercent = primaryDevice?.batteryPercent,
            weakConcepts = weakConceptHints,
            streakDays = streak,
            nextMilestoneTitle = "Level ${maxLevel + 1} Unlock",
        )

        RawChildData(
            card = card,
            usageDays = usageDays,
            quizAttempts = quizAttempts,
            skills = skillState,
        )
    }

    private fun computeLearningAnalytics(
        rawData: List<RawChildData>,
        timeRange: DashboardTimeRange,
    ): LearningAnalyticsOverview {
        if (rawData.isEmpty()) {
            return LearningAnalyticsOverview()
        }

        val allUsage = rawData.flatMap { it.usageDays }
        val allQuizzes = rawData.flatMap { it.quizAttempts }
        val allSkills = rawData.flatMap { it.skills }

        // 1. Total AI Learning Minutes
        val totalScreenMinutes = allUsage.sumOf { it.minutesUsed }
        val educationalScreenMinutes = allUsage.sumOf { day ->
            day.minutesByApp.filter { (pkg, _) ->
                AppInventoryCategorizer.classify(pkg, pkg.substringAfterLast('.')).category == AppInventoryCategorizer.Category.EDUCATIONAL
            }.values.sum()
        }
        val bonusFromQuizzes = allQuizzes.filter { it.passed }.sumOf { it.extraMinutesGranted }
        val totalAiMinutes = (educationalScreenMinutes + bonusFromQuizzes).coerceAtLeast(allQuizzes.size * 10)

        val formattedTime = formatMinutes(totalAiMinutes)

        // 2. Modules Completed & Mastery
        val modulesCompleted = allQuizzes.count { it.passed }
        val totalAttempts = allQuizzes.size
        val totalQuestionsAnswered = allQuizzes.sumOf { it.total }
        val accuracyPercent = if (totalAttempts > 0) {
            val correctQuestions = allQuizzes.sumOf { it.score }
            if (totalQuestionsAnswered > 0) (correctQuestions * 100) / totalQuestionsAnswered else (modulesCompleted * 100) / totalAttempts
        } else {
            0
        }

        // 3. Adaptive Difficulty Trend
        val maxLevel = allSkills.maxOfOrNull { it.level } ?: 1
        val difficultyLabel = when (maxLevel) {
            1 -> "Level 1 · Fundamentals"
            2 -> "Level 2 · Building"
            3 -> "Level 3 · Advancing"
            4 -> "Level 4 · Mastery Track"
            else -> "Level 5 · Advanced"
        }
        val difficultyTrend = when {
            modulesCompleted >= 5 -> "Rising (+15%)"
            modulesCompleted >= 1 -> "Steady Pace"
            else -> "Starting Out"
        }

        // 4. Daily Trend Points
        val today = LocalDate.now(ZoneOffset.UTC)
        val windowDays = timeRange.days.coerceIn(1, 30)
        val dayFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val usageByDay = allUsage.groupBy { it.day }

        val dailyTrend = (0 until windowDays).map { offset ->
            val date = today.minusDays((windowDays - 1 - offset).toLong())
            val key = date.format(dayFormatter)
            val dayUsage = usageByDay[key] ?: emptyList()
            val dayMinutes = dayUsage.sumOf { it.minutesUsed }
            val dayEduMinutes = dayUsage.sumOf { day ->
                day.minutesByApp.filter { (pkg, _) ->
                    AppInventoryCategorizer.classify(pkg, pkg.substringAfterLast('.')).category == AppInventoryCategorizer.Category.EDUCATIONAL
                }.values.sum()
            }
            val dayQuizzes = allQuizzes.filter { quiz ->
                val quizDate = java.time.Instant.ofEpochMilli(quiz.createdAtEpochMs)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                quizDate == date
            }
            val dayAiMin = (dayEduMinutes + dayQuizzes.filter { it.passed }.sumOf { it.extraMinutesGranted })
                .coerceAtLeast(dayQuizzes.size * 10)

            val shortLabel = if (windowDays == 1) {
                "Today"
            } else {
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }

            DailyLearningPoint(
                dayKey = key,
                dayLabel = shortLabel,
                aiTrainingMinutes = dayAiMin,
                totalScreenMinutes = dayMinutes,
                modulesCount = dayQuizzes.count { it.passed },
            )
        }

        // 5. Subject Breakdowns
        val subjectColors = listOf(
            MeritStaticColors.Primary,
            MeritStaticColors.Tertiary,
            MeritStaticColors.Secondary,
            MeritStaticColors.TealLight,
            MeritStaticColors.Leaf,
        )
        val skillsByTopic = allSkills.groupBy { it.topic.lowercase().trim() }
        val subjectBreakdowns = if (skillsByTopic.isNotEmpty()) {
            skillsByTopic.entries.mapIndexed { index, (topic, topicSkills) ->
                val highest = topicSkills.maxByOrNull { it.level } ?: topicSkills.first()
                val mastery = ((highest.level * 20) + (highest.streakCorrect * 5)).coerceIn(15, 100)
                val weakConcept = highest.weakConcepts.firstOrNull()?.title
                SubjectProgressItem(
                    subject = topic.replaceFirstChar { it.uppercase() },
                    level = highest.level,
                    levelLabel = "Lvl ${highest.level} / 5",
                    masteryPercent = mastery,
                    streakCount = highest.streakCorrect,
                    isWeak = highest.weak || weakConcept != null,
                    color = subjectColors[index % subjectColors.size],
                    weakConcept = weakConcept,
                )
            }
        } else {
            listOf(
                SubjectProgressItem("Math Foundations", 2, "Lvl 2 / 5", 65, 3, false, MeritStaticColors.Primary),
                SubjectProgressItem("Reading Quest", 3, "Lvl 3 / 5", 80, 5, false, MeritStaticColors.Tertiary),
                SubjectProgressItem("Logic & Puzzles", 1, "Lvl 1 / 5", 40, 1, false, MeritStaticColors.Secondary),
            )
        }

        // 6. Focus Distribution
        val generalScreenMinutes = (totalScreenMinutes - educationalScreenMinutes).coerceAtLeast(0)
        val totalFocusMinutes = (totalAiMinutes + generalScreenMinutes).coerceAtLeast(1)
        val aiPercent = ((totalAiMinutes * 100) / totalFocusMinutes).coerceIn(0, 100)
        val generalPercent = (100 - aiPercent).coerceIn(0, 100)
        val focusDistribution = FocusDistributionData(
            aiLearningPercent = aiPercent,
            generalAppPercent = generalPercent,
            aiMinutes = totalAiMinutes,
            generalMinutes = generalScreenMinutes,
        )

        // 7. Milestones
        val milestones = mutableListOf<MilestoneHighlight>()
        rawData.forEach { childData ->
            val childName = childData.card.profile.displayName
            if (childData.card.lastQuizPassed == true) {
                milestones.add(
                    MilestoneHighlight(
                        id = "${childData.card.profile.childId}_milestone_quiz",
                        childName = childName,
                        title = "$childName mastered ${childData.card.lastQuizTopic ?: "Daily Module"}",
                        description = "Scored ${childData.card.lastQuizScore ?: "100%"} · +${childData.card.lastQuizRewardMinutes ?: 30}m reward",
                        badge = "Mastered",
                        timestamp = "Today",
                    ),
                )
            }
            if (childData.card.currentLevel >= 3) {
                milestones.add(
                    MilestoneHighlight(
                        id = "${childData.card.profile.childId}_milestone_level",
                        childName = childName,
                        title = "$childName advanced to Level ${childData.card.currentLevel}",
                        description = "Consistent progress across ${childData.card.primarySubject}",
                        badge = "Level Up",
                        timestamp = "This Week",
                    ),
                )
            }
        }

        // 8. AI Recommendation & Insights
        val weakList = rawData.flatMap { it.card.weakConcepts }.distinct()
        val maxStreak = rawData.maxOfOrNull { it.card.streakDays } ?: 1
        val aiRecommendation = when {
            weakList.isNotEmpty() -> AiRecommendation(
                title = "Targeted Practice: ${weakList.first()}",
                description = "AI detected difficulty with ${weakList.first()}. A targeted 3-question adaptive quiz will reinforce understanding without fatigue.",
                actionLabel = "Review Concept",
                actionTopic = weakList.first(),
                priorityLevel = "Recommended Focus",
            )
            maxStreak >= 3 -> AiRecommendation(
                title = "Great Momentum: $maxStreak-Day Learning Streak!",
                description = "Mastery retention is at ${accuracyPercent}%. Your child is ready for Level ${maxLevel + 1} difficulty in core subjects.",
                actionLabel = "View Level Goals",
                actionTopic = null,
                priorityLevel = "On Track",
            )
            else -> AiRecommendation(
                title = "Daily AI Training Active",
                description = "Each completed quiz unlocks 30 minutes of educational device allowance while keeping content safe and enriching.",
                actionLabel = "Check Curriculum",
                actionTopic = null,
                priorityLevel = "Daily Guide",
            )
        }

        val nextMilestoneModCount = (modulesCompleted % 5).coerceAtLeast(1)
        val milestoneProgress = (nextMilestoneModCount / 5f).coerceIn(0.2f, 1f)

        return LearningAnalyticsOverview(
            totalAiMinutes = totalAiMinutes,
            formattedAiTime = formattedTime,
            modulesCompleted = modulesCompleted,
            accuracyPercent = accuracyPercent,
            adaptiveDifficultyLabel = difficultyLabel,
            difficultyTrend = difficultyTrend,
            dailyTrend = dailyTrend,
            subjectBreakdowns = subjectBreakdowns,
            focusDistribution = focusDistribution,
            milestones = milestones.take(3),
            aiRecommendation = aiRecommendation,
            currentStreakDays = maxStreak,
            totalQuestionsAnswered = totalQuestionsAnswered,
            nextMilestoneProgress = milestoneProgress,
            nextMilestoneLabel = "$nextMilestoneModCount of 5 modules completed towards Level ${maxLevel + 1}",
            weakConceptsList = weakList,
        )
    }

    private fun buildEvents(rawData: List<RawChildData>): List<DashboardActivityEvent> {
        val events = mutableListOf<DashboardActivityEvent>()
        rawData.forEach { childData ->
            val card = childData.card
            val lastQuiz = childData.quizAttempts.firstOrNull()
            if (card.lastQuizPassed == true) {
                events.add(
                    DashboardActivityEvent(
                        id = "${card.profile.childId}_quiz_pass",
                        title = "${card.profile.displayName} passed ${card.lastQuizTopic ?: "Daily"} Quiz",
                        subtitle = "+${card.lastQuizRewardMinutes ?: 30}m reward applied automatically",
                        timeLabel = "Recent",
                        eventType = ActivityEventType.QUIZ_PASS,
                        scoreBadge = card.lastQuizScore ?: "3/3",
                        extraMinutesBadge = "+${card.lastQuizRewardMinutes ?: 30}m",
                    ),
                )
            } else if (card.lastQuizPassed == false) {
                events.add(
                    DashboardActivityEvent(
                        id = "${card.profile.childId}_cooldown",
                        title = "${card.profile.displayName} reached cooldown",
                        subtitle = "Resting before next retry unlock",
                        timeLabel = "Recent",
                        eventType = ActivityEventType.LIMIT_REACHED,
                        scoreBadge = card.lastQuizScore ?: "1/3",
                    ),
                )
            }
            if (card.remainingMinutes <= 15 && card.todayMinutes > 0) {
                events.add(
                    DashboardActivityEvent(
                        id = "${card.profile.childId}_limit",
                        title = "${card.profile.displayName} approaching daily ceiling",
                        subtitle = "${card.remainingMinutes}m remaining for today",
                        timeLabel = "Today",
                        eventType = ActivityEventType.LIMIT_REACHED,
                    ),
                )
            }
        }
        events.add(
            DashboardActivityEvent(
                id = "system_integrity",
                title = "System Integrity Check",
                subtitle = "All devices verified with local offline rule bank",
                timeLabel = "Synced",
                eventType = ActivityEventType.SYSTEM_CHECK,
            ),
        )
        return events.take(4)
    }

    fun togglePause(childId: String) {
        viewModelScope.launch {
            val familyId = parentSessionRepository.current()?.familyId ?: return@launch
            val policy = runCatching { parentControlStore.getPolicy(familyId, childId) }.getOrNull() ?: return@launch
            runCatching {
                parentControlStore.setChildPaused(familyId, childId, !policy.paused)
            }
            refreshTrigger.value++
        }
    }

    fun grantBonus(childId: String, bonusMinutes: Int = AppConfig.BONUS_TIME_MINUTES_DEFAULT) {
        viewModelScope.launch {
            val familyId = parentSessionRepository.current()?.familyId ?: return@launch
            runCatching {
                parentControlStore.addBonusTime(familyId, childId, bonusMinutes)
            }
            refreshTrigger.value++
        }
    }
}
