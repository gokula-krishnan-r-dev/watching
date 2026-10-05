import Foundation

/// Target age group for a child profile. Controls difficulty and question format.
/// Mirrors `com.meritscreen.core.common.domain.AgeBand`.
public enum AgeBand: String, Codable, Sendable, CaseIterable {
    case band_3_6 = "3_6"
    case band_7_9 = "7_9"
    case band_10_12 = "10_12"

    public var displayLabel: String {
        switch self {
        case .band_3_6: return "Ages 3–6"
        case .band_7_9: return "Ages 7–9"
        case .band_10_12: return "Ages 10–12"
        }
    }

    public var subtitle: String {
        switch self {
        case .band_3_6: return "Picture choices, big touch targets, early concepts"
        case .band_7_9: return "Foundational reading, numbers, pattern recognition"
        case .band_10_12: return "Logic, comprehension, multi-step thinking"
        }
    }

    /// Matches Android `AgeBand.name` written to Firestore (`AGE_7_TO_9`, …).
    public var firestoreName: String {
        switch self {
        case .band_3_6: return "AGE_3_TO_6"
        case .band_7_9: return "AGE_7_TO_9"
        case .band_10_12: return "AGE_10_TO_12"
        }
    }

    public static func fromStorage(_ raw: String?) -> AgeBand {
        guard let raw = raw?.uppercased() else { return .band_7_9 }
        switch raw {
        case "AGE_3_TO_6", "3_6", "BAND_3_6": return .band_3_6
        case "AGE_10_TO_12", "10_12", "BAND_10_12": return .band_10_12
        case "AGE_7_TO_9", "7_9", "BAND_7_9": return .band_7_9
        default:
            if raw.contains("10") || raw.contains("12") { return .band_10_12 }
            if raw.contains("3") || raw.contains("6") { return .band_3_6 }
            return .band_7_9
        }
    }

    public static let age4To6: AgeBand = .band_3_6
    public static let age7To9: AgeBand = .band_7_9
    public static let age10To12: AgeBand = .band_10_12
}
