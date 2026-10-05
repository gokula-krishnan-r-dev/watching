import XCTest
@testable import MeritScreen

final class SecurityHardeningTests: XCTestCase {

    private var fakeSecureStorage: FakeSecureStorage!
    private var testDefaults: UserDefaults!

    override func setUp() {
        super.setUp()
        fakeSecureStorage = FakeSecureStorage()
        let suite = "test_security_\(UUID().uuidString)"
        testDefaults = UserDefaults(suiteName: suite)!
    }

    override func tearDown() {
        fakeSecureStorage = nil
        super.tearDown()
    }

    // MARK: - PBKDF2 PIN Hasher Tests

    func testPbkdf2PinHasherFormatAndVerification() {
        let hasher = Pbkdf2PinHasher.shared
        let pin = "1234"
        let hash = hasher.hash(pin: pin)

        // Must start with pbkdf2$120000$ (matching Android)
        XCTAssertTrue(hash.hasPrefix("pbkdf2$120000$"))

        let parts = hash.components(separatedBy: "$")
        XCTAssertEqual(parts.count, 4)
        XCTAssertEqual(parts[0], "pbkdf2")
        XCTAssertEqual(parts[1], "120000")
        XCTAssertFalse(parts[2].isEmpty) // salt
        XCTAssertFalse(parts[3].isEmpty) // hash

        // Correct PIN verifies
        XCTAssertTrue(hasher.verify(pin: "1234", storedHash: hash))

        // Incorrect PIN fails
        XCTAssertFalse(hasher.verify(pin: "0000", storedHash: hash))
        XCTAssertFalse(hasher.verify(pin: "1235", storedHash: hash))
        XCTAssertFalse(hasher.verify(pin: "", storedHash: hash))
    }

    func testPbkdf2SaltRandomness() {
        let hasher = Pbkdf2PinHasher.shared
        let pin = "5678"
        let hash1 = hasher.hash(pin: pin)
        let hash2 = hasher.hash(pin: pin)

        // Hashes of the same PIN must have distinct salts
        XCTAssertNotEqual(hash1, hash2)
        XCTAssertTrue(hasher.verify(pin: pin, storedHash: hash1))
        XCTAssertTrue(hasher.verify(pin: pin, storedHash: hash2))
    }

    // MARK: - PIN Gate Store & Lockout Tests

    func testPinGateStoreLockoutLifecycle() {
        let gateStore = PinGateStore(storage: fakeSecureStorage)
        XCTAssertFalse(gateStore.get().isLocked)
        XCTAssertEqual(gateStore.get().failedAttempts, 0)

        // Attempts 1 to 4 do not lock
        for attempt in 1..<AppConfig.parentPinMaxAttempts {
            let state = gateStore.recordFailedAttempt()
            XCTAssertFalse(state.isLocked)
            XCTAssertEqual(state.failedAttempts, attempt)
        }

        // 5th attempt locks the gate for 15 minutes
        let lockedState = gateStore.recordFailedAttempt()
        XCTAssertTrue(lockedState.isLocked)
        XCTAssertEqual(lockedState.failedAttempts, AppConfig.parentPinMaxAttempts)
        XCTAssertGreaterThan(lockedState.remainingMinutes, 0)
        XCTAssertLessThanOrEqual(lockedState.remainingMinutes, AppConfig.parentPinLockoutMinutes)
    }

    func testPinGateStoreLockoutPersistenceAcrossReboot() {
        let gateStore1 = PinGateStore(storage: fakeSecureStorage)
        for _ in 0..<AppConfig.parentPinMaxAttempts {
            _ = gateStore1.recordFailedAttempt()
        }
        XCTAssertTrue(gateStore1.get().isLocked)

        // Simulate app kill / reboot by creating a fresh instance with the same secure storage
        let gateStore2 = PinGateStore(storage: fakeSecureStorage)
        let state = gateStore2.get()
        XCTAssertTrue(state.isLocked, "Lockout must survive process restart in secure storage")
        XCTAssertEqual(state.failedAttempts, AppConfig.parentPinMaxAttempts)

        // Clear resets lockout
        gateStore2.clear()
        XCTAssertFalse(gateStore2.get().isLocked)
        XCTAssertEqual(gateStore2.get().failedAttempts, 0)
    }

    // MARK: - Log Sanitizer Tests

