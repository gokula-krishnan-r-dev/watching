import SwiftUI

/// Semantic Apple color tokens for MeritScreen.
/// All feature screens MUST use these semantic tokens instead of hard-coded hex colors.
public enum MeritColor {
    // Canvas & Surface Backgrounds
    public static var background: Color {
        Color(uiColor: .systemBackground)
    }

    public static var secondaryBackground: Color {
        Color(uiColor: .secondarySystemBackground)
    }

    public static var groupedBackground: Color {
        Color(uiColor: .systemGroupedBackground)
    }

    public static var secondaryGroupedBackground: Color {
        Color(uiColor: .secondarySystemGroupedBackground)
    }

    public static var cardBackground: Color {
        Color(uiColor: .secondarySystemGroupedBackground)
    }

    // Content Labels
    public static var label: Color {
        Color(uiColor: .label)
    }

    public static var secondaryLabel: Color {
        Color(uiColor: .secondaryLabel)
    }

    public static var tertiaryLabel: Color {
        Color(uiColor: .tertiaryLabel)
    }

    // Brand & Semantic Feedback
    public static var accent: Color {
        Color.accentColor
    }

    public static var success: Color {
        Color(uiColor: .systemGreen)
    }

    public static var pass: Color {
        success
    }

    public static var destructive: Color {
        Color(uiColor: .systemRed)
    }

    public static var calmRest: Color {
        Color(uiColor: .systemTeal)
    }

    public static var separator: Color {
        Color(uiColor: .separator)
    }

    public static var fill: Color {
        Color(uiColor: .systemFill)
    }

    public static var secondaryFill: Color {
        Color(uiColor: .secondarySystemFill)
    }

    public static var tertiaryFill: Color {
        Color(uiColor: .tertiarySystemFill)
    }

    public static var warning: Color {
        Color(uiColor: .systemOrange)
    }

    public static var systemBackground: Color {
        background
    }
}
