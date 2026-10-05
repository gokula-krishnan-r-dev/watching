import SwiftUI
import Observation

@Observable
@MainActor
public final class PolicyEditorViewModel {
    public var policy: ChildPolicy = ChildPolicy()
    public var isLoading: Bool = false
    public var isSaving: Bool = false
    public var isSaved: Bool = false
    public var error: String? = nil

    private let childId: String
    private let parentSessionRepo: ParentSessionRepository
    private let parentControlStore: ParentControlStoreProtocol

    public init(
        childId: String,
        parentSessionRepo: ParentSessionRepository = .shared,
        parentControlStore: ParentControlStoreProtocol = FirestoreParentControlStore.shared
    ) {
        self.childId = childId
        self.parentSessionRepo = parentSessionRepo
        self.parentControlStore = parentControlStore
        load()
    }

    public func load() {
        isLoading = true
        error = nil
        Task {
            let familyId = parentSessionRepo.current()?.familyId ?? "sample_family"
            do {
                policy = try await parentControlStore.getPolicy(familyId: familyId, childId: childId)
            } catch {
                self.error = error.localizedDescription
            }
            isLoading = false
        }
    }

    public func update(_ transform: (inout ChildPolicy) -> Void) {
        transform(&policy)
        isSaved = false
        error = nil
    }

    public func save() {
        let familyId = parentSessionRepo.current()?.familyId ?? "sample_family"
        isSaving = true
        error = nil

        // Guidelines sanitization
        let guidelines = policy.customPromptGuidelines.trimmingCharacters(in: .whitespacesAndNewlines)
        if guidelines.count > AppConfig.customPromptGuidelinesMaxChars {
            error = "Custom guidance must be under \(AppConfig.customPromptGuidelinesMaxChars) characters."
            isSaving = false
            return
        }

        Task {
            do {
                try await parentControlStore.updatePolicy(familyId: familyId, childId: childId, policy: policy)
                isSaved = true
            } catch {
                self.error = error.localizedDescription
            }
            isSaving = false
        }
    }

    public func consumeSaved() -> Bool {
        if isSaved {
            isSaved = false
            return true
        }
        return false
    }
}
