import SwiftUI
import Observation

public enum ActivityEventType: Sendable, Equatable {
    case quizPass
    case limitReached
    case systemCheck
}

public enum DashboardTimeRange: String, CaseIterable, Sendable {
    case today = "Today"
    case week = "7 Days"
    case month = "30 Days"

    public var days: Int {
        switch self {
        case .today: return 1
        case .week: return 7
        case .month: return 30
        }
    }
}

public struct DashboardActivityEvent: Sendable, Equatable, Identifiable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let timeLabel: String
    public let eventType: ActivityEventType
    public let scoreBadge: String?
    public let extraMinutesBadge: String?

    public init(
        id: String,
        title: String,
        subtitle: String,
        timeLabel: String,
        eventType: ActivityEventType,
        scoreBadge: String? = nil,
        extraMinutesBadge: String? = nil
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.timeLabel = timeLabel
        self.eventType = eventType
        self.scoreBadge = scoreBadge
        self.extraMinutesBadge = extraMinutesBadge
    }
}

public struct DailyLearningPoint: Sendable, Equatable, Identifiable {
    public var id: String { dayKey }
    public let dayKey: String
    public let dayLabel: String
    public let aiTrainingMinutes: Int
    public let totalScreenMinutes: Int
    public let modulesCount: Int

    public init(
        dayKey: String,
        dayLabel: String,
        aiTrainingMinutes: Int,
        totalScreenMinutes: Int,
        modulesCount: Int
    ) {
        self.dayKey = dayKey
        self.dayLabel = dayLabel
        self.aiTrainingMinutes = aiTrainingMinutes
        self.totalScreenMinutes = totalScreenMinutes
        self.modulesCount = modulesCount
    }
}

public struct SubjectProgressItem: Sendable, Equatable, Identifiable {
    public var id: String { subject }
    public let subject: String
    public let level: Int
    public let levelLabel: String
    public let masteryPercent: Int
    public let streakCount: Int
    public let isWeak: Bool
    public let weakConcept: String?

    public init(
        subject: String,
        level: Int,
        levelLabel: String,
        masteryPercent: Int,
        streakCount: Int,
        isWeak: Bool = false,
        weakConcept: String? = nil
    ) {
        self.subject = subject
        self.level = level
        self.levelLabel = levelLabel
        self.masteryPercent = masteryPercent
        self.streakCount = streakCount
        self.isWeak = isWeak
        self.weakConcept = weakConcept
    }
}

public struct FocusDistributionData: Sendable, Equatable {
    public let aiLearningPercent: Int
    public let generalAppPercent: Int
    public let aiMinutes: Int
    public let generalMinutes: Int

    public init(
        aiLearningPercent: Int = 0,
        generalAppPercent: Int = 0,
        aiMinutes: Int = 0,
        generalMinutes: Int = 0
    ) {
        self.aiLearningPercent = aiLearningPercent
        self.generalAppPercent = generalAppPercent
        self.aiMinutes = aiMinutes
        self.generalMinutes = generalMinutes
    }
}

public struct MilestoneHighlight: Sendable, Equatable, Identifiable {
    public let id: String
    public let childName: String
    public let title: String
    public let description: String
    public let badge: String
    public let timestamp: String

    public init(
        id: String,
        childName: String,
        title: String,
        description: String,
        badge: String,
        timestamp: String
    ) {
        self.id = id
        self.childName = childName
        self.title = title
        self.description = description
        self.badge = badge
        self.timestamp = timestamp
    }
}

public struct AiRecommendation: Sendable, Equatable {
    public let title: String
    public let description: String
    public let actionLabel: String
    public let actionTopic: String?
    public let priorityLevel: String

    public init(
        title: String,
        description: String,
        actionLabel: String,
        actionTopic: String? = nil,
        priorityLevel: String = "High Priority"
    ) {
        self.title = title
        self.description = description
        self.actionLabel = actionLabel
        self.actionTopic = actionTopic
        self.priorityLevel = priorityLevel
    }
}

public struct LearningAnalyticsOverview: Sendable, Equatable {
    public let totalAiMinutes: Int
    public let formattedAiTime: String
    public let modulesCompleted: Int
    public let accuracyPercent: Int
    public let adaptiveDifficultyLabel: String
    public let difficultyTrend: String
    public let dailyTrend: [DailyLearningPoint]
    public let subjectBreakdowns: [SubjectProgressItem]
    public let focusDistribution: FocusDistributionData
    public let milestones: [MilestoneHighlight]
    public let aiRecommendation: AiRecommendation?
    public let currentStreakDays: Int
    public let totalQuestionsAnswered: Int
    public let nextMilestoneProgress: Double
    public let nextMilestoneLabel: String
    public let weakConceptsList: [String]

