import Foundation

public enum OnboardingAppCategory: String, Codable, Sendable, CaseIterable {
    case educational = "Educational"
    case entertainment = "Entertainment"
    case systemEssential = "System Essential"

    public var iconName: String {
        switch self {
        case .educational: return "book.closed.fill"
        case .entertainment: return "gamecontroller.fill"
        case .systemEssential: return "phone.fill"
        }
    }
}

public struct OnboardingAppRule: Identifiable, Codable, Sendable, Equatable {
    public let id: String
    public var name: String
    public var category: OnboardingAppCategory
    public var iconName: String
    public var isAllowed: Bool
    public var isSystemLocked: Bool

    public init(
        id: String,
        name: String,
        category: OnboardingAppCategory,
        iconName: String,
        isAllowed: Bool,
        isSystemLocked: Bool = false
    ) {
        self.id = id
        self.name = name
        self.category = category
        self.iconName = iconName
        self.isAllowed = isAllowed
        self.isSystemLocked = isSystemLocked
    }

    public static var defaultRules: [OnboardingAppRule] {
        [
            OnboardingAppRule(
                id: "com.apple.mobilephone",
                name: "Phone & Emergency",
                category: .systemEssential,
                iconName: "phone.fill",
                isAllowed: true,
                isSystemLocked: true
            ),
            OnboardingAppRule(
                id: "com.apple.MobileSMS",
                name: "Messages (Parents Only)",
                category: .systemEssential,
                iconName: "message.fill",
                isAllowed: true,
                isSystemLocked: true
            ),
            OnboardingAppRule(
                id: "org.khanacademy.KhanAcademyKids",
                name: "Khan Academy Kids",
                category: .educational,
                iconName: "graduationcap.fill",
                isAllowed: true
            ),
            OnboardingAppRule(
                id: "com.duolingo.DuolingoMobile",
                name: "Duolingo ABC",
                category: .educational,
                iconName: "character.book.closed.fill",
                isAllowed: true
            ),
            OnboardingAppRule(
                id: "com.apple.calculator",
                name: "Calculator",
                category: .educational,
                iconName: "plus.forwardslash.minus",
                isAllowed: true
            ),
            OnboardingAppRule(
                id: "com.google.android.youtube.kids",
                name: "YouTube Kids",
                category: .entertainment,
                iconName: "play.rectangle.fill",
                isAllowed: false
            ),
            OnboardingAppRule(
                id: "com.roblox.client",
                name: "Roblox",
                category: .entertainment,
                iconName: "cube.fill",
                isAllowed: false
            ),
            OnboardingAppRule(
                id: "com.mojang.minecraftpe",
                name: "Minecraft",
                category: .entertainment,
                iconName: "hammer.fill",
                isAllowed: false
            )
        ]
    }
}
