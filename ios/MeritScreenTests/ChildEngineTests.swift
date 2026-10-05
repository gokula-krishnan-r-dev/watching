import XCTest
@testable import MeritScreen

final class ChildEngineTests: XCTestCase {

    var samplePolicy: ChildPolicy!
    var sampleRule: AppRule!
    var emergencyRule: AppRule!

    override func setUp() {
        super.setUp()
        samplePolicy = ChildPolicy(
            paused: false,
            quizMode: .appBlock,
            allowRetryDuringCooldown: true,
            dailyCeilingMinutes: 60,
            questionsPerQuiz: 3,
            passScorePercent: 70,
            rewardsEnabled: true,
            weekendBonusEnabled: false,
            extraMinutesOnPass: 5,
            defaultBlockMinutes: 15,
            defaultCooldownMinutes: 15
        )

        sampleRule = AppRule(
            appId: "com.apple.mobilesafari",
            packageOrBundleId: "com.apple.mobilesafari",
            displayName: "Safari",
            allowed: true,
            blockMinutes: 15,
            grantOnPassMinutes: 15,
            cooldownMinutes: 15,
            isEmergency: false
        )

        emergencyRule = AppRule(
            appId: "com.apple.mobilephone",
            packageOrBundleId: "com.apple.mobilephone",
            displayName: "Phone",
            allowed: true,
            blockMinutes: 120,
            grantOnPassMinutes: 120,
            cooldownMinutes: 0,
            isEmergency: true
        )
    }

    // MARK: - Session Engine Tests

    func testNewDayResetsMinutesUsedToday() {
        let snapshot = SessionSnapshot(
            phase: .inBlock,
            activePackageOrBundleId: "com.apple.mobilesafari",
            dayKey: "2026-09-30",
            minutesUsedToday: 45
        )

        let updated = SessionEngine.onNewDay(snapshot: snapshot, dayKey: "2026-10-01")

        XCTAssertEqual(updated.dayKey, "2026-10-01")
        XCTAssertEqual(updated.minutesUsedToday, 0)
        XCTAssertEqual(updated.phase, .idle)
        XCTAssertNil(updated.activePackageOrBundleId)
    }

    func testOpenAppSetsInBlockPhase() {
        let initial = SessionSnapshot(phase: .idle)
        let now: Int64 = 100_000_000

        let state = SessionEngine.openApp(
            snapshot: initial,
            nowEpochMs: now,
            rule: sampleRule,
            policy: samplePolicy
        )

        XCTAssertEqual(state.phase, .inBlock)
        XCTAssertEqual(state.activePackageOrBundleId, sampleRule.packageOrBundleId)
        XCTAssertEqual(state.blockDurationMinutes, 15)
        XCTAssertEqual(state.blockStartedEpochMs, now)
        XCTAssertEqual(state.minutesAccruedInBlock, 0.0)
    }

    func testAppBlockTickAccumulatesTimeAndTriggersQuizDue() {
        var state = SessionSnapshot(
            phase: .inBlock,
            activePackageOrBundleId: sampleRule.packageOrBundleId,
            blockStartedEpochMs: 1_000_000,
            blockDurationMinutes: 15,
            minutesAccruedInBlock: 0.0,
            lastTickEpochMs: 1_000_000
        )

        // Advance 10 minutes (600,000 ms)
        state = SessionEngine.tick(
            snapshot: state,
            nowEpochMs: 1_600_000,
            policy: samplePolicy,
            isAppActive: true
        )
        XCTAssertEqual(state.phase, .inBlock)
        XCTAssertEqual(state.minutesUsedToday, 10)
        XCTAssertEqual(state.minutesAccruedInBlock, 10.0, accuracy: 0.1)

        // Advance another 5 minutes (total 15 minutes = block duration hit)
        state = SessionEngine.tick(
            snapshot: state,
            nowEpochMs: 1_900_000,
            policy: samplePolicy,
            isAppActive: true
        )
        XCTAssertEqual(state.phase, .quizDue, "Block expired must transition to quizDue")
        XCTAssertNil(state.blockStartedEpochMs)
    }

    func testDailyCeilingHitTriggersQuizDue() {
        var state = SessionSnapshot(
            phase: .inBlock,
            activePackageOrBundleId: sampleRule.packageOrBundleId,
            blockStartedEpochMs: 1_000_000,
            blockDurationMinutes: 30,
            minutesAccruedInBlock: 0.0,
            minutesUsedToday: 55, // 5 mins until 60 min ceiling
            lastTickEpochMs: 1_000_000
        )

        // Advance 6 minutes -> passes ceiling of 60 mins
        state = SessionEngine.tick(
            snapshot: state,
            nowEpochMs: 1_360_000,
            policy: samplePolicy,
            isAppActive: true
        )

        XCTAssertEqual(state.phase, .quizDue, "Reaching daily ceiling must transition to quizDue")
    }