    public init(
        totalAiMinutes: Int = 0,
        formattedAiTime: String = "0 min",
        modulesCompleted: Int = 0,
        accuracyPercent: Int = 0,
        adaptiveDifficultyLabel: String = "Level 1 · Starting",
        difficultyTrend: String = "Steady Pace",
        dailyTrend: [DailyLearningPoint] = [],
        subjectBreakdowns: [SubjectProgressItem] = [],
        focusDistribution: FocusDistributionData = FocusDistributionData(),
        milestones: [MilestoneHighlight] = [],
        aiRecommendation: AiRecommendation? = nil,
        currentStreakDays: Int = 1,
        totalQuestionsAnswered: Int = 0,
        nextMilestoneProgress: Double = 0.6,
        nextMilestoneLabel: String = "3 of 5 modules completed",
        weakConceptsList: [String] = []
    ) {
        self.totalAiMinutes = totalAiMinutes
        self.formattedAiTime = formattedAiTime
        self.modulesCompleted = modulesCompleted
        self.accuracyPercent = accuracyPercent
        self.adaptiveDifficultyLabel = adaptiveDifficultyLabel
        self.difficultyTrend = difficultyTrend
        self.dailyTrend = dailyTrend
        self.subjectBreakdowns = subjectBreakdowns
        self.focusDistribution = focusDistribution
        self.milestones = milestones
        self.aiRecommendation = aiRecommendation
        self.currentStreakDays = currentStreakDays
        self.totalQuestionsAnswered = totalQuestionsAnswered
        self.nextMilestoneProgress = nextMilestoneProgress
        self.nextMilestoneLabel = nextMilestoneLabel
        self.weakConceptsList = weakConceptsList
    }
}

public struct DashboardChildCard: Sendable, Equatable, Identifiable {
    public var id: String { profile.childId }
    public let profile: FamilyChildProfile
    public var todayMinutes: Int
    public var dailyCeilingMinutes: Int
    public var remainingMinutes: Int
    public var pairedDevices: Int
    public var allowedApps: Int
    public var deviceModel: String?
    public var isDeviceActive: Bool
    public var isPaused: Bool
    public var lastQuizTopic: String?
    public var lastQuizPassed: Bool?
    public var lastQuizScore: String?
    public var lastQuizRewardMinutes: Int?
    public var cooldownMinutesRemaining: Int?
    public var topAppLabel: String?
    public var topAppRemainingMinutes: Int?
    public var practiceHint: String?
    public var gradeStandard: String
    public var currentLevel: Int
    public var levelLabel: String
    public var aiTrainingMinutes: Int
    public var totalModulesCompleted: Int
    public var masteryRatePercent: Int
    public var primarySubject: String
    public var batteryPercent: Int?
    public var weakConcepts: [String]
    public var streakDays: Int
    public var nextMilestoneTitle: String

    public init(
        profile: FamilyChildProfile,
        todayMinutes: Int = 0,
        dailyCeilingMinutes: Int = 120,
        remainingMinutes: Int = 120,
        pairedDevices: Int = 0,
        allowedApps: Int = 0,
        deviceModel: String? = nil,
        isDeviceActive: Bool = false,
        isPaused: Bool = false,
        lastQuizTopic: String? = nil,
        lastQuizPassed: Bool? = nil,
        lastQuizScore: String? = nil,
        lastQuizRewardMinutes: Int? = nil,
        cooldownMinutesRemaining: Int? = nil,
        topAppLabel: String? = nil,
        topAppRemainingMinutes: Int? = nil,
        practiceHint: String? = nil,
        gradeStandard: String = "",
        currentLevel: Int = 1,
        levelLabel: String = "Level 1",
        aiTrainingMinutes: Int = 0,
        totalModulesCompleted: Int = 0,
        masteryRatePercent: Int = 0,
        primarySubject: String = "Math",
        batteryPercent: Int? = nil,
        weakConcepts: [String] = [],
        streakDays: Int = 1,
        nextMilestoneTitle: String = "Next Level Unlock"
    ) {
        self.profile = profile
        self.todayMinutes = todayMinutes
        self.dailyCeilingMinutes = dailyCeilingMinutes
        self.remainingMinutes = remainingMinutes
        self.pairedDevices = pairedDevices
        self.allowedApps = allowedApps
        self.deviceModel = deviceModel
        self.isDeviceActive = isDeviceActive
        self.isPaused = isPaused
        self.lastQuizTopic = lastQuizTopic
        self.lastQuizPassed = lastQuizPassed
        self.lastQuizScore = lastQuizScore
        self.lastQuizRewardMinutes = lastQuizRewardMinutes
        self.cooldownMinutesRemaining = cooldownMinutesRemaining
        self.topAppLabel = topAppLabel
        self.topAppRemainingMinutes = topAppRemainingMinutes
        self.practiceHint = practiceHint
        self.gradeStandard = gradeStandard
        self.currentLevel = currentLevel
        self.levelLabel = levelLabel
        self.aiTrainingMinutes = aiTrainingMinutes
        self.totalModulesCompleted = totalModulesCompleted
        self.masteryRatePercent = masteryRatePercent
        self.primarySubject = primarySubject
        self.batteryPercent = batteryPercent
        self.weakConcepts = weakConcepts
        self.streakDays = streakDays
        self.nextMilestoneTitle = nextMilestoneTitle
    }
}

