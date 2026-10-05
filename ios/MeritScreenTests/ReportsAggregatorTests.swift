import XCTest
@testable import MeritScreen

final class ReportsAggregatorTests: XCTestCase {

    private static let isoFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    private func createDate(year: Int, month: Int, day: Int) -> Date {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.timeZone = TimeZone(secondsFromGMT: 0)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar.date(from: components)!
    }

    // MARK: - Calendar Average Tests

    func testCalendarAverageMinutes() {
        XCTAssertEqual(ReportsAggregator.calendarAverageMinutes(totalMinutes: 70, days: 7), 10)
        XCTAssertEqual(ReportsAggregator.calendarAverageMinutes(totalMinutes: 75, days: 7), 11) // 75 + 3 = 78 / 7 = 11
        XCTAssertEqual(ReportsAggregator.calendarAverageMinutes(totalMinutes: 0, days: 7), 0)
        XCTAssertEqual(ReportsAggregator.calendarAverageMinutes(totalMinutes: 50, days: 0), 0)
    }

    // MARK: - Trend Percentage Tests

    func testTrendPercentage() {
        // Less than 4 points -> nil
        let shortSeries = [
            DailyMinutesPoint(day: "2026-10-01", minutes: 30),
            DailyMinutesPoint(day: "2026-10-02", minutes: 40)
        ]
        XCTAssertNil(ReportsAggregator.trendPercent(daily: shortSeries))

        // 4 points: first half (10 + 20 = 30), second half (30 + 30 = 60). Trend = (60 - 30) * 100 / 30 = +100%
        let positiveTrend = [
            DailyMinutesPoint(day: "2026-10-01", minutes: 10),
            DailyMinutesPoint(day: "2026-10-02", minutes: 20),
            DailyMinutesPoint(day: "2026-10-03", minutes: 30),
            DailyMinutesPoint(day: "2026-10-04", minutes: 30)
        ]
        XCTAssertEqual(ReportsAggregator.trendPercent(daily: positiveTrend), 100)

        // Decreasing trend: first half (50 + 50 = 100), second half (25 + 25 = 50). Trend = (50 - 100) * 100 / 100 = -50%
        let negativeTrend = [
            DailyMinutesPoint(day: "2026-10-01", minutes: 50),
            DailyMinutesPoint(day: "2026-10-02", minutes: 50),
            DailyMinutesPoint(day: "2026-10-03", minutes: 25),
            DailyMinutesPoint(day: "2026-10-04", minutes: 25)
        ]
        XCTAssertEqual(ReportsAggregator.trendPercent(daily: negativeTrend), -50)

        // All zero -> nil
        let allZero = [
            DailyMinutesPoint(day: "2026-10-01", minutes: 0),
            DailyMinutesPoint(day: "2026-10-02", minutes: 0),
            DailyMinutesPoint(day: "2026-10-03", minutes: 0),
            DailyMinutesPoint(day: "2026-10-04", minutes: 0)
        ]
        XCTAssertNil(ReportsAggregator.trendPercent(daily: allZero))
    }

    // MARK: - Educational Percentage Tests

    func testEducationalPercent() {
        let apps = [
            AppMinutesRollup(packageName: "org.khanacademy.android", displayName: "Khan Academy", minutes: 60),
            AppMinutesRollup(packageName: "com.roblox.client", displayName: "Roblox", minutes: 40)
        ]
        // 60 / 100 = 60%
        let eduPct = ReportsAggregator.educationalPercent(byApp: apps)
        XCTAssertEqual(eduPct, 60)

        // Empty apps -> nil
        XCTAssertNil(ReportsAggregator.educationalPercent(byApp: []))
    }

    // MARK: - Streak Days Tests

    func testStreakDaysCalculation() {
        let daily = [
            DailyMinutesPoint(day: "2026-09-28", minutes: 20),
            DailyMinutesPoint(day: "2026-09-29", minutes: 0),
            DailyMinutesPoint(day: "2026-09-30", minutes: 45),
            DailyMinutesPoint(day: "2026-10-01", minutes: 30) // today
        ]
        // Today (10-01) active, 09-30 active, 09-29 inactive -> streak = 2
        let streak = ReportsAggregator.calculateStreakDays(daily: daily, attempts: [])
        XCTAssertEqual(streak, 2)

        // Streak when today has 0 minutes, but yesterday was active
        let dailyYesterdayActive = [
            DailyMinutesPoint(day: "2026-09-28", minutes: 0),
            DailyMinutesPoint(day: "2026-09-29", minutes: 30),
            DailyMinutesPoint(day: "2026-09-30", minutes: 45),
            DailyMinutesPoint(day: "2026-10-01", minutes: 0) // today not active yet
        ]
        // Starts from yesterday: 09-30 (active), 09-29 (active), 09-28 (inactive) -> streak = 2
        let streakYesterday = ReportsAggregator.calculateStreakDays(daily: dailyYesterdayActive, attempts: [])
        XCTAssertEqual(streakYesterday, 2)
    }

    // MARK: - Mastery Rate Tests

