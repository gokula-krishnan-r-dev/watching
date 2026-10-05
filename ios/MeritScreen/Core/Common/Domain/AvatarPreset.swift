import Foundation

/// Safe preset avatars for child profiles.
/// Avoids requiring child photos or camera permissions.
/// Mirrors `com.meritscreen.core.common.domain.AvatarPreset`.
public enum AvatarPreset: String, Codable, Sendable, CaseIterable {
    case rabbit = "RABBIT"
    case bear = "BEAR"
    case fox = "FOX"
    case owl = "OWL"
    case turtle = "TURTLE"
    case lion = "LION"
    case koala = "KOALA"
    case dolphin = "DOLPHIN"
    case tiger = "TIGER"
    case panda = "PANDA"
    case astro = "ASTRO"

    public var emoji: String {
        switch self {
        case .rabbit: return "🐰"
        case .bear: return "🐻"
        case .fox: return "🦊"
        case .owl: return "🦉"
        case .turtle: return "🐢"
        case .lion: return "🦁"
        case .koala: return "🐨"
        case .dolphin: return "🐬"
        case .tiger: return "🐯"
        case .panda: return "🐼"
        case .astro: return "🚀"
        }
    }

    public var label: String {
        switch self {
        case .rabbit: return "Rabbit"
        case .bear: return "Bear"
        case .fox: return "Fox"
        case .owl: return "Owl"
        case .turtle: return "Turtle"
        case .lion: return "Lion"
        case .koala: return "Koala"
        case .dolphin: return "Dolphin"
        case .tiger: return "Tiger"
        case .panda: return "Panda"
        case .astro: return "Astro"
        }
    }

    public var displayName: String {
        label
    }

    public var id: String {
        rawValue
    }

    public static let `default`: AvatarPreset = .rabbit
}

extension AvatarPreset: Identifiable {}
