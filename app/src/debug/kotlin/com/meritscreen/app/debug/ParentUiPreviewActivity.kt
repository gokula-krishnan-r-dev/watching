package com.meritscreen.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.DeviceSummary
import com.meritscreen.core.firebase.family.FamilyChildProfile
import com.meritscreen.core.ui.components.AppUsageSegment
import com.meritscreen.core.firebase.pairing.PairingOffer
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritScreenTheme
import com.meritscreen.feature.authentication.ui.ChildPairingContent
import com.meritscreen.feature.authentication.ui.DevicePairedSuccessfullyContent
import com.meritscreen.feature.authentication.ui.ParentPairingCodeContent
import com.meritscreen.feature.authentication.ui.ParentPairingRulesInfo
import com.meritscreen.feature.onboarding.domain.ChildDraft
import com.meritscreen.feature.onboarding.ui.AddChildContent
import com.meritscreen.feature.onboarding.ui.AiLearningContextScreen
import com.meritscreen.feature.onboarding.ui.CreateFamilyContent
import com.meritscreen.feature.onboarding.ui.LegalConsentContent
import com.meritscreen.feature.onboarding.ui.OnboardingAllowlistScreen
import com.meritscreen.feature.onboarding.ui.RoleSelectContent
import com.meritscreen.feature.onboarding.ui.SetParentPinContent
import com.meritscreen.feature.onboarding.ui.SetupCompleteScreen
import com.meritscreen.feature.onboarding.ui.TimelineScheduleScreen
import com.meritscreen.feature.onboarding.ui.ValueTourScreen
import com.meritscreen.feature.parent.ui.AccountContent
import com.meritscreen.feature.parent.ui.AccountUi
import com.meritscreen.feature.parent.ui.ActivityEventType
import com.meritscreen.feature.parent.ui.AiTopicInsight
import com.meritscreen.feature.parent.ui.AllowlistContent
import com.meritscreen.feature.parent.ui.AllowlistUi
import com.meritscreen.feature.parent.ui.AppAllocationItem
import com.meritscreen.feature.parent.ui.AppCategoryType
import com.meritscreen.feature.parent.ui.ChildDetailContent
import com.meritscreen.feature.parent.ui.ChildDetailUi
import com.meritscreen.feature.parent.ui.DeviceConnectionStatus
import com.meritscreen.feature.parent.ui.ChildReportProfile
import com.meritscreen.feature.parent.ui.DashboardActivityEvent
import com.meritscreen.feature.parent.ui.DashboardChildCard
import com.meritscreen.feature.parent.ui.DashboardContent
import com.meritscreen.feature.parent.ui.DashboardTimeRange
import com.meritscreen.feature.parent.ui.DashboardUi
import com.meritscreen.feature.parent.ui.AiRecommendation
import com.meritscreen.feature.parent.ui.DailyLearningPoint
import com.meritscreen.feature.parent.ui.FocusDistributionData
import com.meritscreen.feature.parent.ui.LearningAnalyticsOverview
import com.meritscreen.feature.parent.ui.MilestoneHighlight
import com.meritscreen.feature.parent.ui.SubjectProgressItem
import com.meritscreen.feature.parent.ui.FocusSessionItem
import com.meritscreen.feature.parent.ui.ReportsContent
import com.meritscreen.feature.parent.ui.ReportsUi
import com.meritscreen.feature.parent.ui.TimeLimitsContent
import com.meritscreen.core.common.domain.SessionPhase
import com.meritscreen.feature.child.data.ChildLocalProfile
import com.meritscreen.feature.child.domain.SessionSnapshot
import com.meritscreen.feature.child.ui.ChildHomeContent
import com.meritscreen.feature.child.ui.ChildHomeUi
import com.meritscreen.feature.child.ui.HomeAppTile
import androidx.compose.ui.platform.LocalContext
import com.meritscreen.feature.child.domain.MiniLesson
import com.meritscreen.feature.child.domain.QuizAnswerFeedback
import com.meritscreen.feature.child.domain.QuizChoice
import com.meritscreen.feature.child.domain.QuizQuestion
import com.meritscreen.feature.child.domain.QuizSessionResult
import com.meritscreen.feature.child.ui.AnswerTeachPane
import com.meritscreen.feature.child.ui.BedtimeLockPane
import com.meritscreen.feature.child.ui.FailLockCooldownPane
import com.meritscreen.feature.child.ui.LockedExplanationPane
import com.meritscreen.feature.child.ui.QuizInterruptPane
import com.meritscreen.feature.child.ui.QuizPassResultPane
import com.meritscreen.feature.child.ui.QuizQuestionPane
import com.meritscreen.feature.child.ui.QuizTtsNarrator
import com.meritscreen.feature.child.ui.QuizUiStep
import com.meritscreen.feature.child.ui.TileBadgeType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ParentUiPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialScreen = when (intent?.getStringExtra("screen")) {
            "S01_ROLE_SELECT", "ROLE_SELECT", "P01_WELCOME", "WELCOME" -> PreviewScreen.ROLE_SELECT
            "S02_LEGAL_CONSENT", "LEGAL_CONSENT" -> PreviewScreen.LEGAL_CONSENT
            "P05_CREATE_FAMILY", "CREATE_FAMILY" -> PreviewScreen.CREATE_FAMILY
            "P06_ADD_CHILD" -> PreviewScreen.ADD_CHILD
            "P02_VALUE_TOUR", "VALUE_TOUR" -> PreviewScreen.VALUE_TOUR
            "P05_TIMELINE_SCHEDULE", "TIMELINE_SCHEDULE" -> PreviewScreen.TIMELINE_SCHEDULE
            "P04_ONBOARDING_ALLOWLIST", "ONBOARDING_ALLOWLIST" -> PreviewScreen.ONBOARDING_ALLOWLIST
            "P03D_AI_LEARNING_CONTEXT", "AI_LEARNING_CONTEXT" -> PreviewScreen.AI_LEARNING_CONTEXT
            "P06_SETUP_COMPLETE", "SETUP_COMPLETE" -> PreviewScreen.SETUP_COMPLETE
            "POST_ONBOARDING_MODAL" -> PreviewScreen.POST_ONBOARDING_MODAL
            "P07_SET_PARENT_PIN", "SET_PARENT_PIN" -> PreviewScreen.SET_PARENT_PIN
            "P08_PAIRING", "PAIRING_CODE", "PARENT_PAIRING" -> PreviewScreen.PARENT_PAIRING
            "P13_ALLOWLIST" -> PreviewScreen.ALLOWLIST
            "P14_TIME_LIMITS" -> PreviewScreen.TIME_LIMITS
            "C04_CHILD_PAIRING" -> PreviewScreen.CHILD_PAIRING
            "P08C_DEVICE_PAIRED" -> PreviewScreen.DEVICE_PAIRED
            "P12_CHILD_DETAIL" -> PreviewScreen.CHILD_DETAIL
            "P17_REPORTS" -> PreviewScreen.REPORTS
            "P18_ACCOUNT", "ACCOUNT" -> PreviewScreen.ACCOUNT
            "CHILD_WIZARD", "ADD_CHILD_WIZARD", "ADD_CHILD_DASHBOARD" -> PreviewScreen.ADD_CHILD
            "C05_CHILD_LAUNCHER", "C05_LAUNCHER", "CHILD_LAUNCHER" -> PreviewScreen.CHILD_LAUNCHER
            "C07B_INTERRUPT", "C07B_QUIZ_INTERRUPT" -> PreviewScreen.C07B_INTERRUPT
            "C09_QUESTION", "C09_QUIZ_QUESTION" -> PreviewScreen.C09_QUESTION
            "C09B_TEACH", "C09B_ANSWER_TEACH" -> PreviewScreen.C09B_TEACH
            "C10_PASS", "C10_QUIZ_RESULT_PASS" -> PreviewScreen.C10_PASS
            "C12_COOLDOWN", "C12_FAIL_LOCK_CALM_COOLDOWN" -> PreviewScreen.C12_COOLDOWN
            "C16_BEDTIME", "C16_BEDTIME_LOCK" -> PreviewScreen.C16_BEDTIME
            "LOCKED_TEACHING", "C12_LOCKED_PAUSE" -> PreviewScreen.LOCKED_TEACHING
            else -> PreviewScreen.DASHBOARD
        }
        val initialHideDebugBar = intent?.getBooleanExtra("hideDebugBar", false) ?: false
        setContent {
            MeritScreenTheme {
                ParentUiPreviewRoot(
                    initialScreen = initialScreen,
                    initialHideDebugBar = initialHideDebugBar,
                )
            }
        }
    }
}

