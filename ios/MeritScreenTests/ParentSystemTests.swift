import Testing
import Foundation
@testable import MeritScreen

@Suite("Parent System Tests")
struct ParentSystemTests {

    @Test("ParentControlStore Child CRUD operations")
    func testParentControlStoreChildCRUD() async throws {
        let store = InMemoryParentControlStore()
        let familyId = "test_crud_family"

        let draft = FamilyDraftChild(
            localId: "child_test_1",
            name: "Lucas",
            ageBand: .age7To9,
            avatar: .fox,
            language: "en"
        )

        let created = try await store.addChild(familyId: familyId, child: draft)
        #expect(created.displayName == "Lucas")
        #expect(created.avatar == .fox)

        let children = try await store.listChildren(familyId: familyId)
        #expect(children.contains(where: { $0.childId == "child_test_1" }))

        let updatedDraft = FamilyDraftChild(
            localId: "child_test_1",
            name: "Lucas Updated",
            ageBand: .age10To12,
            avatar: .lion,
            language: "en"
        )
        let updated = try await store.updateChild(familyId: familyId, childId: "child_test_1", child: updatedDraft)
        #expect(updated.displayName == "Lucas Updated")
        #expect(updated.avatar == .lion)

        try await store.deleteChild(familyId: familyId, childId: "child_test_1")
        let afterDelete = try await store.listChildren(familyId: familyId)
        #expect(!afterDelete.contains(where: { $0.childId == "child_test_1" }))
    }

    @Test("ParentControlStore Policy Updates and Constraints")
    func testParentControlStorePolicyUpdates() async throws {
        let store = InMemoryParentControlStore()
        let familyId = "test_policy_family"
        let childId = "child_policy_1"

        var policy = try await store.getPolicy(familyId: familyId, childId: childId)
        #expect(policy.defaultBlockMinutes == AppConfig.defaultBlockMinutes)

        policy.dailyCeilingMinutes = 180
        policy.defaultBlockMinutes = 30
        policy.questionsPerQuiz = 5
        policy.passScorePercent = 80
        policy.customPromptGuidelines = "Focus on geometry proofs."

        try await store.updatePolicy(familyId: familyId, childId: childId, policy: policy)

        let reloaded = try await store.getPolicy(familyId: familyId, childId: childId)
        #expect(reloaded.dailyCeilingMinutes == 180)
        #expect(reloaded.defaultBlockMinutes == 30)
        #expect(reloaded.questionsPerQuiz == 5)
        #expect(reloaded.passScorePercent == 80)
        #expect(reloaded.customPromptGuidelines == "Focus on geometry proofs.")
    }

    @Test("Pause Resume and Bonus Time operations")
    func testPauseResumeAndBonusTime() async throws {
        let store = InMemoryParentControlStore()
        let familyId = "test_supervision_family"
        let childId = "child_sup_1"

        // Pause child
        try await store.setChildPaused(familyId: familyId, childId: childId, paused: true)
        var policy = try await store.getPolicy(familyId: familyId, childId: childId)
        #expect(policy.paused == true)

        // Resume child
        try await store.setChildPaused(familyId: familyId, childId: childId, paused: false)
        policy = try await store.getPolicy(familyId: familyId, childId: childId)
        #expect(policy.paused == false)

        // Add 15m bonus time
        let initialCeiling = policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
        try await store.addBonusTime(familyId: familyId, childId: childId, bonusMinutes: 15)
        let policyWithBonus = try await store.getPolicy(familyId: familyId, childId: childId)
        #expect(policyWithBonus.dailyCeilingMinutes == initialCeiling + 15)
        #expect(policyWithBonus.bonusMinutesToday == 15)
    }