    func testLogSanitizerRedactions() {
        // Emails must be redacted
        XCTAssertEqual(LogSanitizer.sanitize("User logged in with parent@meritscreen.com"), "<redacted>")

        // JWT tokens must be redacted
        XCTAssertEqual(LogSanitizer.sanitize("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-ID"), "<redacted>")

        // Pairing deep links must be redacted
        XCTAssertEqual(LogSanitizer.sanitize("meritscreen://pair?code=123456&family=fam1"), "<redacted>")

        // Sensitive dictionary keys must be redacted
        XCTAssertEqual(LogSanitizer.sanitize(key: "parentPin", value: "1234"), "<redacted>")
        XCTAssertEqual(LogSanitizer.sanitize(key: "secretKey", value: "abc123xyz"), "<redacted>")
        XCTAssertEqual(LogSanitizer.sanitize(key: "customToken", value: "tok_999"), "<redacted>")
        XCTAssertEqual(LogSanitizer.sanitize(key: "fcmToken", value: "fcm_token_123"), "<redacted>")

        // Benign logs should not be redacted
        XCTAssertEqual(LogSanitizer.sanitize("Heartbeat scheduled successfully"), "Heartbeat scheduled successfully")
        XCTAssertEqual(LogSanitizer.sanitize(key: "action", value: "quiz_completed"), "quiz_completed")
    }

    // MARK: - Child Session Wiper Tests

    @MainActor
    func testChildSessionWiperWipesAllAndPreservesQuizBank() {
        let pairingStore = ChildPairingStore(secureStorage: fakeSecureStorage)
        let localStore = ChildLocalStore(defaults: testDefaults)
        let sharedStore = ScreenTimeSharedStore(suiteName: "test_wiper_\(UUID().uuidString)")
        let gateStore = PinGateStore(storage: fakeSecureStorage)

        // Seed data
        pairingStore.set(ChildPairingCredential(
            familyId: "fam_wipe",
            childId: "child_wipe",
            deviceId: "dev_wipe",
            parentPinHash: "hash",
            displayName: "Leo"
        ))
        var snapshot = localStore.getSessionSnapshot()
        snapshot.minutesUsedToday = 90
        localStore.saveSessionSnapshot(snapshot)
        sharedStore.saveShieldState(.failLock(deadlineEpochMs: 12345, cooldownMinutes: 15))

        let wiper = ChildSessionWiper(
            pairingStore: pairingStore,
            localStore: localStore,
            sharedStore: sharedStore,
            pinGateStore: gateStore
        )

        // Execute wipe
        wiper.wipeAllChildData()

        // Credentials & state must be wiped
        XCTAssertNil(pairingStore.current())
        XCTAssertEqual(localStore.getSessionSnapshot().minutesUsedToday, 0)
        XCTAssertTrue(sharedStore.isDeviceRevoked())
        XCTAssertEqual(sharedStore.getShieldState(), .none)

        // Invariant: Builtin adaptive quiz question bank MUST be preserved
        let builtinQuestions = AdaptiveQuizEngine.loadBuiltinQuizBank()
        XCTAssertFalse(builtinQuestions.isEmpty, "Builtin quiz bank must never be deleted")
    }

    // MARK: - Device Revocation Guard Tests

    @MainActor
    func testDeviceRevocationGuard() {
        let sharedStore = ScreenTimeSharedStore(suiteName: "test_guard_\(UUID().uuidString)")
        let guardController = DeviceRevocationGuard(sharedStore: sharedStore)

        // Initially not revoked
        sharedStore.setDeviceRevoked(false)
        XCTAssertFalse(guardController.isRevoked)
        XCTAssertNoThrow(try guardController.assertNotRevoked())

        // Revoke device
        sharedStore.setDeviceRevoked(true)
        XCTAssertTrue(guardController.isRevoked)
        XCTAssertThrowsError(try guardController.assertNotRevoked()) { error in
            guard let appError = error as? AppError else {
                XCTFail("Expected AppError")
                return
            }
            if case .auth(let msg) = appError {
                XCTAssertTrue(msg.contains("revoked"))
            } else {
                XCTFail("Expected .auth error")
            }
        }
    }

    // MARK: - App Check Manager Tests

    func testAppCheckManagerConfiguration() {
        let manager = AppCheckManager.shared
        manager.configure(enforce: false)

        let status = manager.status
        XCTAssertTrue(status.configured)
        XCTAssertFalse(status.enforced)
    }
}
