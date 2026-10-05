import SwiftUI
import Observation

@Observable
@MainActor
public final class AccountViewModel {
    public var email: String = "parent@meritscreen.internal"
    public var displayName: String = "Parent"
    public var familyName: String = "The Family"
    public var children: [FamilyChildProfile] = []
    public var isLoading: Bool = false
    public var error: String? = nil

    // Change PIN State
    public var newPin: String = ""
    public var confirmPin: String = ""
    public var isSavingPin: Bool = false
    public var pinSaved: Bool = false
    public var pinError: String? = nil

    // Delete Family State
    public var deleteConfirmationText: String = ""
    public var isDeletingFamily: Bool = false
    public var familyDeleted: Bool = false
    public var deleteError: String? = nil

    private let parentSessionRepo: ParentSessionRepository
    private let parentControlStore: ParentControlStoreProtocol
    private let familyStore: FamilyStoreProtocol
    private let pinHasher: Pbkdf2PinHasher

    public init(
        parentSessionRepo: ParentSessionRepository = .shared,
        parentControlStore: ParentControlStoreProtocol = FirestoreParentControlStore.shared,
        familyStore: FamilyStoreProtocol = FirestoreFamilyStore.shared,
        pinHasher: Pbkdf2PinHasher = Pbkdf2PinHasher()
    ) {
        self.parentSessionRepo = parentSessionRepo
        self.parentControlStore = parentControlStore
        self.familyStore = familyStore
        self.pinHasher = pinHasher
        load()
    }

    public func load() {
        isLoading = true
        error = nil
        Task {
            let session = parentSessionRepo.current()
            let familyId = session?.familyId ?? "sample_family"
            do {
                let meta = try await parentControlStore.getFamilyMeta(familyId: familyId)
                let c = try await parentControlStore.listChildren(familyId: familyId)
                self.familyName = meta?.name.isEmpty == false ? meta!.name : "The Family"
                self.children = c
            } catch {
                self.error = error.localizedDescription
            }
            isLoading = false
        }
    }

    public func saveNewPin() {
        pinError = nil
        pinSaved = false

        guard newPin.count == AppConfig.parentPinMaxLength, newPin.allSatisfy({ $0.isNumber }) else {
            pinError = "PIN must be \(AppConfig.parentPinMaxLength) digits."
            return
        }

        guard newPin == confirmPin else {
            pinError = "PINs do not match."
            return
        }

        isSavingPin = true
        let familyId = parentSessionRepo.current()?.familyId ?? "sample_family"

        Task {
            do {
                let hash = pinHasher.hash(pin: newPin)
                try await familyStore.updateParentPinHash(familyId: familyId, pinHash: hash)
                newPin = ""
                confirmPin = ""
                pinSaved = true
            } catch {
                pinError = "Could not update PIN."
            }
            isSavingPin = false
        }
    }

    public func addChild(name: String, ageBand: AgeBand, avatar: AvatarPreset) async -> Bool {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            error = "Enter a name for the child."
            return false
        }

        let familyId = parentSessionRepo.current()?.familyId ?? "sample_family"
        do {
            let draft = FamilyDraftChild(
                localId: UUID().uuidString.lowercased(),
                name: trimmed,
                ageBand: ageBand,
                avatar: avatar
            )
            let newProfile = try await parentControlStore.addChild(familyId: familyId, child: draft)
            children.append(newProfile)
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }

    public func deleteFamily() {
        deleteError = nil
        guard deleteConfirmationText.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() == "DELETE" else {
            deleteError = "Type DELETE to confirm."
            return
        }

        let familyId = parentSessionRepo.current()?.familyId ?? "sample_family"
        isDeletingFamily = true

        Task {
            do {
                try await parentControlStore.deleteFamily(familyId: familyId)
                parentSessionRepo.clear()
                familyDeleted = true
            } catch {
                deleteError = "Failed to delete family. Please try again."
            }
            isDeletingFamily = false
        }
    }
}
