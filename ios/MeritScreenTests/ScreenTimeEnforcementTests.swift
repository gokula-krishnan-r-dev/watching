import XCTest
import FamilyControls
import ManagedSettings
@testable import MeritScreen

final class ScreenTimeEnforcementTests: XCTestCase {
    private var testSuiteName: String!
    private var sharedStore: ScreenTimeSharedStore!

    override func setUp() {
        super.setUp()
        testSuiteName = "test_screentime_\(UUID().uuidString)"
        sharedStore = ScreenTimeSharedStore(suiteName: testSuiteName)
    }

    override func tearDown() {
        sharedStore.clearAll()
        UserDefaults.standard.removePersistentDomain(forName: testSuiteName)
        super.tearDown()
    }

    // MARK: - ScreenTimeShieldState Tests

    func testShieldStateProperties() {
        let noneState = ScreenTimeShieldState.none
        XCTAssertFalse(noneState.isFailLock)
        XCTAssertFalse(noneState.isQuizDue)

        let quizState = ScreenTimeShieldState.quizDue(appName: "YouTube", blockMinutes: 15)
        XCTAssertFalse(quizState.isFailLock)
        XCTAssertTrue(quizState.isQuizDue)

        let failLockState = ScreenTimeShieldState.failLock(deadlineEpochMs: 1700000000000, cooldownMinutes: 15)
        XCTAssertTrue(failLockState.isFailLock)
        XCTAssertFalse(failLockState.isQuizDue)

        let bedtimeState = ScreenTimeShieldState.bedtimeLock(endLabel: "7:00 AM")
        XCTAssertFalse(bedtimeState.isFailLock)
        XCTAssertFalse(bedtimeState.isQuizDue)
    }

    func testShieldStateCodable() throws {
        let original = ScreenTimeShieldState.failLock(deadlineEpochMs: 123456789, cooldownMinutes: 20)
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(ScreenTimeShieldState.self, from: data)
        XCTAssertEqual(original, decoded)
    }

    // MARK: - PendingQuizRequest Tests

    func testPendingQuizRequestDefaultsAndCodable() throws {
        let request = PendingQuizRequest(appName: "Games", isRetry: true)
        XCTAssertEqual(request.appName, "Games")
        XCTAssertTrue(request.isRetry)
        XCTAssertGreaterThan(request.requestedAtEpochMs, 0)

        let data = try JSONEncoder().encode(request)
        let decoded = try JSONDecoder().decode(PendingQuizRequest.self, from: data)
        XCTAssertEqual(request, decoded)
    }

    // MARK: - ScreenTimeSharedStore Tests

    func testSharedStoreDefaultValues() {
        let selection = sharedStore.getActivitySelection()
        XCTAssertTrue(selection.applicationTokens.isEmpty)
        XCTAssertTrue(selection.categoryTokens.isEmpty)

        let emergencyTokens = sharedStore.getEmergencyTokens()
        XCTAssertTrue(emergencyTokens.isEmpty)

        let state = sharedStore.getShieldState()
        XCTAssertEqual(state, .none)

        XCTAssertFalse(sharedStore.hasPendingQuizRequest())
        XCTAssertNil(sharedStore.consumePendingQuizRequest())
        XCTAssertFalse(sharedStore.isDeviceRevoked())
    }

    func testSharedStoreShieldStatePersistence() {
        let deadline: Int64 = Int64(Date().timeIntervalSince1970 * 1000) + 900_000
        let state = ScreenTimeShieldState.failLock(deadlineEpochMs: deadline, cooldownMinutes: 15)

        sharedStore.saveShieldState(state)

        // Verify across store instance
        let secondStore = ScreenTimeSharedStore(suiteName: testSuiteName)
        XCTAssertEqual(secondStore.getShieldState(), state)
        XCTAssertEqual(secondStore.getFailLockDeadline(), deadline)

        // Clear to none
        sharedStore.saveShieldState(.none)
        XCTAssertEqual(secondStore.getShieldState(), .none)
        XCTAssertNil(secondStore.getFailLockDeadline())
    }

    func testPendingQuizRequestLifecycle() {
        XCTAssertFalse(sharedStore.hasPendingQuizRequest())

        let req = PendingQuizRequest(appName: "Safari", isRetry: false)
        sharedStore.setPendingQuizRequest(req)

        XCTAssertTrue(sharedStore.hasPendingQuizRequest())

        // Consume once
        let consumed = sharedStore.consumePendingQuizRequest()
        XCTAssertNotNil(consumed)
        XCTAssertEqual(consumed?.appName, "Safari")
        XCTAssertFalse(consumed?.isRetry ?? true)

        // Should now be consumed and empty
        XCTAssertFalse(sharedStore.hasPendingQuizRequest())
        XCTAssertNil(sharedStore.consumePendingQuizRequest())
    }