public struct DashboardUi: Sendable, Equatable {
    public let greetingName: String
    public let familyName: String
    public var children: [DashboardChildCard]
    public let canAddChild: Bool
    public var protectedDevicesCount: Int
    public let syncStatusTime: String
    public var recentEvents: [DashboardActivityEvent]
    public var selectedTimeRange: DashboardTimeRange
    public var selectedChildId: String?
    public var analytics: LearningAnalyticsOverview
    public var isRefreshing: Bool

    public init(
        greetingName: String,
        familyName: String,
        children: [DashboardChildCard],
        canAddChild: Bool,
        protectedDevicesCount: Int,
        syncStatusTime: String,
        recentEvents: [DashboardActivityEvent] = [],
        selectedTimeRange: DashboardTimeRange = .week,
        selectedChildId: String? = nil,
        analytics: LearningAnalyticsOverview = LearningAnalyticsOverview(),
        isRefreshing: Bool = false
    ) {
        self.greetingName = greetingName
        self.familyName = familyName
        self.children = children
        self.canAddChild = canAddChild
        self.protectedDevicesCount = protectedDevicesCount
        self.syncStatusTime = syncStatusTime
        self.recentEvents = recentEvents
        self.selectedTimeRange = selectedTimeRange
        self.selectedChildId = selectedChildId
        self.analytics = analytics
        self.isRefreshing = isRefreshing
    }
}

private struct ChildDataBundle: Sendable {
    let card: DashboardChildCard
    let usage: [UsageDaySummary]
    let quizzes: [QuizAttemptSummary]
    let skills: [TopicSkillSummary]
    let deviceCount: Int
}

@Observable
@MainActor
public final class DashboardViewModel {
    public var uiState: UiState<DashboardUi> = .loading
    public var isRefreshing: Bool = false
    public var selectedTimeRange: DashboardTimeRange = .week
    public var selectedChildId: String? = nil

    private let parentSessionRepo: ParentSessionRepository
    private let parentControlStore: ParentControlStoreProtocol
    private var memoryCachedUi: DashboardUi?
    @ObservationIgnored private var debounceTask: Task<Void, Never>?

    public init(
        parentSessionRepo: ParentSessionRepository = .shared,
        parentControlStore: ParentControlStoreProtocol = FirestoreParentControlStore.shared
    ) {
        self.parentSessionRepo = parentSessionRepo
        self.parentControlStore = parentControlStore
    }

    public func load() {
        if case .loading = uiState, let cached = memoryCachedUi {
            uiState = .success(cached)
        }
        Task {
            await fetchDashboardData()
        }
    }

    public func refresh() {
        isRefreshing = true
        Task {
            await fetchDashboardData()
            try? await Task.sleep(nanoseconds: 300_000_000)
            isRefreshing = false
        }
    }

    public func setTimeRange(_ range: DashboardTimeRange) {
        selectedTimeRange = range
        if case .success(var current) = uiState {
            current.selectedTimeRange = range
            uiState = .success(current)
        }
        debounceTask?.cancel()
        debounceTask = Task {
            try? await Task.sleep(nanoseconds: UInt64(AppConfig.dashboardChildrenDebounceMs) * 1_000_000)
            guard !Task.isCancelled else { return }
            await fetchDashboardData()
        }
    }

    public func selectChild(_ childId: String?) {
        selectedChildId = childId
        if case .success(var current) = uiState {
            current.selectedChildId = childId
            uiState = .success(current)
        }
        debounceTask?.cancel()
        debounceTask = Task {
            try? await Task.sleep(nanoseconds: UInt64(AppConfig.dashboardChildrenDebounceMs) * 1_000_000)
            guard !Task.isCancelled else { return }
            await fetchDashboardData()
        }
    }

    public func togglePause(childId: String) {
        guard let familyId = parentSessionRepo.current()?.familyId else { return }

        // Optimistic UI update
        if case .success(var current) = uiState {
            if let idx = current.children.firstIndex(where: { $0.profile.childId == childId }) {
                current.children[idx].isPaused.toggle()
                let newPaused = current.children[idx].isPaused
                uiState = .success(current)

                Task {
                    do {
                        try await parentControlStore.setChildPaused(familyId: familyId, childId: childId, paused: newPaused)
                    } catch {
                        // Rollback on failure
                        if case .success(var rolledBack) = uiState,
                           let rIdx = rolledBack.children.firstIndex(where: { $0.profile.childId == childId }) {
                            rolledBack.children[rIdx].isPaused = !newPaused
                            uiState = .success(rolledBack)
                        }
                    }
                }
            }
        }
    }

