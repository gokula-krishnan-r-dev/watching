import XCTest
@testable import MeritScreen

@MainActor
final class ParentAddChildTests: XCTestCase {
    var inMemoryStore: InMemoryParentControlStore!
    var sessionRepo: ParentSessionRepository!
    var viewModel: ParentAddChildViewModel!

    override func setUp() async throws {
        try await super.setUp()
        inMemoryStore = InMemoryParentControlStore()
        sessionRepo = ParentSessionRepository.shared
        sessionRepo.set(ParentSession(
            uid: "test_parent_id",
            familyId: "test_family_id",
            childIds: []
        ))

        viewModel = ParentAddChildViewModel(
            parentSessionRepo: sessionRepo,
            parentControlStore: inMemoryStore
        )
    }

    override func tearDown() async throws {
        sessionRepo.clear()
        viewModel = nil
        inMemoryStore = nil
        try await super.tearDown()
    }

    func testInitialStateIsProfileStep() {
        XCTAssertEqual(viewModel.currentStep, .profile)
        XCTAssertFalse(viewModel.isSaving)
        XCTAssertNil(viewModel.errorMessage)
        XCTAssertNil(viewModel.createdProfile)
        XCTAssertFalse(viewModel.pairingCode.isEmpty)
        XCTAssertEqual(viewModel.pairingCode.count, 6)
        XCTAssertTrue(viewModel.pairingCode.allSatisfy { $0.isNumber }, "Pairing code must consist strictly of numeric digits")
    }

    func testCreateProfileValidationFailsOnEmptyName() async {
        let emptyDraft = ChildDraft(id: "c1", name: "   ", ageBand: .band_7_9, avatar: .fox)
        let success = await viewModel.createChildProfile(from: emptyDraft)

        XCTAssertFalse(success)
        XCTAssertEqual(viewModel.currentStep, .profile)
        XCTAssertNotNil(viewModel.errorMessage)
        XCTAssertTrue(viewModel.errorMessage?.contains("first name") == true)
    }

    func testCreateProfileSucceedsAndNavigatesToTimeline() async throws {
        let draft = ChildDraft(id: "c1", name: "Leo", ageBand: .band_10_12, avatar: .lion)
        let success = await viewModel.createChildProfile(from: draft)

        XCTAssertTrue(success)
        XCTAssertEqual(viewModel.currentStep, .timeline)
        XCTAssertEqual(viewModel.childName, "Leo")
        XCTAssertEqual(viewModel.ageBand, .band_10_12)
        XCTAssertEqual(viewModel.avatar, .lion)
        XCTAssertNotNil(viewModel.createdProfile)
        XCTAssertEqual(viewModel.createdProfile?.displayName, "Leo")
        XCTAssertNil(viewModel.errorMessage)

        // Verify child was added in store
        let children = try await inMemoryStore.listChildren(familyId: "test_family_id")
        XCTAssertEqual(children.count, 1)
        XCTAssertEqual(children.first?.displayName, "Leo")
    }

    func testSaveTimelineScheduleUpdatesPolicyAndNavigatesToPairing() async throws {
        let draft = ChildDraft(id: "c1", name: "Maya", ageBand: .band_7_9, avatar: .rabbit)
        _ = await viewModel.createChildProfile(from: draft)
        XCTAssertEqual(viewModel.currentStep, .timeline)

        let success = await viewModel.saveTimelineSchedule(
            budgetMinutes: 120,
            quizFreqMinutes: 20,
            bedtimeOn: true,
            start: "21:00",
            end: "06:30",
            cooldown: 15
        )

        XCTAssertTrue(success)
        XCTAssertEqual(viewModel.currentStep, .pairing)
        XCTAssertEqual(viewModel.dailyBudgetMinutes, 120)
        XCTAssertEqual(viewModel.quizFrequencyMinutes, 20)
        XCTAssertEqual(viewModel.bedtimeStart, "21:00")
        XCTAssertEqual(viewModel.bedtimeEnd, "06:30")
        XCTAssertEqual(viewModel.cooldownMinutes, 15)

        // Verify policy updated in store
        let childId = try XCTUnwrap(viewModel.createdProfile?.childId)
        let policy = try await inMemoryStore.getPolicy(familyId: "test_family_id", childId: childId)
        XCTAssertEqual(policy.dailyCeilingMinutes, 120)
        XCTAssertEqual(policy.defaultBlockMinutes, 20)
        XCTAssertEqual(policy.bedtimeEnabled, true)
        XCTAssertEqual(policy.bedtimeStartLabel, "21:00")
        XCTAssertEqual(policy.bedtimeEndLabel, "06:30")
        XCTAssertEqual(policy.defaultCooldownMinutes, 15)
    }