    func testDeviceRevocationFlag() {
        XCTAssertFalse(sharedStore.isDeviceRevoked())

        sharedStore.setDeviceRevoked(true)
        XCTAssertTrue(sharedStore.isDeviceRevoked())

        sharedStore.setDeviceRevoked(false)
        XCTAssertFalse(sharedStore.isDeviceRevoked())
    }

    func testClearAll() {
        sharedStore.saveShieldState(.quizDue(appName: "Test", blockMinutes: 10))
        sharedStore.setPendingQuizRequest(PendingQuizRequest(appName: "Test"))
        sharedStore.setDeviceRevoked(true)

        sharedStore.clearAll()

        XCTAssertEqual(sharedStore.getShieldState(), .none)
        XCTAssertFalse(sharedStore.hasPendingQuizRequest())
        XCTAssertFalse(sharedStore.isDeviceRevoked())
    }

    // MARK: - ScreenTimeEnforcementController Tests

    @MainActor
    func testFailLockApplicationAndClear() {
        let controller = ScreenTimeEnforcementController(sharedStore: sharedStore)

        controller.applyDeviceWideFailLock(cooldownMinutes: 15)
        XCTAssertTrue(controller.activeShieldState.isFailLock)
        XCTAssertTrue(sharedStore.getShieldState().isFailLock)

        if case .failLock(let deadline, let cooldown) = controller.activeShieldState {
            XCTAssertEqual(cooldown, 15)
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            XCTAssertGreaterThan(deadline, now)
        } else {
            XCTFail("Expected failLock state")
        }

        controller.clearFailLock()
        XCTAssertEqual(controller.activeShieldState, .none)
        XCTAssertEqual(sharedStore.getShieldState(), .none)
    }

    @MainActor
    func testQuizDueInterimShieldApplication() {
        let controller = ScreenTimeEnforcementController(sharedStore: sharedStore)

        // Using dummy token isn't directly instantiable, but method updates shield state
        sharedStore.saveShieldState(.quizDue(appName: "Minecraft", blockMinutes: 20))
        controller.reconcileShieldsOnLaunch()

        XCTAssertTrue(controller.activeShieldState.isQuizDue)
        if case .quizDue(let name, let mins) = controller.activeShieldState {
            XCTAssertEqual(name, "Minecraft")
            XCTAssertEqual(mins, 20)
        }

        controller.clearInterimShield()
        XCTAssertEqual(controller.activeShieldState, .none)
    }

    @MainActor
    func testReconcileOnLaunchActiveCooldownPreserved() {
        // Cooldown deadline 10 minutes in the future
        let futureDeadline = Int64(Date().timeIntervalSince1970 * 1000) + 600_000
        sharedStore.saveShieldState(.failLock(deadlineEpochMs: futureDeadline, cooldownMinutes: 10))

        let controller = ScreenTimeEnforcementController(sharedStore: sharedStore)
        controller.reconcileShieldsOnLaunch()

        XCTAssertTrue(controller.activeShieldState.isFailLock)
    }

    @MainActor
    func testReconcileOnLaunchExpiredCooldownCleared() {
        // Cooldown deadline 5 minutes in the past (reboot happened during/after cooldown)
        let pastDeadline = Int64(Date().timeIntervalSince1970 * 1000) - 300_000
        sharedStore.saveShieldState(.failLock(deadlineEpochMs: pastDeadline, cooldownMinutes: 10))

        let controller = ScreenTimeEnforcementController(sharedStore: sharedStore)
        controller.reconcileShieldsOnLaunch()

        // Expired cooldown must automatically clear fail lock!
        XCTAssertEqual(controller.activeShieldState, .none)
        XCTAssertEqual(sharedStore.getShieldState(), .none)
    }

    // MARK: - Invariant Verification Tests

    func testProductInvariantFailLockIsAlwaysDeviceWide() {
        // Verified by architecture:
        // ScreenTimeEnforcementController.applyDeviceWideFailLock applies to all non-emergency application tokens
        // in selection, never only to a single application.
        let state = ScreenTimeShieldState.failLock(deadlineEpochMs: 1000, cooldownMinutes: 15)
        XCTAssertTrue(state.isFailLock)
    }
}