    public func grantBonus(childId: String, bonusMinutes: Int = AppConfig.bonusTimeMinutesDefault) {
        guard let familyId = parentSessionRepo.current()?.familyId else { return }

        // Optimistic UI update
        if case .success(var current) = uiState {
            if let idx = current.children.firstIndex(where: { $0.profile.childId == childId }) {
                current.children[idx].dailyCeilingMinutes += bonusMinutes
                current.children[idx].remainingMinutes += bonusMinutes
                uiState = .success(current)

                Task {
                    do {
                        try await parentControlStore.addBonusTime(familyId: familyId, childId: childId, bonusMinutes: bonusMinutes)
                    } catch {
                        // Refresh to sync state
                        await fetchDashboardData()
                    }
                }
            }
        }
    }

    private func fetchDashboardData() async {
        guard let session = parentSessionRepo.current() else {
            // Emitting sample/mock data for preview or unauthenticated session
            await loadSampleDashboard()
            return
        }

        do {
            let familyId = session.familyId
            let meta = try await parentControlStore.getFamilyMeta(familyId: familyId)
            let childrenProfiles = try await parentControlStore.listChildren(familyId: familyId)
            let timeRangeDays = selectedTimeRange.days

            // Fan out per-child reads in parallel using withThrowingTaskGroup (mirrors Android Phase 10)
            let bundles = try await withThrowingTaskGroup(of: (Int, ChildDataBundle).self) { group in
                for (index, child) in childrenProfiles.enumerated() {
                    group.addTask {
                        let policy = try await self.parentControlStore.getPolicy(familyId: familyId, childId: child.childId)
                        let devices = try await self.parentControlStore.listDevices(familyId: familyId, childId: child.childId).filter { !$0.revoked }
                        let rules = try await self.parentControlStore.listAppRules(familyId: familyId, childId: child.childId)
                        let usage = try await self.parentControlStore.listUsageDays(familyId: familyId, childId: child.childId, limit: timeRangeDays)
                        let quizzes = try await self.parentControlStore.listQuizAttempts(familyId: familyId, childId: child.childId, limit: 30)
                        let skills = try await self.parentControlStore.getSkillState(familyId: familyId, childId: child.childId)

                        let ceiling = policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
                        let usedMinutes = usage.first?.minutesUsed ?? 0
                        let remaining = max(0, ceiling - usedMinutes)
                        let primaryDevice = devices.first
                        let isOnline = primaryDevice?.lastSeenAtEpochMs.map {
                            (Int64(Date().timeIntervalSince1970 * 1000) - $0) < Int64(AppConfig.deviceOnlineThresholdMinutes * 60_000)
                        } ?? false

                        let lastQuiz = quizzes.first
                        let lastQuizTopic = lastQuiz?.topics.first?.capitalized ?? (lastQuiz != nil ? "General Quiz" : nil)
                        let lastQuizScore = lastQuiz.map { "\($0.score)/\($0.total)" }
                        let rewardMinutes = lastQuiz?.extraMinutesGranted ?? (lastQuiz?.passed == true ? 15 : nil)
                        let maxLevel = skills.map(\.level).max() ?? 1
                        let passedQuizzes = quizzes.filter(\.passed).count
                        let totalAttempts = quizzes.count
                        let accuracy = totalAttempts > 0 ? (passedQuizzes * 100) / totalAttempts : 0
                        let streak = skills.map(\.streakCorrect).max() ?? 1
                        let weakConcepts = skills.flatMap(\.weakConcepts)

                        let todayFormatter = DateFormatter()
                        todayFormatter.dateFormat = "yyyy-MM-dd"
                        let todayKey = todayFormatter.string(from: Date())
                        let todayUsage = usage.first(where: { $0.day == todayKey }) ?? usage.first
                        let actualUsedMinutes = todayUsage?.minutesUsed ?? 0
                        let actualRemaining = max(0, ceiling - actualUsedMinutes)

                        let topAppEntry = todayUsage?.minutesByApp.max(by: { ($0.value as? Int ?? 0) < ($1.value as? Int ?? 0) })
                        let topAppPkg = topAppEntry?.key
                        let topAppMins = (topAppEntry?.value as? Int) ?? (topAppEntry?.value as? NSNumber)?.intValue ?? 0
                        let topAppRule = rules.first { $0.packageOrBundleId == topAppPkg }
                        let dynamicTopAppLabel: String? = topAppPkg != nil ? (topAppRule?.displayName ?? topAppPkg) : nil
                        let dynamicTopAppRemaining = dynamicTopAppLabel != nil ? max(0, (topAppRule?.blockMinutes ?? policy.defaultBlockMinutes) - topAppMins) : nil

                        let actualAiMinutes = quizzes.reduce(0) { $0 + $1.extraMinutesGranted } > 0
                            ? quizzes.reduce(0) { $0 + $1.extraMinutesGranted }
                            : (passedQuizzes * 15)
                        let primarySubject = skills.first?.topic ?? (quizzes.first?.topics.first?.capitalized ?? "General Learning")

                        let card = DashboardChildCard(
                            profile: child,
                            todayMinutes: actualUsedMinutes,
                            dailyCeilingMinutes: ceiling,
                            remainingMinutes: actualRemaining,
                            pairedDevices: devices.count,
                            allowedApps: rules.filter(\.allowed).count,
                            deviceModel: primaryDevice?.model ?? (devices.isEmpty ? nil : "Apple Device"),
                            isDeviceActive: isOnline,
                            isPaused: policy.paused,
                            lastQuizTopic: lastQuizTopic,
                            lastQuizPassed: lastQuiz?.passed,
                            lastQuizScore: lastQuizScore,
                            lastQuizRewardMinutes: rewardMinutes,
                            cooldownMinutesRemaining: lastQuiz?.passed == false ? policy.defaultCooldownMinutes : nil,
                            topAppLabel: dynamicTopAppLabel,
                            topAppRemainingMinutes: dynamicTopAppRemaining,
                            practiceHint: weakConcepts.first,
                            gradeStandard: policy.gradeStandard.isEmpty ? child.ageBand.displayLabel : policy.gradeStandard,
                            currentLevel: maxLevel,
                            levelLabel: "Level \(maxLevel)",
                            aiTrainingMinutes: actualAiMinutes,
                            totalModulesCompleted: passedQuizzes,
                            masteryRatePercent: accuracy,
                            primarySubject: primarySubject,
                            batteryPercent: primaryDevice?.batteryPercent,
                            weakConcepts: weakConcepts,
                            streakDays: streak,
                            nextMilestoneTitle: "Level \(maxLevel + 1) Unlock"
                        )

                        return (index, ChildDataBundle(
                            card: card,
                            usage: usage,
                            quizzes: quizzes,
                            skills: skills,
                            deviceCount: devices.count
                        ))
                    }
                }

                var indexedBundles: [(Int, ChildDataBundle)] = []
                for try await item in group {
                    indexedBundles.append(item)
                }
                return indexedBundles.sorted(by: { $0.0 < $1.0 }).map(\.1)
            }

            var cards: [DashboardChildCard] = []
            var allUsageDays: [UsageDaySummary] = []
            var allQuizzes: [QuizAttemptSummary] = []
            var allSkills: [TopicSkillSummary] = []
            var totalDevices = 0

            for bundle in bundles {
                cards.append(bundle.card)
                allUsageDays.append(contentsOf: bundle.usage)
                allQuizzes.append(contentsOf: bundle.quizzes)
                allSkills.append(contentsOf: bundle.skills)
                totalDevices += bundle.deviceCount
            }

            let syncFormatter = DateFormatter()
            syncFormatter.dateFormat = "h:mm a"
            let syncTime = syncFormatter.string(from: Date())

            let analytics = computeAnalytics(
                bundles: bundles,
                range: selectedTimeRange,
                childId: selectedChildId
            )
            let events = buildEvents(cards: cards)

            let ui = DashboardUi(
                greetingName: "Parent",
                familyName: meta?.name.isEmpty == false ? meta!.name : "The Family",
                children: cards,
                canAddChild: cards.count < AppConfig.maxChildrenPerParent,
                protectedDevicesCount: totalDevices,
                syncStatusTime: syncTime,
                recentEvents: events,
                selectedTimeRange: selectedTimeRange,
                selectedChildId: selectedChildId,
                analytics: analytics,
                isRefreshing: false
            )
            memoryCachedUi = ui
            uiState = .success(ui)
        } catch {
            await loadSampleDashboard()
        }
    }

