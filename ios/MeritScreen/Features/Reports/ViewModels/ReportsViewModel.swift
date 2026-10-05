import Foundation
import SwiftUI

/// Observable ViewModel coordinating reports and usage analytics for the parent portal (P17).
@Observable
@MainActor
public final class ReportsViewModel {

    public var uiState: UiState<ReportsUi> = .loading
    public var selectedChildId: String = ""
    public var selectedDays: Int = 7
    public var isRefreshing: Bool = false

    private let parentControlStore: ParentControlStoreProtocol
    private let sessionRepo: ParentSessionRepository
    private let analyticsTracker: AnalyticsTrackerProtocol

    public init(
        parentControlStore: ParentControlStoreProtocol = FirestoreParentControlStore.shared,
        sessionRepo: ParentSessionRepository = .shared,
        analyticsTracker: AnalyticsTrackerProtocol = ChildSafeAnalyticsTracker.shared
    ) {
        self.parentControlStore = parentControlStore
        self.sessionRepo = sessionRepo
        self.analyticsTracker = analyticsTracker
    }

    public func load(preselectedChildId: String? = nil) {
        if let preselectedChildId, !preselectedChildId.isEmpty {
            self.selectedChildId = preselectedChildId
        }
        analyticsTracker.track(.reportsViewed)
        Task {
            await fetchReports()
        }
    }

    public func selectChild(_ childId: String) {
        guard selectedChildId != childId else { return }
        selectedChildId = childId
        Task {
            await fetchReports()
        }
    }

    public func selectDays(_ days: Int) {
        let target = (days <= 1) ? 1 : (days >= 30 ? 30 : 7)
        guard selectedDays != target else { return }
        selectedDays = target
        Task {
            await fetchReports()
        }
    }

    public func refresh() {
        Task {
            isRefreshing = true
            await fetchReports()
            try? await Task.sleep(nanoseconds: 300_000_000)
            isRefreshing = false
        }
    }

    // MARK: - Private Loading Engine

    private func fetchReports() async {
        guard let session = sessionRepo.current(), !session.familyId.isEmpty else {
            uiState = .error(AppError.auth("Parent session expired"))
            return
        }

        let familyId = session.familyId

        do {
            let children = (try? await parentControlStore.listChildren(familyId: familyId)) ?? []
            let childProfiles = children.map {
                ChildReportProfile(
                    childId: $0.childId,
                    displayName: $0.displayName,
                    ageLabel: "Ages \($0.ageBand.displayLabel)",
                    avatar: $0.avatar
                )
            }

            guard !children.isEmpty else {
                uiState = .empty
                return
            }

            let activeChildId: String
            if !selectedChildId.isEmpty && children.contains(where: { $0.childId == selectedChildId }) {
                activeChildId = selectedChildId
            } else {
                activeChildId = children.first!.childId
                selectedChildId = activeChildId
            }

            let currentDays = selectedDays

            async let usageTask = (try? parentControlStore.listUsageDays(familyId: familyId, childId: activeChildId, limit: currentDays)) ?? []
            async let attemptsTask = (try? parentControlStore.listQuizAttempts(familyId: familyId, childId: activeChildId, limit: 50)) ?? []
            async let skillsTask = (try? parentControlStore.getSkillState(familyId: familyId, childId: activeChildId)) ?? []
            async let appsTask = (try? parentControlStore.listInstalledApps(familyId: familyId, childId: activeChildId)) ?? []
            async let rulesTask = (try? parentControlStore.listAppRules(familyId: familyId, childId: activeChildId)) ?? []
            async let policyTask = try? parentControlStore.getPolicy(familyId: familyId, childId: activeChildId)

            let (usage, attempts, skills, installedApps, appRules, policy) = await (
                usageTask,
                attemptsTask,
                skillsTask,
                appsTask,
                rulesTask,
                policyTask
            )

            var labels: [String: String] = [:]
            for app in installedApps {
                labels[app.packageName] = app.appName
            }
            for rule in appRules where !rule.displayName.isEmpty {
                labels[rule.packageOrBundleId] = rule.displayName
            }

            let snapshot = ReportsAggregator.build(
                days: currentDays,
                usage: usage,
                attempts: attempts,
                skills: skills,
                appLabels: labels
            )

            let activeChild = children.first { $0.childId == activeChildId }
            let childName = activeChild?.displayName ?? "Child"
            let cooldownMinutes = policy?.defaultCooldownMinutes ?? 15

            let ui = mapToReportsUi(
                activeChildId: activeChildId,
                selectedDays: currentDays,
                children: childProfiles,
                snapshot: snapshot,
                childName: childName,
                cooldownMinutes: cooldownMinutes
            )

            uiState = .success(ui)
        } catch {
            uiState = .error(AppError.network(error.localizedDescription))
        }
    }