    @Test("App Rules CRUD and Toggle")
    func testAppRulesCRUD() async throws {
        let store = InMemoryParentControlStore()
        let familyId = "test_rules_family"
        let childId = "child_rules_1"

        let rule1 = AppRule(
            appId: "rule_chess",
            packageOrBundleId: "com.chess.mobile",
            displayName: "Chess Kids",
            allowed: true,
            blockMinutes: 20
        )
        try await store.upsertAppRule(familyId: familyId, childId: childId, rule: rule1)

        let rules = try await store.listAppRules(familyId: familyId, childId: childId)
        #expect(rules.contains(where: { $0.packageOrBundleId == "com.chess.mobile" }))

        var updated = rule1
        updated.allowed = false
        try await store.upsertAppRule(familyId: familyId, childId: childId, rule: updated)

        let rulesAfterToggle = try await store.listAppRules(familyId: familyId, childId: childId)
        let found = rulesAfterToggle.first(where: { $0.appId == "rule_chess" })
        #expect(found?.allowed == false)

        try await store.deleteAppRule(familyId: familyId, childId: childId, appId: "rule_chess")
        let afterDelete = try await store.listAppRules(familyId: familyId, childId: childId)
        #expect(!afterDelete.contains(where: { $0.appId == "rule_chess" }))
    }

    @Test("DashboardViewModel Aggregation and Quick Actions")
    @MainActor
    func testDashboardViewModelAggregation() async throws {
        let store = InMemoryParentControlStore()
        let sessionRepo = ParentSessionRepository(userDefaults: UserDefaults(suiteName: "test_dashboard_suite") ?? .standard)
        sessionRepo.set(ParentSession(uid: "parent_test", familyId: "sample_family", childIds: ["child_leo"]))

        let vm = DashboardViewModel(parentSessionRepo: sessionRepo, parentControlStore: store)
        vm.load()

        // Wait a brief moment for async load
        try await Task.sleep(nanoseconds: 100_000_000)

        guard case .success(let data) = vm.uiState else {
            #expect(Bool(false), "Expected uiState to be success")
            return
        }

        #expect(!data.children.isEmpty)
        #expect(data.children.first?.profile.displayName == "Leo")
        #expect(data.analytics.totalAiMinutes > 0)
        #expect(!data.recentEvents.isEmpty)

        // Test toggle pause
        vm.togglePause(childId: "child_leo")
        if case .success(let updated) = vm.uiState {
            #expect(updated.children.first(where: { $0.profile.childId == "child_leo" })?.isPaused == true)
        }

        // Test grant bonus
        vm.grantBonus(childId: "child_leo", bonusMinutes: 15)
        if case .success(let withBonus) = vm.uiState {
            let child = withBonus.children.first(where: { $0.profile.childId == "child_leo" })
            #expect(child?.dailyCeilingMinutes == 135)
        }
    }

    @Test("ChildDetailViewModel Operations and Toast")
    @MainActor
    func testChildDetailViewModelOperations() async throws {
        let store = InMemoryParentControlStore()
        let sessionRepo = ParentSessionRepository(userDefaults: UserDefaults(suiteName: "test_detail_suite") ?? .standard)
        sessionRepo.set(ParentSession(uid: "parent_test", familyId: "sample_family", childIds: ["child_leo"]))

        let vm = ChildDetailViewModel(childId: "child_leo", parentSessionRepo: sessionRepo, parentControlStore: store)
        vm.load()

        try await Task.sleep(nanoseconds: 100_000_000)

        guard case .success(let detail) = vm.uiState else {
            #expect(Bool(false), "Expected child detail uiState to be success")
            return
        }

        #expect(detail.profile.displayName == "Leo")

        // Test grant bonus & toast message
        vm.grantBonus(minutes: 15)
        if case .success(let withBonus) = vm.uiState {
            #expect(withBonus.bonusToastMessage != nil)
        }

        vm.dismissBonusToast()
        if case .success(let dismissed) = vm.uiState {
            #expect(dismissed.bonusToastMessage == nil)
        }
    }