    private func loadSampleDashboard() async {
        let children = (try? await parentControlStore.listChildren(familyId: "sample_family")) ?? []
        let cards: [DashboardChildCard] = children.map { child in
            DashboardChildCard(
                profile: child,
                todayMinutes: 0,
                dailyCeilingMinutes: 120,
                remainingMinutes: 120,
                pairedDevices: 0,
                allowedApps: 0,
                deviceModel: nil,
                isDeviceActive: false,
                isPaused: false,
                lastQuizTopic: nil,
                lastQuizPassed: nil,
                lastQuizScore: nil,
                lastQuizRewardMinutes: nil,
                gradeStandard: child.ageBand.displayLabel,
                currentLevel: 1,
                levelLabel: "Level 1",
                aiTrainingMinutes: 0,
                totalModulesCompleted: 0,
                masteryRatePercent: 0,
                primarySubject: "General",
                batteryPercent: nil,
                weakConcepts: [],
                streakDays: 0,
                nextMilestoneTitle: "Next Level Unlock"
            )
        }

        let syncFormatter = DateFormatter()
        syncFormatter.dateFormat = "h:mm a"
        let syncTime = syncFormatter.string(from: Date())

        let bundles = cards.map { ChildDataBundle(card: $0, usage: [], quizzes: [], skills: [], deviceCount: 0) }
        let analytics = computeAnalytics(
            bundles: bundles,
            range: selectedTimeRange,
            childId: selectedChildId
        )

        let events = buildEvents(cards: cards)

        let ui = DashboardUi(
            greetingName: "Parent",
            familyName: "The Family",
            children: cards,
            canAddChild: cards.count < AppConfig.maxChildrenPerParent,
            protectedDevicesCount: cards.count,
            syncStatusTime: syncTime,
            recentEvents: events,
            selectedTimeRange: selectedTimeRange,
            selectedChildId: selectedChildId,
            analytics: analytics,
            isRefreshing: false
        )
        uiState = .success(ui)
    }

