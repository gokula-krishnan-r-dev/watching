import Foundation

/// A single child profile collected during parent onboarding before an account or Firestore ID exists.
/// Mirrors `com.meritscreen.feature.onboarding.domain.ChildDraft`.
public struct ChildDraft: Codable, Sendable, Equatable, Identifiable {
    public let id: String
    public var name: String
    public var ageBand: AgeBand
    public var avatar: AvatarPreset
    public var language: String
    public var localId: String { id }

    public init(
        id: String = UUID().uuidString,
        name: String,
        ageBand: AgeBand = .band_7_9,
        avatar: AvatarPreset = .rabbit,
        language: String = "en"
    ) {
        self.id = id
        self.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        self.ageBand = ageBand
        self.avatar = avatar
        self.language = language
    }
}