    func testMasteryRateCalculation() {
        let quiz = QuizWindowStats(
            attemptCount: 10,
            passedCount: 8,
            failedCount: 2,
            accuracyPercent: 80,
            questionsCorrect: 24,
            questionsTotal: 30,
            extraMinutesEarned: 120
        )

        let skills = [
            TopicSkillSummary(
                topic: "math",
                level: 3,
                streakCorrect: 5,
                weak: false,
                weakConcepts: ["division"],
                masteredConcepts: ["addition", "multiplication"]
            )
        ]
        // 2 mastered out of 3 concepts = 66%
        let rate = ReportsAggregator.calculateMasteryRate(skills: skills, quiz: quiz)
        XCTAssertEqual(rate, 66)

        // Fallback to average level when no concept breakdown
        let emptyConceptsSkills = [
            TopicSkillSummary(topic: "reading", level: 4, streakCorrect: 3, weak: false)
        ]
        // Level 4 / 5 = 80%
        let levelRate = ReportsAggregator.calculateMasteryRate(skills: emptyConceptsSkills, quiz: quiz)
        XCTAssertEqual(levelRate, 80)
    }

    // MARK: - Full Window Aggregation Tests

    func testFullReportAggregation7Days() {
        let refDate = createDate(year: 2026, month: 10, day: 1)

        let usage = [
            UsageDaySummary(
                day: "2026-09-30",
                minutesUsed: 40,
                minutesByApp: ["com.duolingo": 25, "com.youtube": 15]
            ),
            UsageDaySummary(
                day: "2026-10-01",
                minutesUsed: 60,
                minutesByApp: ["org.khanacademy.android": 40, "com.roblox.client": 20]
            )
        ]

        let attempts = [
            QuizAttemptSummary(
                attemptId: "att_1",
                score: 3,
                total: 3,
                passed: true,
                extraMinutesGranted: 15,
                topics: ["math_fractions"],
                createdAtEpochMs: Int64(refDate.timeIntervalSince1970 * 1000)
            )
        ]

        let skills = [
            TopicSkillSummary(
                topic: "math_fractions",
                level: 3,
                streakCorrect: 4,
                weak: false,
                weakConcepts: [],
                masteredConcepts: ["half_fractions"],
                tierLabel: "intermediate"
            )
        ]

        let report = ReportsAggregator.build(
            days: 7,
            usage: usage,
            attempts: attempts,
            skills: skills,
            appLabels: ["com.duolingo": "Duolingo", "com.youtube": "YouTube"],
            referenceDate: refDate
        )

        XCTAssertEqual(report.days, 7)
        XCTAssertEqual(report.daily.count, 7)
        XCTAssertEqual(report.totalMinutes, 100)
        XCTAssertEqual(report.averageMinutesPerDay, 14) // (100 + 3) / 7 = 14
        XCTAssertTrue(report.hasAnyData)

        // Quiz stats verification
        XCTAssertEqual(report.quiz.attemptCount, 1)
        XCTAssertEqual(report.quiz.passedCount, 1)
        XCTAssertEqual(report.quiz.accuracyPercent, 100)
        XCTAssertEqual(report.quiz.extraMinutesEarned, 15)

        // App rollups verification
        XCTAssertFalse(report.byApp.isEmpty)
        let khan = report.byApp.first { $0.packageName == "org.khanacademy.android" }
        XCTAssertNotNil(khan)
        XCTAssertEqual(khan?.minutes, 40)
    }

    // MARK: - Balanced Score Tests

    func testBalancedScoreCalculation() {
        let emptyReport = ReportsAggregator.build(days: 7, usage: [], attempts: [], skills: [])
        XCTAssertNil(BalancedScoreCalculator.calculate(report: emptyReport, educationalPercent: nil))

        let (emptyLabel, _) = BalancedScoreCalculator.copy(score: nil, report: emptyReport)
        XCTAssertEqual(emptyLabel, "—")

        // Report with 90% accuracy and 80% educational
        let usage = [
            UsageDaySummary(day: "2026-10-01", minutesUsed: 60, minutesByApp: ["org.khanacademy.android": 60])
        ]
        let attempts = [
            QuizAttemptSummary(attemptId: "1", score: 3, total: 3, passed: true, extraMinutesGranted: 15, topics: [], createdAtEpochMs: 0)
        ]
        let activeReport = ReportsAggregator.build(
            days: 1,
            usage: usage,
            attempts: attempts,
            skills: [],
            referenceDate: createDate(year: 2026, month: 10, day: 1)
        )

        let score = BalancedScoreCalculator.calculate(report: activeReport, educationalPercent: 100)
        XCTAssertNotNil(score)
        XCTAssertGreaterThanOrEqual(score!, 80)

        let (scoreLabel, _) = BalancedScoreCalculator.copy(score: score, report: activeReport)
        XCTAssertEqual(scoreLabel, "Great!")
    }

    // MARK: - Token Humanization Tests

    func testHumanizeToken() {
        XCTAssertEqual(ReportsAggregator.humanizeToken("add_within_20"), "Add Within 20")
        XCTAssertEqual(ReportsAggregator.humanizeToken("geometry-angles"), "Geometry Angles")
        XCTAssertEqual(ReportsAggregator.humanizeToken("math"), "Math")
        XCTAssertEqual(ReportsAggregator.humanizeToken(""), "")
    }
}
