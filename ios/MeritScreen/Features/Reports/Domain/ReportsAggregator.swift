import Foundation

/// Pure aggregation engine for parent reports (P17).
/// 100% parity with Android `ReportsAggregator.kt`. Zero UI or network dependencies.
public enum ReportsAggregator {

    private static let isoFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    public static func build(
        days: Int,
        usage: [UsageDaySummary],
        attempts: [QuizAttemptSummary],
        skills: [TopicSkillSummary],
        appLabels: [String: String] = [:],
        referenceDate: Date = Date(),
        recentAttemptLimit: Int = 10
    ) -> ChildReportsSnapshot {
        let calendar = utcCalendar()
        let today = calendar.startOfDay(for: referenceDate)
        let windowDays = max(1, min(30, days))
        let windowStart = calendar.date(byAdding: .day, value: -(windowDays - 1), to: today) ?? today

        let usageInWindow = usage.filter { daySummary in
            guard let date = parseDay(daySummary.day) else { return false }
            return date >= windowStart && date <= today
        }

        let attemptsInWindow = attempts.filter { attempt in
            let attemptDate = Date(timeIntervalSince1970: Double(attempt.createdAtEpochMs) / 1000.0)
            let attemptDay = calendar.startOfDay(for: attemptDate)
            return attemptDay >= windowStart && attemptDay <= today
        }

        let byDayMap = Dictionary(uniqueKeysWithValues: usageInWindow.map { ($0.day, $0) })

        var dailyPoints: [DailyMinutesPoint] = []
        for offset in 0..<windowDays {
            guard let dayDate = calendar.date(byAdding: .day, value: offset, to: windowStart) else { continue }
            let key = isoFormatter.string(from: dayDate)
            let summary = byDayMap[key]
            let dayApps = rollupDayApps(summary?.minutesByApp ?? [:], appLabels: appLabels)
            dailyPoints.append(
                DailyMinutesPoint(
                    day: key,
                    minutes: summary?.minutesUsed ?? 0,
                    byApp: dayApps
                )
            )
        }

        let totalMinutes = dailyPoints.reduce(0) { $0 + $1.minutes }
        let average = calendarAverageMinutes(totalMinutes: totalMinutes, days: windowDays)
        let busiest = dailyPoints.filter { $0.minutes > 0 }.max(by: { $0.minutes < $1.minutes })

        var byAppMinutes: [String: Int] = [:]
        for day in usageInWindow {
            for (pkg, minutes) in day.minutesByApp where minutes > 0 && !pkg.trimmingCharacters(in: .whitespaces).isEmpty {
                byAppMinutes[pkg, default: 0] += minutes
            }
        }

        var byApp = byAppMinutes.sorted { $0.value > $1.value }.map { pkg, minutes in
            AppMinutesRollup(
                packageName: pkg,
                displayName: resolveAppLabel(packageName: pkg, labels: appLabels),
                minutes: minutes
            )
        }

        let byAppSum = byApp.reduce(0) { $0 + $1.minutes }
        let unattributed = max(0, totalMinutes - byAppSum)
        if unattributed > 0 {
            byApp.append(
                AppMinutesRollup(
                    packageName: "_other",
                    displayName: "Other / unattributed",
                    minutes: unattributed
                )
            )
        }

        let passed = attemptsInWindow.filter { $0.passed }.count
        let failed = attemptsInWindow.count - passed
        let questionsCorrect = attemptsInWindow.reduce(0) { $0 + $1.score }
        let questionsTotal = attemptsInWindow.reduce(0) { $0 + $1.total }
        let accuracy: Int? = attemptsInWindow.isEmpty ? nil : ((passed * 100 + attemptsInWindow.count / 2) / attemptsInWindow.count)

        let quizStats = QuizWindowStats(
            attemptCount: attemptsInWindow.count,
            passedCount: passed,
            failedCount: failed,
            accuracyPercent: accuracy,
            questionsCorrect: questionsCorrect,
            questionsTotal: questionsTotal,
            extraMinutesEarned: attemptsInWindow.filter { $0.passed }.reduce(0) { $0 + $1.extraMinutesGranted }
        )

        let sortedTopics = skills.sorted { first, second in
            if first.weak != second.weak {
                return first.weak && !second.weak
            }
            if first.level != second.level {
                return first.level > second.level
            }
            return first.topic < second.topic
        }

        var practiceHints: [WeakConceptHint] = []
        var seenConcepts = Set<String>()
        for topic in sortedTopics {
            for concept in topic.weakConcepts where !seenConcepts.contains(concept) {
                seenConcepts.insert(concept)
                practiceHints.append(WeakConceptHint(conceptId: concept, title: humanizeToken(concept)))
                if practiceHints.count >= 8 { break }
            }
            if practiceHints.count >= 8 { break }
        }

        let hasData = totalMinutes > 0 || !attemptsInWindow.isEmpty || !skills.isEmpty

        return ChildReportsSnapshot(
            days: windowDays,
            totalMinutes: totalMinutes,
            averageMinutesPerDay: average,
            trendPercent: trendPercent(daily: dailyPoints),
            educationalPercent: educationalPercent(byApp: byApp.filter { $0.packageName != "_other" }),
            busiestDay: busiest,
            daily: dailyPoints,
            byApp: byApp,
            quiz: quizStats,
            recentAttempts: Array(attemptsInWindow.sorted { $0.createdAtEpochMs > $1.createdAtEpochMs }.prefix(recentAttemptLimit)),
            topics: sortedTopics,
            practiceHints: practiceHints,
            hasAnyData: hasData,
            streakDays: calculateStreakDays(daily: dailyPoints, attempts: attemptsInWindow),
            masteryRatePercent: calculateMasteryRate(skills: skills, quiz: quizStats)
        )
    }