    func testOpenAppEmergencyAlwaysAllowed() {
        let shieldedState = SessionSnapshot(
            phase: .shielded,
            deviceShieldedUntilEpochMs: 5_000_000
        )

        let result = SessionEngine.canLaunch(
            snapshot: shieldedState,
            rule: emergencyRule,
            policy: samplePolicy
        )
        XCTAssertTrue(result, "Emergency apps like Phone must always be allowed during fail lock")
    }

    func testQuizPassedGrantsNextBlockAndSetsGraceWindow() {
        let quizDueState = SessionSnapshot(
            phase: .quizDue,
            activePackageOrBundleId: sampleRule.packageOrBundleId,
            minutesUsedToday: 60 // At daily ceiling
        )
        let now: Int64 = 2_000_000

        let granted = SessionEngine.onQuizPassed(
            snapshot: quizDueState,
            nowEpochMs: now,
            rule: sampleRule,
            policy: samplePolicy
        )

        XCTAssertEqual(granted.phase, .inBlock)
        // grantOnPassMinutes (15) + extraMinutesOnPass (5) = 20
        XCTAssertEqual(granted.blockDurationMinutes, 20)
        XCTAssertEqual(granted.minutesAccruedInBlock, 0.0)
        XCTAssertNil(granted.deviceShieldedUntilEpochMs)
        XCTAssertNotNil(granted.quizGraceUntilEpochMs)
        XCTAssertGreaterThan(granted.quizGraceUntilEpochMs!, now)
    }

    func testQuizFailedEntersDeviceWideFailLock() {
        let quizDueState = SessionSnapshot(
            phase: .quizDue,
            activePackageOrBundleId: sampleRule.packageOrBundleId
        )
        let now: Int64 = 2_000_000

        let failed = SessionEngine.onQuizFailed(
            snapshot: quizDueState,
            nowEpochMs: now,
            policy: samplePolicy,
            rule: sampleRule
        )

        XCTAssertEqual(failed.phase, .shielded)
        XCTAssertEqual(failed.cooldownMinutes, 15)
        let expectedUntil = now + Int64(15 * 60_000)
        XCTAssertEqual(failed.deviceShieldedUntilEpochMs, expectedUntil)
        XCTAssertNil(failed.blockStartedEpochMs)
    }

    func testRetryQuizFailedRestartsCooldownTimer() {
        let now: Int64 = 2_000_000
        let originalUntil = now + Int64(15 * 60_000)

        let currentlyShielded = SessionSnapshot(
            phase: .shielded,
            deviceShieldedUntilEpochMs: originalUntil,
            cooldownMinutes: 15
        )

        // 10 minutes pass; child takes retry quiz and fails at now + 600,000 ms
        let retryAttemptTime: Int64 = now + 600_000
        let restartState = SessionEngine.onRetryQuizFailed(
            snapshot: currentlyShielded,
            nowEpochMs: retryAttemptTime,
            policy: samplePolicy,
            rule: sampleRule
        )

        XCTAssertEqual(restartState.phase, .shielded)
        // Product locked invariant: Cooldown RESTARTS from retryAttemptTime!
        let newExpectedUntil = retryAttemptTime + Int64(15 * 60_000)
        XCTAssertEqual(restartState.deviceShieldedUntilEpochMs, newExpectedUntil)
        XCTAssertGreaterThan(restartState.deviceShieldedUntilEpochMs!, originalUntil)
    }

    func testExpireShieldUnlocksWhenCooldownEnds() {
        let now: Int64 = 2_000_000
        let shielded = SessionSnapshot(
            phase: .shielded,
            deviceShieldedUntilEpochMs: now - 1000 // In the past
        )

        let expired = SessionEngine.expireShieldIfNeeded(snapshot: shielded, nowEpochMs: now)
        XCTAssertEqual(expired.phase, .idle)
        XCTAssertNil(expired.deviceShieldedUntilEpochMs)
    }

    func testParentPinOverrideEndsFailLockImmediately() {
        let shielded = SessionSnapshot(
            phase: .shielded,
            deviceShieldedUntilEpochMs: 5_000_000
        )

        let unlocked = SessionEngine.endFailLock(snapshot: shielded)
        XCTAssertEqual(unlocked.phase, .idle)
        XCTAssertNil(unlocked.deviceShieldedUntilEpochMs)
    }

    // MARK: - Adaptive Quiz Engine Tests

    func testAdaptiveQuizEngineLoadsQuestions() {
        let questions = AdaptiveQuizEngine.loadBuiltinQuizBank()
        XCTAssertFalse(questions.isEmpty, "Builtin quiz bank must contain questions")

        let mathQuestion = questions.first { $0.topic == "math" }
        XCTAssertNotNil(mathQuestion)
        XCTAssertFalse(mathQuestion!.choices.isEmpty)
        XCTAssertTrue(mathQuestion!.choices.contains { $0.correct })
    }

