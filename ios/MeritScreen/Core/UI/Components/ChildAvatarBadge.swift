import SwiftUI

/// Standardized child avatar display with emoji, circular styling, and optional active connection dot.
public struct ChildAvatarBadge: View {
    public let avatar: AvatarPreset
    public let size: CGFloat
    public let isOnline: Bool?

    public init(avatar: AvatarPreset, size: CGFloat = 44, isOnline: Bool? = nil) {
        self.avatar = avatar
        self.size = size
        self.isOnline = isOnline
    }

    public var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Circle()
                .fill(MeritColor.secondaryFill)
                .frame(width: size, height: size)
                .overlay(
                    Text(avatar.emoji)
                        .font(.system(size: size * 0.55))
                )

            if let isOnline = isOnline {
                Circle()
                    .fill(isOnline ? MeritColor.success : MeritColor.tertiaryLabel)
                    .frame(width: size * 0.28, height: size * 0.28)
                    .overlay(
                        Circle()
                            .stroke(MeritColor.systemBackground, lineWidth: 2)
                    )
                    .offset(x: 2, y: 2)
            }
        }
    }
}
