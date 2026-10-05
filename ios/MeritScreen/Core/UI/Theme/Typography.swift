import SwiftUI

/// Semantic Typography definitions adhering to Apple's Dynamic Type scale.
public enum MeritTypography {
    public static let largeTitle: Font = .largeTitle.weight(.bold)
    public static let title: Font = .title.weight(.semibold)
    public static let title1: Font = .title.weight(.semibold)
    public static let title2: Font = .title2.weight(.semibold)
    public static let title3: Font = .title3.weight(.medium)
    public static let headline: Font = .headline
    public static let subheadline: Font = .subheadline
    public static let body: Font = .body
    public static let callout: Font = .callout
    public static let footnote: Font = .footnote
    public static let caption: Font = .caption
    public static let caption1: Font = .caption
    public static let caption2: Font = .caption2

    // Child-specific larger readable styles
    public static let childQuestion: Font = .system(size: 24, weight: .bold, design: .rounded)
    public static let childChoice: Font = .system(size: 20, weight: .semibold, design: .rounded)
    public static let childTimer: Font = .system(size: 40, weight: .bold, design: .rounded)
}
