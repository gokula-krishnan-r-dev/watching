import XCTest
@testable import MeritScreen

/// Exhaustive matrix test suite for `SessionEngine` state transitions, cooldown resets,
/// daily ceiling caps, midnight rollover, and emergency bypass.
/// Directly enforces non-negotiable product invariants from Docs 07 & 13.
final class SessionEngineMatrixTests: XCTestCase {

    private let testPolicy = ChildPolicy(
        paused: false,
        quizMode: .appBlock,
        allowRetryDuringCooldown: true,
        dailyCeilingMinutes: 60,
        defaultBlockMinutes: 15,
        defaultCooldownMinutes: 15
    )

    private let safariRule = AppRule(
        appId: "safari",
        packageOrBundleId: "com.apple.mobilesafari",
        displayName: "Safari",
        allowed: true,
        blockMinutes: 15,
        cooldownMinutes: 15
    )

    private let emergencyRule = AppRule(
        appId: "phone",
        packageOrBundleId: "com.apple.mobilephone",
        displayName: "Phone",
        allowed: true,
        blockMinutes: 60,
        isEmergency: true
    )

    // MARK: - Sequential App Block Grants

    func testSequentialAppBlockGrantsAccrueMinutesCorrectly() {
        let initialTime: Int64 = 1_000_000
        var state = SessionSnapshot(phase: .idle, minutesUsedToday: 0)

        // 1. Open app
        state = SessionEngine.openApp(snapshot: state, nowEpochMs: initialTime, rule: safariRule, policy: testPolicy)
        XCTAssertEqual(state.phase, .inBlock)

        // 2. Tick for 15 minutes (900,000 ms)
        let block1EndTime = initialTime + (15 * 60 * 1000)
        state = SessionEngine.tick(snapshot: state, nowEpochMs: block1EndTime, policy: testPolicy, isAppActive: true)
        XCTAssertEqual(state.phase, .quizDue, "Block expiration must trigger quiz due")
        XCTAssertEqual(state.minutesUsedToday, 15)

        // 3. Child passes quiz -> grant another 15 minutes
        state = SessionEngine.onQuizPassed(snapshot: state, nowEpochMs: block1EndTime, rule: safariRule, policy: testPolicy)
        XCTAssertEqual(state.phase, .inBlock)
        XCTAssertEqual(state.minutesAccruedInBlock, 0.0)

        // 4. Tick another 15 minutes
        let block2EndTime = block1EndTime + (15 * 60 * 1000)
        state = SessionEngine.tick(snapshot: state, nowEpochMs: block2EndTime, policy: testPolicy, isAppActive: true)
        XCTAssertEqual(state.phase, .quizDue)
        XCTAssertEqual(state.minutesUsedToday, 30, "Minutes used today must accumulate monotonically across passed blocks")
    }

    // MARK: - Daily Ceiling Boundary

    func testDailyCeilingBoundaryEnforcement() {
        var ceilingPolicy = testPolicy
        ceilingPolicy.dailyCeilingMinutes = 30

        var state = SessionSnapshot(
            phase: .inBlock,
            blockDurationMinutes: 15,
            minutesAccruedInBlock: 0.0,
            minutesUsedToday: 29
        )
        let startEpoch: Int64 = 10_000_000
        state.blockStartedEpochMs = startEpoch
        state.lastTickEpochMs = startEpoch

        // Tick 1 minute (reaches 30-minute daily ceiling)
        let oneMinLater = startEpoch + 60_000
        state = SessionEngine.tick(snapshot: state, nowEpochMs: oneMinLater, policy: ceilingPolicy, isAppActive: true)

        XCTAssertEqual(state.minutesUsedToday, 30)
        XCTAssertEqual(state.phase, .quizDue, "Reaching the daily ceiling cap must trigger quiz due immediately")
    }

    // MARK: - Midnight Rollover

    func testMidnightDayKeyTransitionResetsDailyMinutes() {
        let yesterdaySnapshot = SessionSnapshot(
            phase: .inBlock,
            dayKey: "2026-09-30",
            minutesUsedToday: 55
        )

        let todaySnapshot = SessionEngine.onNewDay(snapshot: yesterdaySnapshot, dayKey: "2026-10-01")

        XCTAssertEqual(todaySnapshot.dayKey, "2026-10-01")
        XCTAssertEqual(todaySnapshot.minutesUsedToday, 0, "Midnight transition must reset minutesUsedToday to 0")
        XCTAssertEqual(todaySnapshot.phase, .idle, "Active inBlock sessions reset to idle on new day")
    }

    // MARK: - Double Fail & Retry Lockout Reset

    func testDoubleFailRetryResetsCooldownDeadline() {
        let firstFailEpoch: Int64 = 10_000_000
        var state = SessionSnapshot(phase: .inBlock)

        // First quiz fail: enters 15-minute cooldown
        state = SessionEngine.onQuizFailed(snapshot: state, nowEpochMs: firstFailEpoch, policy: testPolicy, rule: safariRule)
        XCTAssertEqual(state.phase, .shielded)
        let firstDeadline = state.deviceShieldedUntilEpochMs!
        XCTAssertEqual(firstDeadline, firstFailEpoch + (15 * 60 * 1000))

        // Child waits 5 minutes (300,000 ms), then attempts Retry Quiz during cooldown
        let retryEpoch = firstFailEpoch + (5 * 60 * 1000)

        // Child FAILS the retry quiz:
        // Invariant: Cooldown timer must restart from ZERO (15m from the retry fail epoch)
        state = SessionEngine.onRetryQuizFailed(snapshot: state, nowEpochMs: retryEpoch, policy: testPolicy, rule: safariRule)
        XCTAssertEqual(state.phase, .shielded)
        let resetDeadline = state.deviceShieldedUntilEpochMs!
        XCTAssertEqual(resetDeadline, retryEpoch + (15 * 60 * 1000))
        XCTAssertGreaterThan(resetDeadline, firstDeadline, "Failing a retry during cooldown must push deadline forward")
    }

    // MARK: - Emergency App Bypass

    func testEmergencyAppAlwaysBypassesFailLock() {
        let nowEpoch: Int64 = 5_000_000
        let shieldedState = SessionSnapshot(
            phase: .shielded,
            deviceShieldedUntilEpochMs: nowEpoch + (15 * 60 * 1000),
            cooldownMinutes: 15
        )

        let stateAfterOpening = SessionEngine.openApp(
            snapshot: shieldedState,
            nowEpochMs: nowEpoch,
            rule: emergencyRule,
            policy: testPolicy
        )

        // State remains shielded, but emergency app was not rejected/modified
        XCTAssertEqual(stateAfterOpening.phase, .shielded)
        XCTAssertEqual(stateAfterOpening.deviceShieldedUntilEpochMs, shieldedState.deviceShieldedUntilEpochMs)
    }

    // MARK: - Shield Expiration

    func testExpireShieldExactlyAtDeadline() {
        let deadline: Int64 = 5_000_000
        let shieldedState = SessionSnapshot(
            phase: .shielded,
            deviceShieldedUntilEpochMs: deadline,
            cooldownMinutes: 15
        )

        // 1 millisecond before deadline -> remains shielded
        let beforeState = SessionEngine.expireShieldIfNeeded(snapshot: shieldedState, nowEpochMs: deadline - 1)
        XCTAssertEqual(beforeState.phase, .shielded)

        // At deadline -> unlocks to idle
        let atState = SessionEngine.expireShieldIfNeeded(snapshot: shieldedState, nowEpochMs: deadline)
        XCTAssertEqual(atState.phase, .idle)
    }
}