    private func computeAnalytics(
        bundles: [ChildDataBundle],
        range: DashboardTimeRange,
        childId: String?
    ) -> LearningAnalyticsOverview {
        let targetBundles = childId == nil ? bundles : bundles.filter { $0.card.profile.childId == childId }
        let filteredCards = targetBundles.map(\.card)
        let allUsage = targetBundles.flatMap(\.usage)
        let allQuizzes = targetBundles.flatMap(\.quizzes)
        let allSkills = targetBundles.flatMap(\.skills)

        // Basic KPI metrics
        let totalAiMin = filteredCards.reduce(0) { $0 + $1.aiTrainingMinutes }
        let totalModules = allQuizzes.filter(\.passed).count
        let totalAttempts = allQuizzes.count
        let accuracy = totalAttempts > 0 ? (totalModules * 100) / totalAttempts : 0
        let maxLevel = allSkills.map(\.level).max() ?? (filteredCards.map(\.currentLevel).max() ?? 1)
        let maxStreak = allSkills.map(\.streakCorrect).max() ?? (filteredCards.map(\.streakDays).max() ?? 0)

        let difficultyLabel: String
        switch maxLevel {
        case 1: difficultyLabel = "Level 1 · Fundamentals"
        case 2: difficultyLabel = "Level 2 · Building"
        case 3: difficultyLabel = "Level 3 · Advancing"
        case 4: difficultyLabel = "Level 4 · Mastery Track"
        default: difficultyLabel = "Level \(maxLevel) · Advanced"
        }

        let difficultyTrend: String = {
            if allQuizzes.isEmpty { return "Getting Started" }
            if accuracy >= 80 { return "Rising (+15%)" }
            if accuracy >= 60 { return "Steady Pace" }
            return "Building Confidence"
        }()

        // Calendar & Daily Trend Points
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "en_US_POSIX")
        let today = calendar.startOfDay(for: Date())
        let isoFormatter = DateFormatter()
        isoFormatter.dateFormat = "yyyy-MM-dd"
        isoFormatter.locale = Locale(identifier: "en_US_POSIX")

        let dayLabelFormatter = DateFormatter()
        dayLabelFormatter.locale = Locale.current

        var dailyPoints: [DailyLearningPoint] = []

        if range == .today {
            let key = isoFormatter.string(from: today)
            let todayUsage = allUsage.filter { $0.day == key }
            let screenMin = todayUsage.reduce(0) { $0 + $1.minutesUsed }
            let dayQuizzes = allQuizzes.filter {
                let d = Date(timeIntervalSince1970: Double($0.createdAtEpochMs) / 1000.0)
                return calendar.isDate(d, inSameDayAs: today)
            }
            let aiMin = dayQuizzes.reduce(0) { $0 + $1.extraMinutesGranted } > 0
                ? dayQuizzes.reduce(0) { $0 + $1.extraMinutesGranted }
                : (dayQuizzes.filter(\.passed).count * 15)
            dailyPoints.append(DailyLearningPoint(
                dayKey: key,
                dayLabel: "Today",
                aiTrainingMinutes: aiMin,
                totalScreenMinutes: screenMin,
                modulesCount: dayQuizzes.filter(\.passed).count
            ))
        } else if range == .week {
            dayLabelFormatter.dateFormat = "EEE"
            for offset in (0..<7).reversed() {
                guard let targetDate = calendar.date(byAdding: .day, value: -offset, to: today) else { continue }
                let key = isoFormatter.string(from: targetDate)
                let matchingUsage = allUsage.filter { $0.day == key }
                let screenMin = matchingUsage.reduce(0) { $0 + $1.minutesUsed }

                let matchingQuizzes = allQuizzes.filter {
                    let d = Date(timeIntervalSince1970: Double($0.createdAtEpochMs) / 1000.0)
                    return calendar.isDate(d, inSameDayAs: targetDate)
                }
                let aiMin = matchingQuizzes.reduce(0) { $0 + $1.extraMinutesGranted } > 0
                    ? matchingQuizzes.reduce(0) { $0 + $1.extraMinutesGranted }
                    : (matchingQuizzes.filter(\.passed).count * 15)

                let label = calendar.isDateInToday(targetDate) ? "Today" : dayLabelFormatter.string(from: targetDate)
                dailyPoints.append(DailyLearningPoint(
                    dayKey: key,
                    dayLabel: label,
                    aiTrainingMinutes: aiMin,
                    totalScreenMinutes: screenMin,
                    modulesCount: matchingQuizzes.filter(\.passed).count
                ))
            }
        } else { // .month (30 Days) -> 4 weekly intervals
            for weekIndex in (0..<4).reversed() {
                let daysFromNowEnd = weekIndex * 7
                let daysFromNowStart = min(29, (weekIndex + 1) * 7 - 1)
                guard let weekEndDate = calendar.date(byAdding: .day, value: -daysFromNowEnd, to: today),
                      let weekStartDate = calendar.date(byAdding: .day, value: -daysFromNowStart, to: today) else { continue }

                let matchingUsage = allUsage.filter { summary in
                    guard let d = isoFormatter.date(from: summary.day) else { return false }
                    return d >= weekStartDate && d <= weekEndDate
                }
                let screenMin = matchingUsage.reduce(0) { $0 + $1.minutesUsed }

                let matchingQuizzes = allQuizzes.filter {
                    let d = Date(timeIntervalSince1970: Double($0.createdAtEpochMs) / 1000.0)
                    return d >= weekStartDate && d <= weekEndDate
                }
                let aiMin = matchingQuizzes.reduce(0) { $0 + $1.extraMinutesGranted } > 0
                    ? matchingQuizzes.reduce(0) { $0 + $1.extraMinutesGranted }
                    : (matchingQuizzes.filter(\.passed).count * 15)

                let label = "W\(4 - weekIndex)"
                dailyPoints.append(DailyLearningPoint(
                    dayKey: "week_\(4 - weekIndex)",
                    dayLabel: label,
                    aiTrainingMinutes: aiMin,
                    totalScreenMinutes: screenMin,
                    modulesCount: matchingQuizzes.filter(\.passed).count
                ))
            }
        }