    func testDevicePairingProgression() async {
        let draft = ChildDraft(id: "c1", name: "Maya", ageBand: .band_7_9, avatar: .rabbit)
        _ = await viewModel.createChildProfile(from: draft)
        _ = await viewModel.saveTimelineSchedule(budgetMinutes: 60, quizFreqMinutes: 15, bedtimeOn: true, start: "20:30", end: "07:00", cooldown: 10)

        XCTAssertEqual(viewModel.currentStep, .pairing)
        XCTAssertFalse(viewModel.isDevicePaired)

        viewModel.confirmPairing(simulated: true)
        XCTAssertEqual(viewModel.currentStep, .allowlist)
        XCTAssertTrue(viewModel.isDevicePaired)
    }

    func testSaveAllowlistUpdatesRulesAndNavigatesToAiContext() async throws {
        let draft = ChildDraft(id: "c1", name: "Maya", ageBand: .band_7_9, avatar: .rabbit)
        _ = await viewModel.createChildProfile(from: draft)
        _ = await viewModel.saveTimelineSchedule(budgetMinutes: 60, quizFreqMinutes: 15, bedtimeOn: true, start: "20:30", end: "07:00", cooldown: 10)
        viewModel.skipPairing()

        XCTAssertEqual(viewModel.currentStep, .allowlist)

        var rules = OnboardingAppRule.defaultRules
        rules.append(OnboardingAppRule(id: "com.test.chess", name: "Kids Chess", category: .educational, iconName: "puzzlepiece.fill", isAllowed: true))

        let success = await viewModel.saveAllowlist(rules: rules)
        XCTAssertTrue(success)
        XCTAssertEqual(viewModel.currentStep, .aiContext)

        // Verify rules in store
        let childId = try XCTUnwrap(viewModel.createdProfile?.childId)
        let savedRules = try await inMemoryStore.listAppRules(familyId: "test_family_id", childId: childId)
        XCTAssertFalse(savedRules.isEmpty)
        XCTAssertTrue(savedRules.contains { $0.displayName == "Kids Chess" && $0.allowed })
    }

    func testSaveAiContextUpdatesPolicyAndNavigatesToComplete() async throws {
        let draft = ChildDraft(id: "c1", name: "Maya", ageBand: .band_7_9, avatar: .rabbit)
        _ = await viewModel.createChildProfile(from: draft)
        _ = await viewModel.saveTimelineSchedule(budgetMinutes: 60, quizFreqMinutes: 15, bedtimeOn: true, start: "20:30", end: "07:00", cooldown: 10)
        viewModel.skipPairing()
        _ = await viewModel.saveAllowlist(rules: OnboardingAppRule.defaultRules)

        XCTAssertEqual(viewModel.currentStep, .aiContext)

        let prompt = "Enjoys geometry puzzles and animal trivia"
        let success = await viewModel.saveAiLearningContext(prompt: prompt)
        XCTAssertTrue(success)
        XCTAssertEqual(viewModel.currentStep, .complete)
        XCTAssertEqual(viewModel.aiPrompt, prompt)

        // Verify policy custom prompt in store
        let childId = try XCTUnwrap(viewModel.createdProfile?.childId)
        let policy = try await inMemoryStore.getPolicy(familyId: "test_family_id", childId: childId)
        XCTAssertEqual(policy.customPromptGuidelines, prompt)
        XCTAssertEqual(policy.gradeStandard, "Grade 3")
        XCTAssertTrue(policy.aiQuizzesEnabled)
    }

    func testNavigateBackStepsThroughFlowProperly() async {
        let draft = ChildDraft(id: "c1", name: "Maya", ageBand: .band_7_9, avatar: .rabbit)
        _ = await viewModel.createChildProfile(from: draft)
        XCTAssertEqual(viewModel.currentStep, .timeline)

        viewModel.navigateBack()
        XCTAssertEqual(viewModel.currentStep, .profile)

        // Navigating back at profile remains at profile
        viewModel.navigateBack()
        XCTAssertEqual(viewModel.currentStep, .profile)
    }
}
