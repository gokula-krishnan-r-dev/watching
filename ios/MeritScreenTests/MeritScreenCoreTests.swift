import XCTest
@testable import MeritScreen

final class MeritScreenCoreTests: XCTestCase {

    // MARK: - AppConfig Parity Tests
    func testAppConfigParityWithAndroid() {
        XCTAssertEqual(AppConfig.maxChildrenPerParent, 5)
        XCTAssertEqual(AppConfig.parentPinMinLength, 4)
        XCTAssertEqual(AppConfig.parentPinMaxLength, 4)
        XCTAssertEqual(AppConfig.parentPinMaxAttempts, 5)
        XCTAssertEqual(AppConfig.parentPinLockoutMinutes, 5)
        XCTAssertEqual(AppConfig.defaultBlockMinutes, 15)
        XCTAssertEqual(AppConfig.defaultCooldownMinutes, 15)
        XCTAssertEqual(AppConfig.pairingTokenTtlMinutes, 10)
        XCTAssertEqual(AppConfig.pairingCodeLength, 6)
        XCTAssertEqual(AppConfig.defaultQuestionsPerQuiz, 3)
        XCTAssertEqual(AppConfig.defaultPassScorePercent, 70)
        XCTAssertEqual(AppConfig.defaultDailyCeilingMinutes, 120)
        XCTAssertEqual(AppConfig.explorerLevelMax, 10)
    }

    // MARK: - UiState Tests
    func testUiStateTransitions() {
        let loadingState: UiState<String> = .loading
        XCTAssertTrue(loadingState.isLoading)
        XCTAssertNil(loadingState.dataOrNull)
        XCTAssertNil(loadingState.errorOrNull)

        let successState: UiState<String> = .success("Active")
        XCTAssertFalse(successState.isLoading)
        XCTAssertEqual(successState.dataOrNull, "Active")
        XCTAssertNil(successState.errorOrNull)

        let error = AppError.network()
        let errorState: UiState<String> = .error(error)
        XCTAssertFalse(errorState.isLoading)
        XCTAssertNil(errorState.dataOrNull)
        XCTAssertEqual(errorState.errorOrNull, error)

        let emptyState: UiState<String> = .empty
        XCTAssertTrue(emptyState.isEmpty)
    }

    // MARK: - AppearanceStore Tests
    func testAppearanceStorePersistence() {
        let testDefaults = UserDefaults(suiteName: "test_appearance_\(UUID().uuidString)")!
        let store = AppearanceStore(defaults: testDefaults)

        XCTAssertEqual(store.mode, .system)
        XCTAssertNil(store.colorScheme)

        store.mode = .dark
        XCTAssertEqual(store.mode, .dark)
        XCTAssertEqual(store.colorScheme, .dark)

        // Verify loaded from defaults
        let newStoreInstance = AppearanceStore(defaults: testDefaults)
        XCTAssertEqual(newStoreInstance.mode, .dark)

        store.mode = .light
        XCTAssertEqual(store.mode, .light)
        XCTAssertEqual(store.colorScheme, .light)
    }

    // MARK: - PBKDF2 PIN Hasher Tests
    func testPbkdf2PinHasherVerification() {
        let hasher = Pbkdf2PinHasher.shared
        let pin = "1234"
        let hash = hasher.hash(pin: pin)

        XCTAssertTrue(hash.hasPrefix("pbkdf2$120000$"))
        XCTAssertTrue(hasher.verify(pin: "1234", storedHash: hash))
        XCTAssertFalse(hasher.verify(pin: "0000", storedHash: hash))
        XCTAssertFalse(hasher.verify(pin: "1235", storedHash: hash))
        XCTAssertFalse(hasher.verify(pin: "", storedHash: hash))
    }

    // MARK: - PinGateStore Lockout Tests
    func testPinGateStoreLockout() {
        let fakeStorage = FakeSecureStorage()
        let pinGate = PinGateStore(storage: fakeStorage)

        XCTAssertFalse(pinGate.get().isLocked)
        XCTAssertEqual(pinGate.get().failedAttempts, 0)

        // 4 failed attempts should not lock
        for i in 1...4 {
            let state = pinGate.recordFailedAttempt()
            XCTAssertEqual(state.failedAttempts, i)
            XCTAssertFalse(state.isLocked)
        }

        // 5th attempt should trigger 5-minute lockout
        let lockedState = pinGate.recordFailedAttempt()
        XCTAssertEqual(lockedState.failedAttempts, 5)
        XCTAssertTrue(lockedState.isLocked)
        XCTAssertGreaterThan(lockedState.remainingMinutes, 0)

        // Clear should reset attempts and lockout
        pinGate.clear()
        XCTAssertFalse(pinGate.get().isLocked)
        XCTAssertEqual(pinGate.get().failedAttempts, 0)
    }

    // MARK: - LogSanitizer Tests
    func testLogSanitizerRedaction() {
        XCTAssertEqual(LogSanitizer.sanitize("parent@example.com"), "<redacted>")
        XCTAssertEqual(LogSanitizer.sanitize("meritscreen://pair?c=123456&s=abc"), "<redacted>")
        XCTAssertEqual(LogSanitizer.sanitize("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"), "<redacted>")
        XCTAssertEqual(LogSanitizer.sanitize(key: "parentPin", value: "1234"), "<redacted>")
        XCTAssertEqual(LogSanitizer.sanitize(key: "fcmToken", value: "abcdef"), "<redacted>")
        XCTAssertEqual(LogSanitizer.sanitize("Device initialized successfully"), "Device initialized successfully")
    }

    // MARK: - ChildPolicy Default Tests
    func testChildPolicyDefaults() {
        let policy = ChildPolicy()
        XCTAssertEqual(policy.quizMode, .appBlock)
        XCTAssertTrue(policy.allowRetryDuringCooldown)
        XCTAssertEqual(policy.defaultBlockMinutes, 15)
        XCTAssertEqual(policy.defaultCooldownMinutes, 15)
        XCTAssertEqual(policy.questionsPerQuiz, 3)
        XCTAssertEqual(policy.passScorePercent, 70)
        XCTAssertTrue(policy.aiQuizzesEnabled)
        XCTAssertTrue(policy.adaptiveDifficultyEnabled)
        XCTAssertTrue(policy.showExplanations)
    }
}

// In-memory fake secure storage for test isolation
final class FakeSecureStorage: SecureStorageProtocol, @unchecked Sendable {
    private var dict: [String: String] = [:]

    func get(_ key: String) -> String? {
        dict[key]
    }

    func put(_ key: String, value: String) {
        dict[key] = value
    }

    func remove(_ key: String) {
        dict.removeValue(forKey: key)
    }
}
