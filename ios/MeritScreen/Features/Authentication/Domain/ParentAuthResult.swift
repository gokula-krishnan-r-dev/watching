import Foundation

/// Result of parent authentication and family reconciliation.
public struct ParentAuthResult: Sendable, Equatable {
    public let familyId: String?
    public let childIds: [String]
    public let isNewFamily: Bool
    public let needsOnboarding: Bool

    public init(
        familyId: String?,
        childIds: [String],
        isNewFamily: Bool,
        needsOnboarding: Bool
    ) {
        self.familyId = familyId
        self.childIds = childIds
        self.isNewFamily = isNewFamily
        self.needsOnboarding = needsOnboarding
    }
}
