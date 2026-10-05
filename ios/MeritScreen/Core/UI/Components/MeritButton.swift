import SwiftUI

public enum MeritButtonStyle {
    case primary
    case secondary
    case destructive
    case outline
    case text
}

/// Standardized interactive button with loading state, automatic disablement, and debouncing.
public struct MeritButton: View {
    public let title: String
    public let icon: String?
    public let style: MeritButtonStyle
    public let isLoading: Bool
    public let isEnabled: Bool
    public let action: () -> Void

    public init(
        _ title: String,
        icon: String? = nil,
        style: MeritButtonStyle = .primary,
        isLoading: Bool = false,
        isEnabled: Bool = true,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.icon = icon
        self.style = style
        self.isLoading = isLoading
        self.isEnabled = isEnabled
        self.action = action
    }

    public var body: some View {
        Button(action: {
            guard !isLoading && isEnabled else { return }
            action()
        }) {
            HStack(spacing: MeritSpacing.xSmall) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: foregroundColor))
                        .scaleEffect(0.9)
                } else if let icon = icon {
                    Image(systemName: icon)
                        .font(MeritTypography.headline)
                }

                Text(title)
                    .font(MeritTypography.headline)
            }
            .frame(maxWidth: style == .text ? nil : .infinity)
            .padding(.vertical, verticalPadding)
            .padding(.horizontal, horizontalPadding)
            .background(backgroundView)
            .foregroundColor(foregroundColor)
            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
            .overlay(borderOverlay)
            .contentShape(Rectangle())
        }
        .disabled(!isEnabled || isLoading)
        .opacity((!isEnabled || isLoading) ? 0.6 : 1.0)
        .animation(.easeInOut(duration: 0.15), value: isLoading)
    }

    private var verticalPadding: CGFloat {
        switch style {
        case .primary, .secondary, .destructive, .outline:
            return MeritSpacing.medium
        case .text:
            return MeritSpacing.xSmall
        }
    }

    private var horizontalPadding: CGFloat {
        switch style {
        case .primary, .secondary, .destructive, .outline:
            return MeritSpacing.large
        case .text:
            return MeritSpacing.xSmall
        }
    }

    private var foregroundColor: Color {
        switch style {
        case .primary:
            return Color.white
        case .secondary:
            return MeritColor.label
        case .destructive:
            return Color.white
        case .outline:
            return MeritColor.accent
        case .text:
            return MeritColor.accent
        }
    }

    @ViewBuilder
    private var backgroundView: some View {
        switch style {
        case .primary:
            MeritColor.accent
        case .secondary:
            MeritColor.secondaryFill
        case .destructive:
            MeritColor.destructive
        case .outline:
            Color.clear
        case .text:
            Color.clear
        }
    }

    @ViewBuilder
    private var borderOverlay: some View {
        if style == .outline {
            RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous)
                .stroke(MeritColor.accent, lineWidth: 1.5)
        }
    }
}