    public static func calculateStreakDays(daily: [DailyMinutesPoint], attempts: [QuizAttemptSummary]) -> Int {
        if daily.isEmpty { return 0 }
        let calendar = utcCalendar()
        let activeDays = Set(daily.filter { $0.minutes > 0 }.map { $0.day })
        let quizDays = Set(attempts.filter { $0.passed }.map { attempt in
            let date = Date(timeIntervalSince1970: Double(attempt.createdAtEpochMs) / 1000.0)
            return isoFormatter.string(from: calendar.startOfDay(for: date))
        })
        let allActiveDays = activeDays.union(quizDays)
        let daysReversed = daily.map { $0.day }.reversed()

        guard let todayStr = daysReversed.first else { return 0 }
        let todayActive = allActiveDays.contains(todayStr)
        let startIndex = todayActive ? 0 : 1

        var streak = 0
        let daysList = Array(daysReversed)
        for i in startIndex..<daysList.count {
            if allActiveDays.contains(daysList[i]) {
                streak += 1
            } else {
                break
            }
        }
        return streak
    }

    public static func calculateMasteryRate(skills: [TopicSkillSummary], quiz: QuizWindowStats) -> Int? {
        let totalMastered = skills.reduce(0) { $0 + $1.masteredConcepts.count }
        let totalWeak = skills.reduce(0) { $0 + $1.weakConcepts.count }
        let totalConcepts = totalMastered + totalWeak
        if totalConcepts > 0 {
            return min(100, max(0, (totalMastered * 100) / totalConcepts))
        }
        if !skills.isEmpty {
            let avgLevel = Double(skills.reduce(0) { $0 + min(5, max(1, $1.level)) }) / Double(skills.count)
            return min(100, max(0, Int((avgLevel / 5.0) * 100)))
        }
        return quiz.accuracyPercent
    }

    public static func calendarAverageMinutes(totalMinutes: Int, days: Int) -> Int {
        if days <= 0 { return 0 }
        return (totalMinutes + days / 2) / days
    }

    public static func trendPercent(daily: [DailyMinutesPoint]) -> Int? {
        if daily.count < 4 { return nil }
        let half = daily.count / 2
        let firstHalf = daily.prefix(half).reduce(0) { $0 + $1.minutes }
        let secondHalf = daily.suffix(half).reduce(0) { $0 + $1.minutes }
        if firstHalf == 0 && secondHalf == 0 { return nil }
        if firstHalf == 0 { return nil }
        return ((secondHalf - firstHalf) * 100) / firstHalf
    }

    public static func educationalPercent(byApp: [AppMinutesRollup]) -> Int? {
        let attributable = byApp.filter { $0.packageName != "_other" && $0.minutes > 0 }
        let total = attributable.reduce(0) { $0 + $1.minutes }
        if total <= 0 { return nil }
        let edu = attributable
            .filter { AppInventoryCategorizer.classify(packageName: $0.packageName, label: $0.displayName).category == .educational }
            .reduce(0) { $0 + $1.minutes }
        return (edu * 100 + total / 2) / total
    }

    public static func resolveAppLabel(packageName: String, labels: [String: String]) -> String {
        if let custom = labels[packageName], !custom.trimmingCharacters(in: .whitespaces).isEmpty {
            return custom
        }
        let leaf = packageName.components(separatedBy: ".").last ?? packageName
        return humanizeToken(leaf.isEmpty ? packageName : leaf)
    }

    public static func humanizeToken(_ raw: String) -> String {
        if raw.trimmingCharacters(in: .whitespaces).isEmpty { return raw }
        let replaced = raw.replacingOccurrences(of: "_", with: " ")
            .replacingOccurrences(of: "-", with: " ")
        let tokens = replaced.components(separatedBy: .whitespaces).filter { !$0.isEmpty }
        return tokens.map { token in
            token.prefix(1).uppercased() + token.dropFirst()
        }.joined(separator: " ")
    }

    public static func topicLabel(_ topic: String) -> String {
        humanizeToken(topic)
    }

    public static func levelLabel(_ level: Int) -> String {
        let clamped = min(5, max(1, level))
        let tone: String
        switch clamped {
        case 1: tone = "Getting started"
        case 2: tone = "Building"
        case 3: tone = "On track"
        case 4: tone = "Strong"
        default: tone = "Advanced"
        }
        return "Level \(clamped) of 5 · \(tone)"
    }

    public static func shortDayLabel(day: String) -> String {
        guard let parsed = parseDay(day) else { return String(day.suffix(5)) }
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.locale = Locale(identifier: "en_US")
        return formatter.string(from: parsed)
    }

    public static func friendlyDayLabel(day: String) -> String {
        guard let parsed = parseDay(day) else { return day }
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.locale = Locale(identifier: "en_US")
        return formatter.string(from: parsed)
    }

    private static func rollupDayApps(
        _ minutesByApp: [String: Int],
        appLabels: [String: String]
    ) -> [AppMinutesRollup] {
        minutesByApp
            .filter { !$0.key.trimmingCharacters(in: .whitespaces).isEmpty && $0.value > 0 }
            .sorted { $0.value > $1.value }
            .map { pkg, minutes in
                AppMinutesRollup(
                    packageName: pkg,
                    displayName: resolveAppLabel(packageName: pkg, labels: appLabels),
                    minutes: minutes
                )
            }
    }

    private static func parseDay(_ day: String) -> Date? {
        isoFormatter.date(from: day)
    }

    private static func utcCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        return calendar
    }
}