    @Test("PolicyEditorViewModel Validation and Guidelines")
    @MainActor
    func testPolicyEditorViewModelValidation() async throws {
        let store = InMemoryParentControlStore()
        let sessionRepo = ParentSessionRepository(userDefaults: UserDefaults(suiteName: "test_policy_vm_suite") ?? .standard)
        sessionRepo.set(ParentSession(uid: "parent_test", familyId: "sample_family", childIds: ["child_leo"]))

        let vm = PolicyEditorViewModel(childId: "child_leo", parentSessionRepo: sessionRepo, parentControlStore: store)
        try await Task.sleep(nanoseconds: 100_000_000)

        vm.update { policy in
            policy.dailyCeilingMinutes = 150
            policy.questionsPerQuiz = 4
        }
        #expect(vm.policy.dailyCeilingMinutes == 150)
        #expect(vm.policy.questionsPerQuiz == 4)

        // Exceed guidelines character limit
        let longPrompt = String(repeating: "A", count: 600)
        vm.update { $0.customPromptGuidelines = longPrompt }
        vm.save()
        #expect(vm.error != nil)
    }

    @Test("AllowlistViewModel Search Query and Rule Management")
    @MainActor
    func testAllowlistViewModel() async throws {
        let store = InMemoryParentControlStore()
        let sessionRepo = ParentSessionRepository(userDefaults: UserDefaults(suiteName: "test_allowlist_suite") ?? .standard)
        sessionRepo.set(ParentSession(uid: "parent_test", familyId: "sample_family", childIds: ["child_leo"]))

        let vm = AllowlistViewModel(childId: "child_leo", parentSessionRepo: sessionRepo, parentControlStore: store)
        try await Task.sleep(nanoseconds: 100_000_000)

        #expect(!vm.rules.isEmpty)

        // Test search query
        vm.searchQuery = "youtube"
        #expect(vm.filteredRules.count == 1)
        #expect(vm.filteredRules.first?.displayName == "YouTube Kids")

        vm.searchQuery = "nonexistent"
        #expect(vm.filteredRules.isEmpty)

        // Test add custom rule
        vm.addCustomApp(packageOrBundleId: "com.test.mathgame", displayName: "Math Fun")
        #expect(vm.rules.contains(where: { $0.packageOrBundleId == "com.test.mathgame" }))
    }

    @Test("AccountViewModel PIN Update and Family Deletion")
    @MainActor
    func testAccountViewModelPinAndDeletion() async throws {
        let store = InMemoryParentControlStore()
        let familyStore = InMemoryFamilyStore()
        let sessionRepo = ParentSessionRepository(userDefaults: UserDefaults(suiteName: "test_account_suite") ?? .standard)
        sessionRepo.set(ParentSession(uid: "parent_test", familyId: "sample_family", childIds: ["child_leo"]))

        let vm = AccountViewModel(
            parentSessionRepo: sessionRepo,
            parentControlStore: store,
            familyStore: familyStore
        )
        try await Task.sleep(nanoseconds: 100_000_000)

        #expect(!vm.children.isEmpty)

        // Test PIN validation: length mismatch
        vm.newPin = "123"
        vm.confirmPin = "123"
        vm.saveNewPin()
        #expect(vm.pinError != nil)

        // Test PIN validation: non-matching PINs
        vm.newPin = "1234"
        vm.confirmPin = "5678"
        vm.saveNewPin()
        #expect(vm.pinError == "PINs do not match.")

        // Test valid PIN update
        vm.newPin = "1234"
        vm.confirmPin = "1234"
        vm.saveNewPin()
        try await Task.sleep(nanoseconds: 50_000_000)
        #expect(vm.pinSaved == true)

        // Test delete family: bad confirmation text
        vm.deleteConfirmationText = "NO"
        vm.deleteFamily()
        #expect(vm.deleteError == "Type DELETE to confirm.")

        // Test delete family: valid confirmation text
        vm.deleteConfirmationText = "DELETE"
        vm.deleteFamily()
        try await Task.sleep(nanoseconds: 100_000_000)
        #expect(vm.familyDeleted == true)
        #expect(sessionRepo.current() == nil)
    }
}