    func testAdaptiveQuizEngineFiltersByAgeBand() {
        let bank = AdaptiveQuizEngine.loadBuiltinQuizBank()
        let picked36 = AdaptiveQuizEngine.pickNext(bank: bank, targetAgeBand: .band_3_6)
        let picked79 = AdaptiveQuizEngine.pickNext(bank: bank, targetAgeBand: .band_7_9)

        XCTAssertNotNil(picked36)
        XCTAssertNotNil(picked79)
        XCTAssertEqual(picked36?.ageBand, .band_3_6)
        XCTAssertEqual(picked79?.ageBand, .band_7_9)
    }

    func testAdaptiveQuizEngineGradingIncreasesLevelAndMastery() {
        let question = QuizQuestion(
            id: "test_q1",
            ageBand: .band_7_9,
            topic: "math",
            conceptId: "addition",
            conceptTitle: "Basic Addition",
            difficulty: 2,
            prompt: "What is 2 + 2?",
            choices: [
                QuizChoice(id: "a", text: "3", correct: false),
                QuizChoice(id: "b", text: "4", correct: true)
            ],
            whyCorrect: "2 + 2 = 4",
            conceptExplainer: "Add two groups of two"
        )

        let skill = TopicSkill(topic: "math", level: 2, streakCorrect: 1)
        let (feedback, updatedSkill) = AdaptiveQuizEngine.gradeAnswer(
            question: question,
            choiceId: "b",
            skill: skill,
            responseTimeMs: 3000
        )

        XCTAssertTrue(feedback.correct)
        XCTAssertEqual(updatedSkill.level, 3, "2 consecutive correct answers must promote to level 3")
        XCTAssertEqual(updatedSkill.totalCorrect, 1)
        XCTAssertEqual(updatedSkill.totalAttempts, 1)
    }

    func testAdaptiveQuizEngineGradingDecreasesLevelOnIncorrect() {
        let question = QuizQuestion(
            id: "test_q2",
            ageBand: .band_7_9,
            topic: "math",
            conceptId: "fractions",
            conceptTitle: "Fractions",
            difficulty: 3,
            prompt: "What is 1/2 of 10?",
            choices: [
                QuizChoice(id: "a", text: "5", correct: true),
                QuizChoice(id: "b", text: "4", correct: false)
            ],
            whyCorrect: "Half of 10 is 5",
            conceptExplainer: "Divide 10 by 2"
        )

        let skill = TopicSkill(topic: "math", level: 3, streakCorrect: 2)
        let (feedback, updatedSkill) = AdaptiveQuizEngine.gradeAnswer(
            question: question,
            choiceId: "b",
            skill: skill,
            responseTimeMs: 4000
        )

        XCTAssertFalse(feedback.correct)
        XCTAssertEqual(updatedSkill.level, 2, "Wrong answer must demote to level 2")
        XCTAssertEqual(updatedSkill.streakCorrect, 0)
        XCTAssertTrue(updatedSkill.weakConcepts.contains("fractions"))
    }

    func testAdaptiveQuizEngineFinalize() {
        let passResult = AdaptiveQuizEngine.finalize(
            correctCount: 3,
            total: 3,
            passScorePercent: 70,
            rule: sampleRule,
            policy: samplePolicy
        )
        XCTAssertTrue(passResult.passed)
        XCTAssertEqual(passResult.percent, 100)
        XCTAssertGreaterThan(passResult.unlockedMinutes, 0)
        XCTAssertGreaterThan(passResult.xpGained, 0)

        let failResult = AdaptiveQuizEngine.finalize(
            correctCount: 1,
            total: 3,
            passScorePercent: 70,
            rule: sampleRule,
            policy: samplePolicy
        )
        XCTAssertFalse(failResult.passed)
        XCTAssertEqual(failResult.percent, 33)
        XCTAssertEqual(failResult.unlockedMinutes, 0)
        XCTAssertEqual(failResult.xpGained, 0)
    }

    // MARK: - Child Local Store Tests

    func testChildLocalStorePersistence() {
        let defaults = UserDefaults(suiteName: "ChildEngineTestsDefaults")!
        defaults.removePersistentDomain(forName: "ChildEngineTestsDefaults")
        let store = ChildLocalStore(defaults: defaults)

        let snapshot = SessionSnapshot(
            phase: .inBlock,
            activePackageOrBundleId: "com.apple.mobilesafari",
            minutesUsedToday: 25
        )
        store.saveSessionSnapshot(snapshot)

        let retrieved = store.getSessionSnapshot()
        XCTAssertEqual(retrieved.phase, .inBlock)
        XCTAssertEqual(retrieved.activePackageOrBundleId, "com.apple.mobilesafari")
        XCTAssertEqual(retrieved.minutesUsedToday, 25)

        // Test XP and stickers
        _ = store.addXp(45)
        XCTAssertEqual(store.getTotalXp(), 45)
        XCTAssertEqual(store.getExplorerLevel(), 2)

        let sticker = ChildSticker(id: "star_1", title: "Star 1", emoji: "⭐")
        store.addSticker(sticker)
        let stickers = store.getUnlockedStickers()
        XCTAssertTrue(stickers.contains { $0.id == "star_1" })
    }
}
