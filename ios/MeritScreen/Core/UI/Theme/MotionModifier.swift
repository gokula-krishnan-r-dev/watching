import SwiftUI

/// Accessibility & motion utilities adhering to Apple Human Interface Guidelines.
/// Respects user's system "Reduce Motion" accessibility preference.
public enum MeritMotion {

    /// Standard interactive transition animation (spring or subtle ease), disabled when Reduce Motion is on.
    public static func interactive(reduceMotion: Bool) -> Animation? {
        if reduceMotion {
            return nil
        }
        return .spring(response: 0.35, dampingFraction: 0.8)
    }

    /// Subtle fade animation for state transitions, replaced with instant cut when Reduce Motion is on.
    public static func gentle(reduceMotion: Bool) -> Animation? {
        if reduceMotion {
            return nil
        }
        return .easeInOut(duration: 0.2)
    }
}

public struct MeritMotionModifier<V: Equatable>: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let animation: Animation?
    let value: V

    public func body(content: Content) -> some View {
        content
            .animation(reduceMotion ? nil : animation, value: value)
    }
}

public extension View {
    /// Applies animation only when system Reduce Motion is NOT enabled.
    func meritAnimation<V: Equatable>(_ animation: Animation?, value: V) -> some View {
        modifier(MeritMotionModifier(animation: animation, value: value))
    }
}
