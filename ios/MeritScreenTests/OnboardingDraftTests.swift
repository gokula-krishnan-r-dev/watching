import XCTest
@testable import MeritScreen

final class OnboardingDraftTests: XCTestCase {

    var testDefaults: UserDefaults!
    var repository: OnboardingDraftRepository!

    override func setUp() {
        super.setUp()
        testDefaults = UserDefaults(suiteName: "test_onboarding_\(UUID().uuidString)")!
        repository = OnboardingDraftRepository(defaults: testDefaults)
    }

    override func tearDown() {
        testDefaults.removePersistentDomain(forName: testDefaults.description)
        super.tearDown()
    }

    // MARK: - Draft Defaults
    func testOnboardingDraftDefaults() {
        let draft = repository.getDraft()
        XCTAssertFalse(draft.consentGiven)
        XCTAssertEqual(draft.familyName, "")
        XCTAssertTrue(draft.children.isEmpty)
        XCTAssertNil(draft.parentPinHash)
        XCTAssertFalse(draft.isReadyToCommit)
        XCTAssertTrue(draft.canAddChild)
    }

    // MARK: - Draft Persistence Across Process Kills
    func testDraftPersistenceAcrossInstances() throws {
        // Set consent & family name
        repository.setConsentGiven(true)
        repository.setFamilyName("The Explorers")

        // Add a child
        let child = ChildDraft(name: "Leo", ageBand: .band_7_9, avatar: .rabbit)
        try repository.addChild(child)

        // Set PIN
        try repository.setParentPin(pin: "4321")

        // Create new repository instance to simulate app relaunch
        let relaunchedRepo = OnboardingDraftRepository(defaults: testDefaults)
        let loadedDraft = relaunchedRepo.getDraft()

        XCTAssertTrue(loadedDraft.consentGiven)
        XCTAssertEqual(loadedDraft.familyName, "The Explorers")
        XCTAssertEqual(loadedDraft.children.count, 1)
        XCTAssertEqual(loadedDraft.children.first?.name, "Leo")
        XCTAssertEqual(loadedDraft.children.first?.ageBand, .band_7_9)
        XCTAssertEqual(loadedDraft.children.first?.avatar, .rabbit)
        XCTAssertNotNil(loadedDraft.parentPinHash)
        XCTAssertTrue(loadedDraft.isReadyToCommit)

        // Verify the stored hash matches the PIN
        let hash = loadedDraft.parentPinHash!
        XCTAssertTrue(Pbkdf2PinHasher.shared.verify(pin: "4321", storedHash: hash))
        XCTAssertFalse(Pbkdf2PinHasher.shared.verify(pin: "1234", storedHash: hash))
    }

    // MARK: - Child Validation Tests
    func testAddChildBlankNameThrows() {
        XCTAssertThrowsError(try repository.addChild(ChildDraft(name: ""))) { error in
            guard case AppError.validation(let msg) = error else {
                return XCTFail("Expected validation error")
            }
            XCTAssertTrue(msg.contains("name"))
        }

        XCTAssertThrowsError(try repository.addChild(ChildDraft(name: "   "))) { error in
            guard case AppError.validation(let msg) = error else {
                return XCTFail("Expected validation error")
            }
            XCTAssertTrue(msg.contains("name"))
        }
    }

    func testChildCapExceededThrows() throws {
        // Add maximum allowed children (5)
        for i in 1...AppConfig.maxChildrenPerParent {
            try repository.addChild(ChildDraft(name: "Child \(i)"))
        }

        let draft = repository.getDraft()
        XCTAssertEqual(draft.children.count, AppConfig.maxChildrenPerParent)
        XCTAssertFalse(draft.canAddChild)

        // Attempting to add 6th child must fail
        XCTAssertThrowsError(try repository.addChild(ChildDraft(name: "Child 6"))) { error in
            guard case AppError.validation(let msg) = error else {
                return XCTFail("Expected validation error")
            }
            XCTAssertTrue(msg.contains("up to \(AppConfig.maxChildrenPerParent) children"))
        }
    }

    func testRemoveChild() throws {
        let child1 = ChildDraft(name: "Alice")
        let child2 = ChildDraft(name: "Bob")
        try repository.addChild(child1)
        try repository.addChild(child2)

        XCTAssertEqual(repository.getDraft().children.count, 2)

        repository.removeChild(id: child1.id)
        let updated = repository.getDraft()
        XCTAssertEqual(updated.children.count, 1)
        XCTAssertEqual(updated.children.first?.name, "Bob")
    }

