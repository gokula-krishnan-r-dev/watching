import SwiftUI

/// Device form-factor utilities.
@MainActor
public enum DeviceFamily {
    public static var isPad: Bool {
        UIDevice.current.userInterfaceIdiom == .pad
    }

    public static var isPhone: Bool {
        UIDevice.current.userInterfaceIdiom == .phone
    }
}

public struct ResponsiveContainerModifier: ViewModifier {
    public var maxWidth: CGFloat

    public init(maxWidth: CGFloat = 680) {
        self.maxWidth = maxWidth
    }

    public func body(content: Content) -> some View {
        HStack {
            Spacer(minLength: 0)
            content
                .frame(maxWidth: DeviceFamily.isPad ? maxWidth : .infinity)
            Spacer(minLength: 0)
        }
    }
}

public extension View {
    func responsiveContainer(maxWidth: CGFloat = 680) -> some View {
        modifier(ResponsiveContainerModifier(maxWidth: maxWidth))
    }
}