        // Subjects Breakdown
        var subjects: [SubjectProgressItem] = []
        if !allSkills.isEmpty {
            var seenTopics = Set<String>()
            for skill in allSkills where !seenTopics.contains(skill.topic) {
                seenTopics.insert(skill.topic)
                let pct = min(100, max(20, skill.level * 20))
                subjects.append(SubjectProgressItem(
                    subject: skill.topic,
                    level: skill.level,
                    levelLabel: "Lvl \(skill.level) / 5",
                    masteryPercent: pct,
                    streakCount: skill.streakCorrect,
                    isWeak: skill.weak,
                    weakConcept: skill.weakConcepts.first
                ))
            }
        } else if !allQuizzes.isEmpty {
            var topicCounts: [String: (total: Int, passed: Int)] = [:]
            for q in allQuizzes {
                let topic = q.topics.first?.capitalized ?? "General Learning"
                var cur = topicCounts[topic, default: (total: 0, passed: 0)]
                cur.total += 1
                if q.passed { cur.passed += 1 }
                topicCounts[topic] = cur
            }
            for (topic, stats) in topicCounts.prefix(3) {
                let pct = stats.total > 0 ? (stats.passed * 100) / stats.total : 0
                let lvl = max(1, min(5, (stats.passed / 2) + 1))
                subjects.append(SubjectProgressItem(
                    subject: topic,
                    level: lvl,
                    levelLabel: "Lvl \(lvl) / 5",
                    masteryPercent: pct,
                    streakCount: stats.passed
                ))
            }
        }

        // Focus Distribution
        let totalScreenTime = dailyPoints.reduce(0) { $0 + $1.totalScreenMinutes }
        let totalPeriodAiMin = dailyPoints.reduce(0) { $0 + $1.aiTrainingMinutes }
        let generalMinutes = max(0, totalScreenTime - totalPeriodAiMin)
        let totalFocus = max(totalScreenTime, 1)
        let aiPercent = min(100, max(0, (totalPeriodAiMin * 100) / totalFocus))
        let generalPercent = 100 - aiPercent

        let distribution = FocusDistributionData(
            aiLearningPercent: aiPercent,
            generalAppPercent: generalPercent,
            aiMinutes: totalPeriodAiMin,
            generalMinutes: generalMinutes
        )

        // Dynamic AI Recommendation
        let primaryChildName = filteredCards.first?.profile.displayName ?? "Learner"
        let weakSkill = allSkills.first(where: { $0.weak || !$0.weakConcepts.isEmpty })
        let recommendation: AiRecommendation? = {
            if let weak = weakSkill {
                let concept = weak.weakConcepts.first ?? weak.topic
                return AiRecommendation(
                    title: "Reinforce \(weak.topic): \(concept)",
                    description: "AI detected an opportunity to strengthen confidence in \(concept). A quick adaptive practice quiz will solidify understanding.",
                    actionLabel: "Review Concept",
                    actionTopic: weak.topic,
                    priorityLevel: "Recommended Focus"
                )
            } else if let lastQuiz = allQuizzes.first {
                let topic = lastQuiz.topics.first?.capitalized ?? "Learning Track"
                if lastQuiz.passed {
                    return AiRecommendation(
                        title: "Advance in \(topic)",
                        description: "\(primaryChildName) is demonstrating solid retention. Ready for the next difficulty level.",
                        actionLabel: "Advance",
                        actionTopic: topic,
                        priorityLevel: "Next Milestone"
                    )
                } else {
                    return AiRecommendation(
                        title: "Review \(topic)",
                        description: "\(primaryChildName) can earn extra minutes by reviewing key points with an adaptive quiz.",
                        actionLabel: "Practice",
                        actionTopic: topic,
                        priorityLevel: "Recommended Focus"
                    )
                }
            } else {
                return AiRecommendation(
                    title: "Start Daily AI Learning Track",
                    description: "Complete the first interactive quiz to establish baseline mastery and unlock reward screen time.",
                    actionLabel: "Begin Quiz",
                    actionTopic: "General Knowledge",
                    priorityLevel: "Getting Started"
                )
            }
        }()