    // MARK: - PIN Validation Tests
    func testParentPinValidation() {
        // Less than 4 digits
        XCTAssertThrowsError(try repository.setParentPin(pin: "123")) { error in
            guard case AppError.validation = error else { return XCTFail() }
        }

        // More than 4 digits
        XCTAssertThrowsError(try repository.setParentPin(pin: "12345")) { error in
            guard case AppError.validation = error else { return XCTFail() }
        }

        // Non-numeric
        XCTAssertThrowsError(try repository.setParentPin(pin: "12ab")) { error in
            guard case AppError.validation = error else { return XCTFail() }
        }

        // Valid 4-digit PIN
        XCTAssertNoThrow(try repository.setParentPin(pin: "9876"))
        let hash = repository.getDraft().parentPinHash
        XCTAssertNotNil(hash)
        XCTAssertTrue(hash!.hasPrefix("pbkdf2$120000$"))
    }

    // MARK: - isReadyToCommit Gate Tests
    func testIsReadyToCommitGate() throws {
        var draft = OnboardingDraft()
        XCTAssertFalse(draft.isReadyToCommit)

        draft.consentGiven = true
        XCTAssertFalse(draft.isReadyToCommit) // No children yet

        draft.children.append(ChildDraft(name: "Maya"))
        XCTAssertFalse(draft.isReadyToCommit) // No PIN yet

        draft.parentPinHash = Pbkdf2PinHasher.shared.hash(pin: "1111")
        XCTAssertTrue(draft.isReadyToCommit) // Consent, child, and PIN exist!
    }

    // MARK: - Schedule & Boundaries Tests
    func testSchedulePersistenceAndDefaults() {
        let draft = repository.getDraft()
        XCTAssertEqual(draft.dailyBudgetMinutes, 90)
        XCTAssertEqual(draft.quizFrequencyMinutes, 30)
        XCTAssertTrue(draft.bedtimeEnabled)
        XCTAssertEqual(draft.bedtimeStart, "20:30")
        XCTAssertEqual(draft.bedtimeEnd, "07:00")
        XCTAssertEqual(draft.cooldownMinutes, 10)

        repository.setSchedule(
            dailyBudgetMinutes: 120,
            quizFrequencyMinutes: 45,
            bedtimeEnabled: false,
            bedtimeStart: "21:00",
            bedtimeEnd: "06:30",
            cooldownMinutes: 15
        )

        let updated = repository.getDraft()
        XCTAssertEqual(updated.dailyBudgetMinutes, 120)
        XCTAssertEqual(updated.quizFrequencyMinutes, 45)
        XCTAssertFalse(updated.bedtimeEnabled)
        XCTAssertEqual(updated.bedtimeStart, "21:00")
        XCTAssertEqual(updated.bedtimeEnd, "06:30")
        XCTAssertEqual(updated.cooldownMinutes, 15)
    }

    // MARK: - App Allowlist Rules Tests
    func testAppRulesPersistenceAndToggle() {
        let draft = repository.getDraft()
        XCTAssertFalse(draft.allowedApps.isEmpty)
        XCTAssertTrue(draft.allowedApps.contains { $0.id == "com.apple.mobilephone" && $0.isSystemLocked })

        var rules = draft.allowedApps
        if let khanIdx = rules.firstIndex(where: { $0.name == "Khan Academy Kids" }) {
            rules[khanIdx].isAllowed = false
        }
        repository.setAppRules(rules)

        let updated = repository.getDraft()
        let updatedKhan = updated.allowedApps.first { $0.name == "Khan Academy Kids" }
        XCTAssertEqual(updatedKhan?.isAllowed, false)
    }

    // MARK: - AI Learning Context Tests
    func testAiLearningContextPersistence() {
        let draft = repository.getDraft()
        XCTAssertEqual(draft.aiLearningPrompt, "")

        repository.setAiLearningContext(
            prompt: "Maya loves space exploration and needs practice with fractions.",
            topics: ["math_foundations", "science_exploration"]
        )

        let updated = repository.getDraft()
        XCTAssertEqual(updated.aiLearningPrompt, "Maya loves space exploration and needs practice with fractions.")
        XCTAssertEqual(updated.curriculumFocusIds, ["math_foundations", "science_exploration"])
    }

    // MARK: - Device Pairing Tests
    func testDevicePairingPersistence() {
        let draft = repository.getDraft()
        XCTAssertFalse(draft.isDevicePaired)

        repository.setPairedDevice(token: "PAIR_123456", isPaired: true)

        let updated = repository.getDraft()
        XCTAssertTrue(updated.isDevicePaired)
        XCTAssertEqual(updated.pairedDeviceToken, "PAIR_123456")
    }

    // MARK: - OnboardingAppRule Model Codable Tests
    func testOnboardingAppRuleEncodingDecoding() throws {
        let rule = OnboardingAppRule(
            id: "test.app.id",
            name: "Test App",
            category: .educational,
            iconName: "book.fill",
            isAllowed: true,
            isSystemLocked: false
        )

        let data = try JSONEncoder().encode(rule)
        let decoded = try JSONDecoder().decode(OnboardingAppRule.self, from: data)

        XCTAssertEqual(rule, decoded)
        XCTAssertEqual(decoded.category.iconName, "book.closed.fill")
    }
}
