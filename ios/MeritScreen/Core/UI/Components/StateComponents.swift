import SwiftUI

/// Standardized loading view with activity indicator and optional status message.
public struct LoadingView: View {
    public let message: String?

    public init(_ message: String? = nil) {
        self.message = message
    }

    public var body: some View {
        VStack(spacing: MeritSpacing.medium) {
            ProgressView()
                .scaleEffect(1.2)
                .progressViewStyle(CircularProgressViewStyle(tint: MeritColor.accent))

            if let message = message {
                Text(message)
                    .font(MeritTypography.subheadline)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(MeritSpacing.large)
    }
}

/// Standardized error view presenting user-facing error message with optional retry action.
public struct ErrorView: View {
    public let error: AppError
    public let onRetry: (() -> Void)?

    public init(error: AppError, onRetry: (() -> Void)? = nil) {
        self.error = error
        self.onRetry = onRetry
    }

    public var body: some View {
        VStack(spacing: MeritSpacing.medium) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 44))
                .foregroundColor(MeritColor.destructive)

            Text("Something Went Wrong")
                .font(MeritTypography.title3)
                .foregroundColor(MeritColor.label)

            Text(error.userMessage)
                .font(MeritTypography.body)
                .foregroundColor(MeritColor.secondaryLabel)
                .multilineTextAlignment(.center)
                .padding(.horizontal, MeritSpacing.medium)

            if let onRetry = onRetry, error.isRetryable {
                MeritButton("Try Again", icon: "arrow.clockwise", style: .outline, action: onRetry)
                    .padding(.top, MeritSpacing.small)
                    .frame(maxWidth: 240)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(MeritSpacing.large)
    }
}

/// Compact error banner card suitable for embedding directly inside forms and lists.
public struct StateErrorCard: View {
    public let message: String
    public let onRetry: (() -> Void)?

    public init(message: String, onRetry: (() -> Void)? = nil) {
        self.message = message
        self.onRetry = onRetry
    }

    public var body: some View {
        HStack(alignment: .top, spacing: MeritSpacing.small) {
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundColor(MeritColor.destructive)
                .font(.system(size: 20))

            VStack(alignment: .leading, spacing: MeritSpacing.xxxSmall) {
                Text(message)
                    .font(MeritTypography.subheadline)
                    .foregroundColor(MeritColor.label)

                if let onRetry = onRetry {
                    Button("Try again", action: onRetry)
                        .font(MeritTypography.footnote)
                        .foregroundColor(MeritColor.accent)
                        .padding(.top, MeritSpacing.xxxSmall)
                }
            }
            Spacer()
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.destructive.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
    }
}

/// Compact loading indicator view for forms and in-flight operations.
public struct StateLoadingView: View {
    public let message: String

    public init(_ message: String = "Loading...") {
        self.message = message
    }

    public var body: some View {
        HStack(spacing: MeritSpacing.medium) {
            ProgressView()
                .tint(MeritColor.accent)
            Text(message)
                .font(MeritTypography.subheadline)
                .foregroundColor(MeritColor.secondaryLabel)
        }
        .frame(maxWidth: .infinity)
        .padding(MeritSpacing.medium)
    }
}

/// Standardized empty state view.
public struct EmptyStateView: View {
    public let icon: String
    public let title: String
    public let description: String
    public let actionTitle: String?
    public let action: (() -> Void)?

    public init(
        icon: String = "tray",
        title: String,
        description: String,
        actionTitle: String? = nil,
        action: (() -> Void)? = nil
    ) {
        self.icon = icon
        self.title = title
        self.description = description
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        VStack(spacing: MeritSpacing.medium) {
            Image(systemName: icon)
                .font(.system(size: 48))
                .foregroundColor(MeritColor.secondaryLabel)

            Text(title)
                .font(MeritTypography.title3)
                .foregroundColor(MeritColor.label)

            Text(description)
                .font(MeritTypography.body)
                .foregroundColor(MeritColor.secondaryLabel)
                .multilineTextAlignment(.center)
                .padding(.horizontal, MeritSpacing.medium)

            if let actionTitle = actionTitle, let action = action {
                MeritButton(actionTitle, style: .primary, action: action)
                    .padding(.top, MeritSpacing.small)
                    .frame(maxWidth: 240)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(MeritSpacing.large)
    }
}