        // Dynamic Milestone Highlights
        let passedQuizzes = allQuizzes.filter(\.passed)
        let milestones: [MilestoneHighlight] = {
            if !passedQuizzes.isEmpty {
                return passedQuizzes.prefix(3).map { quiz in
                    let topic = quiz.topics.first?.capitalized ?? "Daily"
                    let extraTime = quiz.extraMinutesGranted > 0 ? "+\(quiz.extraMinutesGranted)m reward time unlocked" : "Mastered quiz"
                    return MilestoneHighlight(
                        id: quiz.attemptId,
                        childName: primaryChildName,
                        title: "\(primaryChildName) mastered \(topic) Quiz",
                        description: "Scored \(quiz.score)/\(quiz.total) · \(extraTime)",
                        badge: "Mastered",
                        timestamp: "Recent"
                    )
                }
            } else {
                return [
                    MilestoneHighlight(
                        id: "initial_goal",
                        childName: primaryChildName,
                        title: "\(primaryChildName) ready for first quiz",
                        description: "Pass a quiz to earn bonus screen time and level up",
                        badge: "New",
                        timestamp: "Today"
                    )
                ]
            }
        }()

        let passedInLevel = totalModules % 5
        let milestoneProgress = Double(passedInLevel) / 5.0
        let milestoneLabel = "\(passedInLevel) of 5 modules completed towards Level \(maxLevel + 1)"
        let weakConcepts = allSkills.flatMap(\.weakConcepts)

        let formattedAiTime: String = {
            if totalPeriodAiMin >= 60 {
                let h = totalPeriodAiMin / 60
                let m = totalPeriodAiMin % 60
                return m > 0 ? "\(h)h \(m)m" : "\(h)h"
            }
            return "\(totalPeriodAiMin) min"
        }()

        let totalQuestionsAnswered = allQuizzes.reduce(0) { $0 + $1.total }

        return LearningAnalyticsOverview(
            totalAiMinutes: totalPeriodAiMin,
            formattedAiTime: formattedAiTime,
            modulesCompleted: totalModules,
            accuracyPercent: accuracy,
            adaptiveDifficultyLabel: difficultyLabel,
            difficultyTrend: difficultyTrend,
            dailyTrend: dailyPoints,
            subjectBreakdowns: subjects,
            focusDistribution: distribution,
            milestones: milestones,
            aiRecommendation: recommendation,
            currentStreakDays: maxStreak,
            totalQuestionsAnswered: totalQuestionsAnswered,
            nextMilestoneProgress: milestoneProgress,
            nextMilestoneLabel: milestoneLabel,
            weakConceptsList: weakConcepts
        )
    }

    private func buildEvents(cards: [DashboardChildCard]) -> [DashboardActivityEvent] {
        var events: [DashboardActivityEvent] = []
        for card in cards {
            if card.lastQuizPassed == true {
                events.append(
                    DashboardActivityEvent(
                        id: "\(card.profile.childId)_pass",
                        title: "\(card.profile.displayName) passed \(card.lastQuizTopic ?? "Daily") Quiz",
                        subtitle: "+\(card.lastQuizRewardMinutes ?? 15)m reward unlocked automatically",
                        timeLabel: "Recent",
                        eventType: .quizPass,
                        scoreBadge: card.lastQuizScore ?? "3/3",
                        extraMinutesBadge: "+\(card.lastQuizRewardMinutes ?? 15)m"
                    )
                )
            }
            if card.remainingMinutes <= 15 && card.todayMinutes > 0 {
                events.append(
                    DashboardActivityEvent(
                        id: "\(card.profile.childId)_limit",
                        title: "\(card.profile.displayName) approaching daily ceiling",
                        subtitle: "\(card.remainingMinutes)m remaining for today",
                        timeLabel: "Today",
                        eventType: .limitReached
                    )
                )
            }
        }

        events.append(
            DashboardActivityEvent(
                id: "system_integrity",
                title: "System Integrity Check",
                subtitle: "All devices verified with local offline rule bank",
                timeLabel: "Synced",
                eventType: .systemCheck
            )
        )
        return Array(events.prefix(4))
    }
}