enum class PreviewScreen {
    ROLE_SELECT,
    LEGAL_CONSENT,
    VALUE_TOUR,
    CREATE_FAMILY,
    DASHBOARD,
    CHILD_DETAIL,
    ADD_CHILD,
    TIMELINE_SCHEDULE,
    ONBOARDING_ALLOWLIST,
    SET_PARENT_PIN,
    AI_LEARNING_CONTEXT,
    SETUP_COMPLETE,
    POST_ONBOARDING_MODAL,
    PARENT_PAIRING,
    ALLOWLIST,
    TIME_LIMITS,
    CHILD_PAIRING,
    DEVICE_PAIRED,
    REPORTS,
    ACCOUNT,
    CHILD_LAUNCHER,
    C07B_INTERRUPT,
    C09_QUESTION,
    C09B_TEACH,
    C10_PASS,
    C12_COOLDOWN,
    C16_BEDTIME,
    LOCKED_TEACHING,
}

@Composable
fun ParentUiPreviewRoot(
    initialScreen: PreviewScreen = PreviewScreen.DASHBOARD,
    initialHideDebugBar: Boolean = false,
) {
    var currentScreen by remember { mutableStateOf(initialScreen) }
    var isDebugSwitcherVisible by remember { mutableStateOf(!initialHideDebugBar) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val previewTts = remember { QuizTtsNarrator(context) }
    DisposableEffect(Unit) {
        onDispose {
            previewTts.shutdown()
        }
    }

    val mockQuizQuestion = remember {
        QuizQuestion(
            id = "preview_q_math_01",
            ageBand = AgeBand.AGE_7_TO_9,
            topic = "Math Foundations",
            conceptId = "div_fair_sharing",
            conceptTitle = "Fair Sharing & Grouping",
            difficulty = 2,
            prompt = "What is 48 ÷ 6?",
            choices = listOf(
                QuizChoice(id = "c1", text = "6", correct = false),
                QuizChoice(id = "c2", text = "7", correct = false),
                QuizChoice(id = "c3", text = "8", correct = true),
                QuizChoice(id = "c4", text = "9", correct = false),
            ),
            whyCorrect = "48 divided by 6 equals 8 because 8 × 6 = 48.",
            whyWrongByChoice = mapOf(
                "c1" to "6 × 6 = 36, which is 12 less than 48.",
                "c2" to "7 × 6 = 42, which is 6 less than 48.",
                "c4" to "9 × 6 = 54, which is 6 more than 48.",
            ),
            conceptExplainer = "Division splits an entire pool into equal portions. When 48 items are partitioned into 6 groups, each pile receives 8.",
            language = "en",
            interactionType = "TAP_TEXT",
            miniLesson = MiniLesson(
                title = "Fair Sharing & Grouping",
                bodyLines = listOf(
                    "Division splits an entire pool into equal portions.",
                    "48 items partitioned into 6 groups gives 8 each.",
                    "Remember: 8 × 6 = 48!",
                ),
                illustrationAssetId = "sim_division_boxes",
            ),
        )
    }

    val mockQuizFeedback = remember {
        QuizAnswerFeedback(
            correct = true,
            resultLine = "Spot on Leo! ✨",
            whyLine = "48 ÷ 6 = 8 because 8 × 6 = 48.",
            conceptLine = "Fair Sharing & Grouping",
            nextLevel = 3,
        )
    }

    val mockQuizResult = remember {
        QuizSessionResult(
            passed = true,
            correctCount = 3,
            total = 3,
            percent = 100,
        )
    }

    // Leo's state
    var leoPaused by remember { mutableStateOf(false) }
    var leoBonusMinutes by remember { mutableIntStateOf(0) }
    var bonusToast by remember { mutableStateOf<String?>(null) }

    // Maya's state
    var mayaPaused by remember { mutableStateOf(true) }
    var mayaBonusMinutes by remember { mutableIntStateOf(0) }

    val leoProfile = remember {
        FamilyChildProfile(
            childId = "c_leo",
            displayName = "Leo",
            ageBand = AgeBand.AGE_7_TO_9,
            avatar = AvatarPreset.RABBIT,
            language = "en",
        )
    }

    val mayaProfile = remember {
        FamilyChildProfile(
            childId = "c_maya",
            displayName = "Maya",
            ageBand = AgeBand.AGE_3_TO_6,
            avatar = AvatarPreset.OWL,
            language = "en",
        )
    }

    var dashboardTimeRange by remember { mutableStateOf(DashboardTimeRange.WEEK) }
    var dashboardSelectedChildId by remember { mutableStateOf<String?>(null) }

    val dashboardData = DashboardUi(
        greetingName = "Sarah",
        familyName = "The Miller Family",
        protectedDevicesCount = 2,
        syncStatusTime = "9:41 AM",
        canAddChild = true,
        selectedTimeRange = dashboardTimeRange,
        selectedChildId = dashboardSelectedChildId,
        children = listOf(
            DashboardChildCard(
                profile = leoProfile,
                todayMinutes = 75,
                dailyCeilingMinutes = 120 + leoBonusMinutes,
                remainingMinutes = (45 + leoBonusMinutes).coerceAtLeast(0),
                pairedDevices = 1,
                allowedApps = 6,
                deviceModel = "Galaxy Tab S9",
                isDeviceActive = true,
                isPaused = leoPaused,
                lastQuizTopic = "Math Foundations",
                lastQuizPassed = true,
                lastQuizScore = "3/3",
                lastQuizRewardMinutes = 30,
                topAppLabel = "YouTube Kids",
                topAppRemainingMinutes = 18,
                practiceHint = "Multiplication speed",
                gradeStandard = "Grade 3",
                currentLevel = 4,
                levelLabel = "Level 4",
                aiTrainingMinutes = 75,
                totalModulesCompleted = 6,
                masteryRatePercent = 92,
                primarySubject = "Math",
            ),
            DashboardChildCard(
                profile = mayaProfile,
                todayMinutes = 45,
                dailyCeilingMinutes = 90 + mayaBonusMinutes,
                remainingMinutes = (45 + mayaBonusMinutes).coerceAtLeast(0),
                pairedDevices = 1,
                allowedApps = 4,
                deviceModel = "Galaxy Tab A",
                isDeviceActive = false,
                isPaused = mayaPaused,
                lastQuizTopic = "Reading Quest",
                lastQuizPassed = true,
                lastQuizScore = "5/5",
                lastQuizRewardMinutes = 15,
                topAppLabel = "Bedtime Mode",
                topAppRemainingMinutes = 0,
                practiceHint = "Sight words",
                gradeStandard = "Early Learner",
                currentLevel = 2,
                levelLabel = "Level 2",
                aiTrainingMinutes = 45,
                totalModulesCompleted = 4,
                masteryRatePercent = 88,
                primarySubject = "Reading",
            ),
        ),
        analytics = LearningAnalyticsOverview(
            totalAiMinutes = if (dashboardSelectedChildId == "c_maya") 45 else if (dashboardSelectedChildId == "c_leo") 75 else 120,
            formattedAiTime = if (dashboardSelectedChildId == "c_maya") "45 min" else if (dashboardSelectedChildId == "c_leo") "1h 15m" else "2h",
            modulesCompleted = if (dashboardSelectedChildId == "c_maya") 4 else if (dashboardSelectedChildId == "c_leo") 6 else 10,
            accuracyPercent = if (dashboardSelectedChildId == "c_maya") 88 else if (dashboardSelectedChildId == "c_leo") 92 else 90,
            adaptiveDifficultyLabel = "Level 4 · Advancing",
            difficultyTrend = "Rising (+15%)",
            dailyTrend = listOf(
                DailyLearningPoint("2026-09-16", "Mon", 25, 60, 2),
                DailyLearningPoint("2026-09-17", "Tue", 40, 80, 3),
                DailyLearningPoint("2026-09-18", "Wed", 55, 95, 4),
                DailyLearningPoint("2026-09-19", "Thu", 30, 70, 2),
                DailyLearningPoint("2026-09-20", "Fri", 50, 85, 3),
                DailyLearningPoint("2026-09-21", "Sat", 65, 110, 5),
                DailyLearningPoint("2026-09-22", "Sun", 45, 75, 3),
            ),
            subjectBreakdowns = listOf(
                SubjectProgressItem("Math Foundations", 4, "Lvl 4 / 5", 92, 5, false, MeritColors.Primary),
                SubjectProgressItem("Reading Quest", 3, "Lvl 3 / 5", 85, 4, false, MeritColors.Tertiary),
                SubjectProgressItem("Logic & Problem Solving", 2, "Lvl 2 / 5", 68, 2, false, MeritColors.Secondary),
                SubjectProgressItem("Science Explorer", 2, "Lvl 2 / 5", 60, 1, false, MeritColors.Leaf),
            ),
            focusDistribution = FocusDistributionData(
                aiLearningPercent = 70,
                generalAppPercent = 30,
                aiMinutes = 120,
                generalMinutes = 50,
            ),
            milestones = listOf(
                MilestoneHighlight(
                    id = "m1",
                    childName = "Leo",
                    title = "Leo mastered Math Foundations",
                    description = "Passed 3/3 with 100% accuracy · +30m unlocked",
                    badge = "Mastered",
                    timestamp = "Today",
                ),
                MilestoneHighlight(
                    id = "m2",
                    childName = "Maya",
                    title = "Maya completed Reading Quest",
                    description = "5/5 questions correct · New streak started",
                    badge = "Level Up",
                    timestamp = "Yesterday",
                ),
            ),
            aiRecommendation = AiRecommendation(
                title = "Targeted Practice: Multiplication Tables",
                description = "AI detected retention gaps in recent sessions. A 10-minute micro-lesson is ready to help boost confidence.",
                actionLabel = "Start Micro-Lesson",
                actionTopic = "Multiplication Tables",
                priorityLevel = "Recommended Focus",
            ),
            currentStreakDays = 5,
            nextMilestoneProgress = 0.6f,
            nextMilestoneLabel = "3 of 5 modules completed towards Level 5",
        ),
        recentEvents = listOf(
            DashboardActivityEvent(
                id = "e1",
                title = "Leo unlocked 30m screen time",
                subtitle = "Math Foundations Quiz passed (3/3)",
                timeLabel = "9:15 AM",
                eventType = ActivityEventType.QUIZ_PASS,
            ),
            DashboardActivityEvent(
                id = "e2",
                title = "Maya reached daily limit",
                subtitle = "YouTube Kids paused automatically",
                timeLabel = "8:45 AM",
                eventType = ActivityEventType.LIMIT_REACHED,
            ),
            DashboardActivityEvent(
                id = "e3",
                title = "Leo device synced successfully",
                subtitle = "Galaxy Tab S9 • 84% battery",
                timeLabel = "8:30 AM",
                eventType = ActivityEventType.SYSTEM_CHECK,
            ),
        ),
    )

    val primaryDevice = remember {
        DeviceSummary(
            deviceId = "d_pixel",
            platform = "android",
            model = "Galaxy Tab S9",
            osVersion = "Android 14",
            batteryPercent = 84,
            launcherDefault = true,
            lastSeenAtEpochMs = System.currentTimeMillis() - 120_000,
        )
    }

    val childDetailData = ChildDetailUi(
        profile = leoProfile,
        policy = ChildPolicy(
            dailyCeilingMinutes = 120,
            bonusMinutesToday = leoBonusMinutes,
            paused = leoPaused,
            questionsPerQuiz = 8,
            passScorePercent = 88,
        ),
        devices = listOf(primaryDevice),
        primaryDevice = primaryDevice,
        connectionStatus = if (leoPaused) {
            DeviceConnectionStatus.Paused
        } else {
            DeviceConnectionStatus.Connected
        },
        connectionStatusLabel = if (leoPaused) "Device Paused" else "Connected",
        deviceModelAndOs = "Galaxy Tab S9 • Android 14",
        batteryPercent = 84,
        lastSyncedLabel = "Synced 2m ago",
        launcherStatusLabel = "Merit Launcher Active",
        explorationTierLabel = "Exploration Tier • Age 7–9",
        todayMinutes = 75,
        dailyCeilingMinutes = 120 + leoBonusMinutes,
        remainingMinutes = (45 + leoBonusMinutes).coerceAtLeast(0),
        isPaused = leoPaused,
        hasUsageBreakdown = true,
        appUsageBreakdown = listOf(
            AppUsageSegment("Khan Academy", 45, MeritColors.Primary),
            AppUsageSegment("YouTube Kids", 30, MeritColors.Secondary),
        ),
        hasQuizData = true,
        quizScorePercent = 88,
        quizScoreFraction = "7 / 8 Correct",
        quizFeedbackNote = "Passed this unlock check",
        strengthsText = "Addition, Phonetics",
        practiceGoalText = "Word Problems",
        curriculumLevelText = "Adaptive Grade 3 Band",
        allowedAppCount = 6,
        emergencyContactCount = 2,
        bonusToastMessage = bonusToast,
    )

    // P06 Add Child state
    var addChildName by remember { mutableStateOf("Leo") }
    var addChildBand by remember { mutableStateOf(AgeBand.AGE_7_TO_9) }
    var addChildAvatar by remember { mutableStateOf(AvatarPreset.RABBIT) }
    var addChildLanguage by remember { mutableStateOf("English (US)") }
    var draftChildren by remember {
        mutableStateOf(
            listOf(
                ChildDraft(
                    localId = "draft_1",
                    name = "Maya",
                    ageBand = AgeBand.AGE_3_TO_6,
                    avatar = AvatarPreset.OWL,
                ),
            ),
        )
    }

    // P13 Allowlist state
    var allowlistUi by remember {
        mutableStateOf(
            AllowlistUi(
                rules = listOf(
                    AppRule(
                        appId = "khan_kids",
                        packageOrBundleId = "org.khankids.android",
                        displayName = "Khan Academy Kids",
                        allowed = true,
                        blockMinutes = 0,
                    ),
                    AppRule(
                        appId = "yt_kids",
                        packageOrBundleId = "com.google.android.apps.youtube.kids",
                        displayName = "YouTube Kids",
                        allowed = true,
                        blockMinutes = 30,
                    ),
                    AppRule(
                        appId = "duo_abc",
                        packageOrBundleId = "com.duolingo.kids",
                        displayName = "Duolingo ABC",
                        allowed = true,
                        blockMinutes = 0,
                    ),
                    AppRule(
                        appId = "roblox",
                        packageOrBundleId = "com.roblox.client",
                        displayName = "Roblox",
                        allowed = false,
                        blockMinutes = 15,
                    ),
                ),
            ),
        )
    }

    // P14 Time limits policy state
    var timePolicy by remember {
        mutableStateOf(
            ChildPolicy(
                dailyCeilingMinutes = 90,
                paused = false,
                questionsPerQuiz = 6,
                passScorePercent = 85,
                defaultCooldownMinutes = 15,
            ),
        )
    }

    // C04 Child pairing state
    var childPairingCode by remember { mutableStateOf("842916") }
    var childUsageAccess by remember { mutableStateOf(false) }
    var childProfileConfirmed by remember { mutableStateOf(true) }

    // P17 Reports preview state
    val reportsChildProfiles = remember {
        listOf(
            ChildReportProfile("c_leo", "Leo", "Age 8", AvatarPreset.RABBIT),
            ChildReportProfile("c_maya", "Maya", "Age 5", AvatarPreset.OWL),
        )
    }
    var reportsSelectedChild by remember { mutableStateOf("c_leo") }
    var reportsSelectedDays by remember { mutableIntStateOf(7) }

    val leoReportsData = ReportsUi(
        selectedChildId = "c_leo",
        selectedDays = reportsSelectedDays,
        children = reportsChildProfiles,
        report = com.meritscreen.core.common.domain.ReportsAggregator.build(
            days = reportsSelectedDays,
            usage = listOf(
                com.meritscreen.core.common.domain.UsageDaySummary("2026-09-18", 102, mapOf("org.khankids.android" to 43, "com.google.android.apps.youtube.kids" to 32, "com.duolingo.kids" to 15, "org.scratchjr.android" to 12)),
            ),
            attempts = listOf(
                com.meritscreen.core.common.domain.QuizAttemptSummary(
                    "q1",
                    System.currentTimeMillis(),
                    listOf("math"),
                    8,
                    8,
                    true,
                    30,
                ),
            ),
            skills = listOf(
                com.meritscreen.core.common.domain.TopicSkillSummary(
                    topic = "math",
                    level = 4,
                    streakCorrect = 3,
                    weak = false,
                    masteredConcepts = listOf("add-within-20"),
                    tierLabel = "advanced",
                ),
            ),
        ),
        selectedChildName = "Leo",
        periodLabel = if (reportsSelectedDays == 7) "Last 7 days" else "Last 30 days",
        dailyAverageFormatted = if (reportsSelectedDays == 7) "15m" else "3m",
        totalScreenTimeFormatted = "1h 42m",
        totalAiFocusTimeFormatted = "30m",
        trendPercentage = null,
        streakDays = 3,
        masteryRatePercent = 100,
        balancedScore = 92,
        balancedScoreLabel = "Great!",
        balancedScoreDescription = "92/100 · 69% learning apps · 100% quiz pass",
        educationalPercent = 69,
        appAllocations = listOf(
            AppAllocationItem("org.khankids.android", "Khan Academy Kids", 43, 42, "Learn", AppCategoryType.LEARN, MeritColors.Primary),
            AppAllocationItem("com.google.android.apps.youtube.kids", "YouTube Kids", 32, 31, "Media", AppCategoryType.MEDIA, MeritColors.Secondary),
            AppAllocationItem("com.duolingo.kids", "Duo ABC", 15, 15, "Language", AppCategoryType.LANGUAGE, MeritColors.Tertiary),
            AppAllocationItem("org.scratchjr.android", "Scratch Jr", 12, 12, "Logic", AppCategoryType.LOGIC, MeritColors.PrimaryFixedDim),
        ),
        quizPassPercent = 100,
        focusSessionCount = 1,
        focusSessionsPassed = 1,
        extraMinutesEarned = 30,
        focusSessions = listOf(
            FocusSessionItem("q1", "Math", true, "8/8", 30),
        ),
        aiTopics = listOf(
            AiTopicInsight("Math", "Level 4 of 5 · Strong", "Advanced", false, 1),
        ),
        demonstratedStrengths = listOf("Math · Level 4 of 5 · Strong"),
        growthRecommended = emptyList(),
        masteredConceptCount = 1,
        cooldownIncidentsCount = 0,
        avgCooldownRestMinutes = 15,
        hasUsageData = true,
        hasQuizData = true,
        hasSkillData = true,
    )

    val mayaReportsData = ReportsUi(
        selectedChildId = "c_maya",
        selectedDays = reportsSelectedDays,
        children = reportsChildProfiles,
        report = com.meritscreen.core.common.domain.ReportsAggregator.build(
            days = reportsSelectedDays,
            usage = listOf(
                com.meritscreen.core.common.domain.UsageDaySummary("2026-09-18", 48, mapOf("com.duolingo.kids" to 26, "org.khankids.android" to 15)),
            ),
            attempts = emptyList(),
            skills = emptyList(),
        ),
        selectedChildName = "Maya",
        periodLabel = if (reportsSelectedDays == 7) "Last 7 days" else "Last 30 days",
        dailyAverageFormatted = if (reportsSelectedDays == 7) "7m" else "2m",
        totalScreenTimeFormatted = "48m",
        totalAiFocusTimeFormatted = "0m",
        trendPercentage = null,
        streakDays = 0,
        masteryRatePercent = null,
        balancedScore = 75,
        balancedScoreLabel = "Good",
        balancedScoreDescription = "75/100 · 100% educational screen time",
        educationalPercent = 100,
        appAllocations = listOf(
            AppAllocationItem("com.duolingo.kids", "Duo ABC", 26, 63, "Language", AppCategoryType.LANGUAGE, MeritColors.Primary),
            AppAllocationItem("org.khankids.android", "Khan Academy Kids", 15, 37, "Learn", AppCategoryType.LEARN, MeritColors.Tertiary),
        ),
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
        avgCooldownRestMinutes = 15,
        hasUsageData = true,
        hasQuizData = false,
        hasSkillData = false,
    )

    var launcherSearchQuery by remember { mutableStateOf("") }
    var launcherCallingEmergency by remember { mutableStateOf(false) }

    val childLauncherData = remember {
        val rules = listOf(
            AppRule("app_yt", "com.google.android.apps.youtube.kids", "YT Kids", allowed = true, blockMinutes = 25),
            AppRule("app_sg", "com.meritscreen.safegram", "SafeGram", allowed = true, blockMinutes = 15),
            AppRule("app_chat", "com.meritscreen.kidschat", "Kids Chat", allowed = true),
            AppRule("app_mc", "com.mojang.minecraftpe", "Minecraft", allowed = true),
            AppRule("app_duo", "com.duolingo.kids", "Duo ABC", allowed = true),
            AppRule("app_disney", "com.disney.disneyplus", "Disney+", allowed = true),
            AppRule("app_scratch", "org.scratchjr.android", "Scratch Jr", allowed = true),
            AppRule("app_gc", "com.google.android.apps.classroom", "Google Classroom", allowed = true),
        )
        val tiles = listOf(
            HomeAppTile(
                rule = rules[0],
                label = "YT Kids",
                subtitle = "Videos",
                badgeLabel = "25m left",
                badgeType = TileBadgeType.TIME_ALERT,
                category = "Media",
                remainingMinutes = 25,
            ),
            HomeAppTile(
                rule = rules[1],
                label = "SafeGram",
                subtitle = "Photos",
                badgeLabel = "15m left",
                badgeType = TileBadgeType.TIME_ALERT,
                category = "Media",
                remainingMinutes = 15,
            ),
            HomeAppTile(
                rule = rules[2],
                label = "Kids Chat",
                subtitle = "Family Safe",
                badgeLabel = "Friends",
                badgeType = TileBadgeType.INFO,
                category = "Social",
            ),
            HomeAppTile(
                rule = rules[3],
                label = "Minecraft",
                subtitle = "Build",
                badgeLabel = "✓ Open",
                badgeType = TileBadgeType.OPEN,
                category = "Game",
            ),
            HomeAppTile(
                rule = rules[4],
                label = "Duo ABC",
                subtitle = "Reading",
                badgeLabel = "∞ Free",
                badgeType = TileBadgeType.FREE,
                category = "Learn",
            ),
            HomeAppTile(
                rule = rules[5],
                label = "Disney+",
                subtitle = "Movies",
                badgeLabel = "Weekend",
                badgeType = TileBadgeType.INFO,
                category = "Media",
            ),
            HomeAppTile(
                rule = rules[6],
                label = "Scratch Jr",
                subtitle = "Coding",
                badgeLabel = "Creative",
                badgeType = TileBadgeType.INFO,
                category = "Logic",
            ),
            HomeAppTile(
                rule = rules[7],
                label = "Classroom",
                subtitle = "Homework",
                badgeLabel = "School",
                badgeType = TileBadgeType.INFO,
                category = "Learn",
            ),
        )
        ChildHomeUi(
            greetingName = "Leo",
            profile = ChildLocalProfile(
                childId = "c_leo",
                familyId = "fam_miller",
                displayName = "Leo",
                ageBand = "Elementary Quest",
                avatarId = "rabbit",
                language = "en",
            ),
            policy = ChildPolicy(dailyCeilingMinutes = 120),
            session = SessionSnapshot(phase = SessionPhase.Idle, minutesUsedToday = 72),
            apps = tiles,
            remainingBlockMinutes = 48,
            remainingCooldownSeconds = 0,
            dailyRemainingMinutes = 48,
            usedTodayMinutes = 72,
            dailyCeilingMinutes = 120,
            balanceStatus = "Good balance",
            quietTimeHint = "Quiet time starts at 7:30 PM",
            explorerLevel = 4,
            syncHint = null,
            isDefaultHome = true,
        )
    }

    var previewLegalConsent by remember { mutableStateOf(false) }
    var previewFamilyName by remember { mutableStateOf("The Millers") }
    var previewPin by remember { mutableStateOf("842") }
    var previewConfirmPin by remember { mutableStateOf("") }

    BackHandler(enabled = currentScreen != PreviewScreen.DASHBOARD) {
        currentScreen = PreviewScreen.DASHBOARD
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MeritColors.SurfaceContainerLow,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (isDebugSwitcherVisible) 56.dp else 0.dp),
            ) {
                when (currentScreen) {
                PreviewScreen.ROLE_SELECT -> {
                    RoleSelectContent(
                        onParentChosen = { currentScreen = PreviewScreen.DASHBOARD },
                        onChildChosen = { currentScreen = PreviewScreen.CHILD_PAIRING },
                    )
                }
                PreviewScreen.LEGAL_CONSENT -> {
                    LegalConsentContent(
                        consentGiven = previewLegalConsent,
                        onConsentChanged = { previewLegalConsent = it },
                        onContinue = { currentScreen = PreviewScreen.VALUE_TOUR },
                        onBack = { currentScreen = PreviewScreen.ROLE_SELECT },
                    )
                }
                PreviewScreen.VALUE_TOUR -> {
                    ValueTourScreen(
                        onContinue = { currentScreen = PreviewScreen.ADD_CHILD },
                        onSkip = { currentScreen = PreviewScreen.ADD_CHILD },
                        onBack = { currentScreen = PreviewScreen.LEGAL_CONSENT },
                    )
                }
                PreviewScreen.CREATE_FAMILY -> {
                    CreateFamilyContent(
                        familyName = previewFamilyName,
                        onFamilyNameChanged = { previewFamilyName = it },
                        onContinue = { currentScreen = PreviewScreen.ADD_CHILD },
                        onBack = { currentScreen = PreviewScreen.LEGAL_CONSENT },
                    )
                }
                PreviewScreen.TIMELINE_SCHEDULE -> {
                    val previewChild = draftChildren.lastOrNull()
                    val previewChildName = previewChild?.name
                        ?: addChildName.takeIf { it.isNotBlank() }
                        ?: "your child"
                    TimelineScheduleScreen(
                        childName = previewChildName,
                        avatar = previewChild?.avatar ?: addChildAvatar,
                        onContinue = { currentScreen = PreviewScreen.ONBOARDING_ALLOWLIST },
                        onBack = { currentScreen = PreviewScreen.ADD_CHILD },
                    )
                }
                PreviewScreen.ONBOARDING_ALLOWLIST -> {
                    OnboardingAllowlistScreen(
                        onContinue = { currentScreen = PreviewScreen.SET_PARENT_PIN },
                        onBack = { currentScreen = PreviewScreen.TIMELINE_SCHEDULE },
                    )
                }
                PreviewScreen.SET_PARENT_PIN -> {
                    SetParentPinContent(
                        pin = previewPin,
                        confirmPin = previewConfirmPin,
                        formError = null,
                        onPinChanged = { previewPin = it },
                        onConfirmPinChanged = { previewConfirmPin = it },
                        onSave = { currentScreen = PreviewScreen.AI_LEARNING_CONTEXT },
                        onBack = { currentScreen = PreviewScreen.ONBOARDING_ALLOWLIST },
                    )
                }
                PreviewScreen.AI_LEARNING_CONTEXT -> {
                    val previewChild = draftChildren.lastOrNull()
                    val previewChildName = previewChild?.name
                        ?: addChildName.takeIf { it.isNotBlank() }
                        ?: "your child"
                    val previewBand = previewChild?.ageBand ?: addChildBand
                    val previewGrade = when (previewBand) {
                        AgeBand.AGE_3_TO_6 -> "Kindergarten"
                        AgeBand.AGE_7_TO_9 -> "3rd Grade"
                        AgeBand.AGE_10_TO_12 -> "6th Grade"
                    }
                    val previewAvatar = previewChild?.avatar ?: addChildAvatar
                    AiLearningContextScreen(
                        childName = previewChildName,
                        grade = previewGrade,
                        avatarEmoji = previewAvatar.emoji,
                        onContinue = { currentScreen = PreviewScreen.SETUP_COMPLETE },
                        onBack = { currentScreen = PreviewScreen.SET_PARENT_PIN },
                    )
                }
                PreviewScreen.SETUP_COMPLETE -> {
                    val previewChild = draftChildren.lastOrNull()
                    val previewChildName = previewChild?.name
                        ?: addChildName.takeIf { it.isNotBlank() }
                        ?: "your child"
                    val previewBand = previewChild?.ageBand ?: addChildBand
                    val previewGrade = when (previewBand) {
                        AgeBand.AGE_3_TO_6 -> "Kindergarten"
                        AgeBand.AGE_7_TO_9 -> "3rd Grade"
                        AgeBand.AGE_10_TO_12 -> "6th Grade"
                    }
                    SetupCompleteScreen(
                        childName = previewChildName,
                        grade = previewGrade,
                        avatar = previewChild?.avatar ?: addChildAvatar,
                        autoRedirect = false,
                        onOpenDashboard = { currentScreen = PreviewScreen.POST_ONBOARDING_MODAL },
                    )
                }
                PreviewScreen.POST_ONBOARDING_MODAL -> {
                    DashboardContent(
                        data = dashboardData,
                        onOpenChild = { _ -> currentScreen = PreviewScreen.CHILD_DETAIL },
                        onOpenAccount = { },
                        onOpenNotifications = { },
                        onAddChild = { currentScreen = PreviewScreen.ADD_CHILD },
                        showPostOnboardingModal = true,
                    )
                }
                PreviewScreen.PARENT_PAIRING -> {
                    ParentPairingCodeContent(
                        offer = PairingOffer(
                            code = "842916",
                            secret = "sec_test_123",
                            expiresAtEpochMs = System.currentTimeMillis() + 600_000L,
                            qrPayload = "meritscreen://pair?c=842916&s=sec_test_123",
                            childId = "c_leo",
                            familyId = "fam_miller",
                        ),
                        isLoading = false,
                        error = null,
                        onRefresh = {},
                        onSkip = { currentScreen = PreviewScreen.DASHBOARD },
                        onBack = { currentScreen = PreviewScreen.SET_PARENT_PIN },
                        childName = "Leo",
                        ageBandLabel = "Ages 7–9",
                        explorationLabel = "Elementary Quest",
                        avatar = AvatarPreset.RABBIT,
                    )
                }
                PreviewScreen.DASHBOARD -> {
                    DashboardContent(
                        data = dashboardData,
                        onOpenChild = { _ -> currentScreen = PreviewScreen.CHILD_DETAIL },
                        onOpenAccount = { currentScreen = PreviewScreen.ACCOUNT },
                        onOpenNotifications = { /* No-op in preview */ },
                        onAddChild = { currentScreen = PreviewScreen.ADD_CHILD },
                        onTogglePause = { childId ->
                            if (childId == "c_leo") leoPaused = !leoPaused
                            else if (childId == "c_maya") mayaPaused = !mayaPaused
                        },
                        onAddBonus = { childId ->
                            if (childId == "c_leo") leoBonusMinutes += 15
                            else if (childId == "c_maya") mayaBonusMinutes += 15
                        },
                        onSelectTimeRange = { dashboardTimeRange = it },
                        onSelectChild = { dashboardSelectedChildId = it },
                    )
                }
                PreviewScreen.CHILD_DETAIL -> {
                    ChildDetailContent(
                        data = childDetailData,
                        onBack = { currentScreen = PreviewScreen.DASHBOARD },
                        onTogglePause = {
                            leoPaused = !leoPaused
                        },
                        onAddBonus = {
                            leoBonusMinutes += 15
                            bonusToast = "✨ 15 minutes added to Leo's daily ceiling!"
                            scope.launch {
                                delay(3500)
                                bonusToast = null
                            }
                        },
                    )
                }
                PreviewScreen.ADD_CHILD -> {
                    AddChildContent(
                        name = addChildName,
                        ageBand = addChildBand,
                        avatar = addChildAvatar,
                        language = addChildLanguage,
                        formError = null,
                        children = draftChildren,
                        onNameChanged = { addChildName = it },
                        onAgeBandSelected = { addChildBand = it },
                        onAvatarSelected = { addChildAvatar = it },
                        onLanguageSelected = { addChildLanguage = it },
                        onAddChild = {
                            if (addChildName.isNotBlank()) {
                                draftChildren = draftChildren + ChildDraft(
                                    localId = "draft_${System.currentTimeMillis()}",
                                    name = addChildName,
                                    ageBand = addChildBand,
                                    avatar = addChildAvatar,
                                )
                                addChildName = ""
                            }
                        },
                        onRemoveChild = { localId ->
                            draftChildren = draftChildren.filterNot { it.localId == localId }
                        },
                        onSaveAndContinue = { currentScreen = PreviewScreen.TIMELINE_SCHEDULE },
                        onBack = { currentScreen = PreviewScreen.DASHBOARD },
                    )
                }
                PreviewScreen.ALLOWLIST -> {
                    AllowlistContent(
                        uiState = allowlistUi,
                        childName = "Leo",
                        onBack = { currentScreen = PreviewScreen.DASHBOARD },
                        onToggleAllowed = { rule, allowed ->
                            allowlistUi = allowlistUi.copy(
                                rules = allowlistUi.rules.map {
                                    if (it.packageOrBundleId == rule.packageOrBundleId) it.copy(allowed = allowed) else it
                                },
                            )
                        },
                        onUpdateBlockMinutes = { rule, minutes ->
                            allowlistUi = allowlistUi.copy(
                                rules = allowlistUi.rules.map {
                                    if (it.packageOrBundleId == rule.packageOrBundleId) it.copy(blockMinutes = minutes) else it
                                },
                            )
                        },
                    )
                }
                PreviewScreen.TIME_LIMITS -> {
                    TimeLimitsContent(
                        policy = timePolicy,
                        childName = "Leo",
                        onUpdatePolicy = { transform -> timePolicy = transform(timePolicy) },
                        onBack = { currentScreen = PreviewScreen.DASHBOARD },
                    )
                }
                PreviewScreen.CHILD_PAIRING -> {
                    ChildPairingContent(
                        code = childPairingCode,
                        isSubmitting = false,
                        error = null,
                        usageAccessGranted = childUsageAccess,
                        confirmedProfile = childProfileConfirmed,
                        onCodeChanged = { childPairingCode = it },
                        onToggleUsageAccess = { childUsageAccess = !childUsageAccess },
                        onConfirmProfile = { childProfileConfirmed = it },
                        onCompleteSetup = { currentScreen = PreviewScreen.DEVICE_PAIRED },
                        onBack = { currentScreen = PreviewScreen.DASHBOARD },
                    )
                }
                PreviewScreen.DEVICE_PAIRED -> {
                    DevicePairedSuccessfullyContent(
                        childName = "Leo",
                        ageBandLabel = "Ages 7–9",
                        explorationLabel = "Elementary Quest",
                        avatar = AvatarPreset.RABBIT,
                        deviceName = "Leo's Galaxy Tab S9",
                        batteryPercent = 84,
                        isOnline = true,
                        pairedLabel = "Paired just now",
                        rules = ParentPairingRulesInfo(
                            dailyLimitSubtitle = "1h 30m allowance scheduled for today",
                            allowedAppsSubtitle = "9 approved apps ready on this device",
                            emergencySubtitle = "2 emergency apps stay reachable",
                            activeRuleCount = 3,
                        ),
                        onGoToDashboard = { currentScreen = PreviewScreen.DASHBOARD },
                        onPairAnotherDevice = { currentScreen = PreviewScreen.CHILD_PAIRING },
                        onCustomizeAllowlist = { currentScreen = PreviewScreen.ALLOWLIST },
                    )
                }
                PreviewScreen.REPORTS -> {
                    val activeData = if (reportsSelectedChild == "c_maya") mayaReportsData else leoReportsData
                    ReportsContent(
                        data = activeData,
                        onBack = { currentScreen = PreviewScreen.DASHBOARD },
                        onSelectChild = { reportsSelectedChild = it },
                        onSelectDays = { reportsSelectedDays = it },
                        onAdjustQuizFocus = { currentScreen = PreviewScreen.CHILD_DETAIL },
                    )
                }
                PreviewScreen.ACCOUNT -> {
                    AccountContent(
                        data = AccountUi(
                            email = "gokulakrishnanr812@gmail.com",
                            displayName = "Gokulakrishnanr",
                            familyName = "My Family",
                            childCount = 1,
                            children = listOf(
                                FamilyChildProfile(
                                    childId = "c_leo",
                                    displayName = "Leo",
                                    ageBand = AgeBand.AGE_7_TO_9,
                                    avatar = AvatarPreset.LION,
                                    language = "en",
                                ),
                            ),
                        ),
                        onBack = { currentScreen = PreviewScreen.DASHBOARD },
                        onAddChild = { currentScreen = PreviewScreen.ADD_CHILD },
                        onResetPin = { currentScreen = PreviewScreen.SET_PARENT_PIN },
                        onDeleteFamily = { },
                        onSignOut = { currentScreen = PreviewScreen.ROLE_SELECT },
                    )
                }
                PreviewScreen.CHILD_LAUNCHER -> {
                    ChildHomeContent(
                        data = childLauncherData,
                        searchQuery = launcherSearchQuery,
                        onSearchQueryChange = { launcherSearchQuery = it },
                        isCallingEmergency = launcherCallingEmergency,
                        onStartEmergencyCall = { launcherCallingEmergency = true },
                        onEndEmergencyCall = { launcherCallingEmergency = false },
                        onAppTapped = { /* preview tap */ },
                        onOpenQuiz = { /* preview tap */ },
                        onOpenPin = { currentScreen = PreviewScreen.DASHBOARD },
                        onOpenLauncherSetup = { /* preview tap */ },
                        onRefreshRules = { /* preview tap */ },
                    )
                }
                PreviewScreen.C07B_INTERRUPT -> {
                    QuizInterruptPane(
                        appLabel = "YouTube Kids",
                        childName = "Leo",
                        onStartQuiz = { currentScreen = PreviewScreen.C09_QUESTION },
                        onCloseApp = { currentScreen = PreviewScreen.CHILD_LAUNCHER },
                    )
                }
                PreviewScreen.C09_QUESTION -> {
                    var previewStep by remember {
                        mutableStateOf(
                            QuizUiStep.Question(
                                index = 1,
                                total = 3,
                                question = mockQuizQuestion,
                            ),
                        )
                    }
                    val baselineResource = remember {
                        com.meritscreen.core.common.domain.StaticLearningResourceCatalog.getFor(
                            mockQuizQuestion.conceptId,
                            mockQuizQuestion.topic,
                            mockQuizQuestion.conceptTitle,
                        )
                    }

                    QuizQuestionPane(
                        step = previewStep,
                        childName = "Leo",
                        appLabel = "YouTube Kids",
                        tts = previewTts,
                        onAnswerSelected = { choiceId ->
                            val isCorrect = choiceId == "c3"
                            val correctChoice = mockQuizQuestion.choices.firstOrNull { it.correct }
                            val correctText = correctChoice?.text ?: "8"
                            val whyWrong = mockQuizQuestion.whyWrongByChoice[choiceId]
                                ?: "48 divided by 6 equals 8 because 8 × 6 = 48."
                            previewStep = previewStep.copy(
                                evaluatedChoiceId = choiceId,
                                isCorrect = isCorrect,
                                whyWrongText = whyWrong,
                                correctChoiceText = correctText,
                                resource = baselineResource,
                            )
                        },
                        onProceedNext = { currentScreen = PreviewScreen.C09B_TEACH },
                        onOpenResourceModal = {
                            previewStep = previewStep.copy(showResourceModal = true)
                        },
                        onCloseResourceModal = {
                            previewStep = previewStep.copy(showResourceModal = false)
                        },
                        onPlayVideo = { video ->
                            previewStep = previewStep.copy(activeFullscreenVideo = video)
                        },
                        onCloseVideo = {
                            previewStep = previewStep.copy(activeFullscreenVideo = null)
                        },
                    )
                }
                PreviewScreen.C09B_TEACH -> {
                    AnswerTeachPane(
                        step = QuizUiStep.Feedback(
                            index = 1,
                            total = 3,
                            feedback = mockQuizFeedback,
                            question = mockQuizQuestion,
                        ),
                        childName = "Leo",
                        appLabel = "YouTube Kids",
                        onNext = { currentScreen = PreviewScreen.C10_PASS },
                    )
                }
                PreviewScreen.C10_PASS -> {
                    QuizPassResultPane(
                        result = mockQuizResult,
                        childName = "Leo",
                        appLabel = "YouTube Kids",
                        unlockedMinutes = 30,
                        onContinueToApp = { currentScreen = PreviewScreen.CHILD_LAUNCHER },
                        onGoHome = { currentScreen = PreviewScreen.CHILD_LAUNCHER },
                    )
                }
                PreviewScreen.C12_COOLDOWN -> {
                    FailLockCooldownPane(
                        childName = "Leo",
                        secondsRemaining = 14 * 60 + 20,
                        totalSeconds = 15 * 60,
                        endsAtEpochMs = System.currentTimeMillis() + (14 * 60 + 20) * 1000L,
                        onCooldownFinished = { currentScreen = PreviewScreen.CHILD_LAUNCHER },
                        onCallMom = { /* preview */ },
                        onCallDad = { /* preview */ },
                        onOpenPin = { currentScreen = PreviewScreen.DASHBOARD },
                    )
                }
                PreviewScreen.C16_BEDTIME -> {
                    BedtimeLockPane(
                        childName = "Leo",
                        onCallMom = { /* preview */ },
                        onCallDad = { /* preview */ },
                        onOpenPin = { currentScreen = PreviewScreen.DASHBOARD },
                    )
                }
                PreviewScreen.LOCKED_TEACHING -> {
                    LockedExplanationPane(
                        step = QuizUiStep.LockedExplanation(
                            index = 1,
                            total = 3,
                            feedback = QuizAnswerFeedback(
                                correct = false,
                                resultLine = "Not this one",
                                whyLine = "6 × 6 = 36, which is 12 less than 48.",
                                conceptLine = "Fair Sharing & Grouping",
                                nextLevel = 1,
                            ),
                            correctChoiceText = "8",
                            miniLesson = mockQuizQuestion.miniLesson!!,
                            lockSecondsRemaining = 24,
                            visual = false,
                            question = mockQuizQuestion,
                        ),
                        childName = "Leo",
                        tts = previewTts,
                        onContinue = { currentScreen = PreviewScreen.C09_QUESTION },
                    )
                }
            }
        }

            // Quick Debug Floating Navigation Switcher (Horizontal Scrolling Chips)
            if (isDebugSwitcherVisible) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = MeritColors.InverseSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ElevatedButton(
                            onClick = { isDebugSwitcherVisible = false },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MeritColors.SurfaceContainerHigh,
                            ),
                            modifier = Modifier.padding(end = 6.dp),
                        ) {
                            Text(
                                text = "✕ Hide",
                                style = MaterialTheme.typography.labelSmall,
                                color = MeritColors.OnSurface,
                            )
                        }
                        val screens = listOf(
                            PreviewScreen.ROLE_SELECT to "S01 Welcome",
                            PreviewScreen.LEGAL_CONSENT to "S02 Legal",
                            PreviewScreen.VALUE_TOUR to "P02 Tour",
                            PreviewScreen.CREATE_FAMILY to "P05 Family",
                            PreviewScreen.ADD_CHILD to "P06 Child",
                            PreviewScreen.TIMELINE_SCHEDULE to "P05 Schedule",
                            PreviewScreen.ONBOARDING_ALLOWLIST to "P04 Allowlist",
                            PreviewScreen.SET_PARENT_PIN to "P07 PIN",
                            PreviewScreen.AI_LEARNING_CONTEXT to "P03D AI Prompt",
                            PreviewScreen.SETUP_COMPLETE to "P06 Complete",
                            PreviewScreen.POST_ONBOARDING_MODAL to "Setup Modal",
                            PreviewScreen.PARENT_PAIRING to "P08 QR & PIN",
                            PreviewScreen.DASHBOARD to "P11 Dash",
                            PreviewScreen.CHILD_DETAIL to "P12 Detail",
                            PreviewScreen.ALLOWLIST to "P13 Allowlist",
                            PreviewScreen.TIME_LIMITS to "P14 Time Limits",
                            PreviewScreen.CHILD_PAIRING to "C04 Child Pairing",
                            PreviewScreen.DEVICE_PAIRED to "P08C Paired",
                            PreviewScreen.REPORTS to "P17 Reports",
                            PreviewScreen.ACCOUNT to "P18 Account",
                            PreviewScreen.CHILD_LAUNCHER to "C05 Launcher",
                            PreviewScreen.C07B_INTERRUPT to "C07B Interrupt",
                            PreviewScreen.C09_QUESTION to "C09 Question",
                            PreviewScreen.C09B_TEACH to "C09B Teach",
                            PreviewScreen.C10_PASS to "C10 Pass",
                            PreviewScreen.C12_COOLDOWN to "C12 Cooldown",
                            PreviewScreen.C16_BEDTIME to "C16 Bedtime",
                            PreviewScreen.LOCKED_TEACHING to "30s Lock Teaching",
                        )
                        screens.forEach { (screen, label) ->
                            val isSelected = currentScreen == screen
                            ElevatedButton(
                                onClick = { currentScreen = screen },
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = if (isSelected) MeritColors.PrimaryFixed else MeritColors.SurfaceContainerHigh,
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) MeritColors.OnPrimaryFixed else MeritColors.OnSurface,
                                )
                            }
                        }
                    }
                }
            } else if (!initialHideDebugBar) {
                ElevatedButton(
                    onClick = { isDebugSwitcherVisible = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MeritColors.Primary,
                    ),
                ) {
                    Text(
                        text = "▲ Screens",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