    private func mapToReportsUi(
        activeChildId: String,
        selectedDays: Int,
        children: [ChildReportProfile],
        snapshot: ChildReportsSnapshot,
        childName: String,
        cooldownMinutes: Int
    ) -> ReportsUi {
        let allocations = buildAppAllocations(apps: snapshot.byApp, totalMinutes: snapshot.totalMinutes)
        let eduPct = snapshot.educationalPercent
        let score = BalancedScoreCalculator.calculate(report: snapshot, educationalPercent: eduPct)
        let (scoreLabel, scoreDesc) = BalancedScoreCalculator.copy(score: score, report: snapshot)
        let strengths = buildStrengths(snapshot: snapshot)
        let growth = buildGrowth(snapshot: snapshot)

        let eduAppMinutes = snapshot.byApp
            .filter { AppInventoryCategorizer.classify(packageName: $0.packageName, label: $0.displayName).category == .educational }
            .reduce(0) { $0 + $1.minutes }
        let quizEstimatedMinutes = snapshot.quiz.passedCount * 3
        let aiFocusMinutes = max(0, eduAppMinutes + quizEstimatedMinutes)

        return ReportsUi(
            selectedChildId: activeChildId,
            selectedDays: selectedDays,
            children: children,
            report: snapshot,
            selectedChildName: childName,
            periodLabel: periodLabel(selectedDays),
            dailyAverageFormatted: formatDuration(snapshot.averageMinutesPerDay),
            totalScreenTimeFormatted: formatDuration(snapshot.totalMinutes),
            totalAiFocusTimeFormatted: formatDuration(aiFocusMinutes),
            trendPercentage: snapshot.trendPercent,
            streakDays: snapshot.streakDays,
            masteryRatePercent: snapshot.masteryRatePercent,
            balancedScore: score,
            balancedScoreLabel: scoreLabel,
            balancedScoreDescription: scoreDesc,
            educationalPercent: eduPct,
            appAllocations: allocations,
            quizPassPercent: snapshot.quiz.accuracyPercent,
            focusSessionCount: snapshot.quiz.attemptCount,
            focusSessionsPassed: snapshot.quiz.passedCount,
            extraMinutesEarned: snapshot.quiz.extraMinutesEarned,
            focusSessions: snapshot.recentAttempts.map { attempt in
                FocusSessionItem(
                    attemptId: attempt.attemptId,
                    title: attempt.topics.first.map(ReportsAggregator.topicLabel) ?? "Quiz Session",
                    passed: attempt.passed,
                    scoreLabel: "\(attempt.score)/\(attempt.total)",
                    extraMinutes: attempt.extraMinutesGranted
                )
            },
            aiTopics: snapshot.topics.prefix(6).map { topic in
                AiTopicInsight(
                    topic: ReportsAggregator.topicLabel(topic.topic),
                    levelLabel: ReportsAggregator.levelLabel(topic.level),
                    tierLabel: topic.tierLabel.capitalized,
                    weak: topic.weak,
                    masteredCount: topic.masteredConcepts.count
                )
            },
            demonstratedStrengths: strengths,
            growthRecommended: growth,
            masteredConceptCount: snapshot.topics.reduce(0) { $0 + $1.masteredConcepts.count },
            cooldownIncidentsCount: snapshot.quiz.failedCount,
            avgCooldownRestMinutes: cooldownMinutes,
            hasUsageData: snapshot.totalMinutes > 0,
            hasQuizData: snapshot.quiz.attemptCount > 0,
            hasSkillData: !snapshot.topics.isEmpty,
            isRefreshing: isRefreshing
        )
    }

    private func buildAppAllocations(apps: [AppMinutesRollup], totalMinutes: Int) -> [AppAllocationItem] {
        if apps.isEmpty || totalMinutes <= 0 { return [] }
        let pctBase = max(1, apps.reduce(0) { $0 + $1.minutes })
        return apps.prefix(8).map { app in
            let pct = min(100, max(0, (app.minutes * 100) / pctBase))
            let classification = AppInventoryCategorizer.classify(packageName: app.packageName, label: app.displayName)
            let (type, label): (AppCategoryType, String) = {
                if app.packageName == "_other" {
                    return (.general, "Other")
                } else if classification.category == .educational {
                    return (categorizeEducational(app: app), "Learn")
                } else if classification.category == .entertainment {
                    return (.media, "Media")
                } else if classification.category == .system {
                    return (.general, "System")
                } else {
                    return (.general, "App")
                }
            }()
            return AppAllocationItem(
                packageName: app.packageName,
                displayName: app.displayName,
                minutes: app.minutes,
                percentage: max(app.minutes > 0 ? 1 : 0, pct),
                categoryLabel: label,
                categoryType: type
            )
        }
    }

    private func categorizeEducational(app: AppMinutesRollup) -> AppCategoryType {
        let lower = (app.displayName + " " + app.packageName).lowercased()
        if lower.contains("duo") || lower.contains("spell") || lower.contains("read") ||
           lower.contains("abc") || lower.contains("language") {
            return .language
        } else if lower.contains("scratch") || lower.contains("code") || lower.contains("logic") ||
                  lower.contains("puzzle") {
            return .logic
        } else {
            return .learn
        }
    }

    private func buildStrengths(snapshot: ChildReportsSnapshot) -> [String] {
        let fromTopics = snapshot.topics
            .filter { !$0.weak && $0.level >= 3 }
            .map { "\(ReportsAggregator.topicLabel($0.topic)) · \(ReportsAggregator.levelLabel($0.level))" }
        let fromMastered = Array(Set(snapshot.topics.flatMap { $0.masteredConcepts }))
            .prefix(2)
            .map { ReportsAggregator.humanizeToken($0) }
        return Array(Set(fromTopics + fromMastered)).prefix(4).map { $0 }
    }

    private func buildGrowth(snapshot: ChildReportsSnapshot) -> [String] {
        let hints = snapshot.practiceHints.map { $0.title }
        let weakTopics = snapshot.topics
            .filter { $0.weak }
            .map { "\(ReportsAggregator.topicLabel($0.topic)) · needs practice" }
        return Array(Set(hints + weakTopics)).prefix(4).map { $0 }
    }

    private func periodLabel(_ days: Int) -> String {
        switch days {
        case 1: return "Today"
        case 30: return "Last 30 Days"
        default: return "Last 7 Days"
        }
    }

    private func formatDuration(_ minutes: Int) -> String {
        let safe = max(0, minutes)
        let h = safe / 60
        let m = safe % 60
        if h > 0 && m > 0 {
            return "\(h)h \(m)m"
        } else if h > 0 {
            return "\(h)h"
        } else {
            return "\(m)m"
        }
    }
}
