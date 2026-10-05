import SwiftUI
import FamilyControls

/// Screen I01: Screen Time Authorization & Setup.
/// Explains why Apple Screen Time permission is needed and guides the parent/child
/// through the native iOS Authorization prompt.
public struct I01_ScreenTimeSetupView: View {
    public let onComplete: () -> Void

    @State private var authManager = ScreenTimeAuthorizationManager.shared

    public init(onComplete: @escaping () -> Void) {
        self.onComplete = onComplete
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: MeritSpacing.xLarge) {
                    // Header Visual
                    ZStack {
                        Circle()
                            .fill(MeritColor.accent.opacity(0.15))
                            .frame(width: 96, height: 96)

                        Image(systemName: "hourglass.badge.shield.half.filled")
                            .font(.system(size: 48))
                            .foregroundColor(MeritColor.accent)
                    }
                    .padding(.top, MeritSpacing.large)

                    // Title & Subtitle
                    VStack(spacing: MeritSpacing.small) {
                        Text("Screen Time Supervision")
                            .font(MeritTypography.title)
                            .fontWeight(.bold)
                            .foregroundColor(MeritColor.label)
                            .multilineTextAlignment(.center)

                        Text("Apple's native Screen Time API enables MeritScreen to pause apps when your block ends, and shield apps during fail-lock resting windows.")
                            .font(MeritTypography.body)
                            .foregroundColor(MeritColor.secondaryLabel)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, MeritSpacing.medium)
                    }

                    // Feature highlights
                    VStack(alignment: .leading, spacing: MeritSpacing.medium) {
                        featureRow(
                            icon: "timer",
                            title: "Protected Learning Blocks",
                            description: "When an app's timer ends, MeritScreen prompts a quick quiz to unlock more time."
                        )

                        featureRow(
                            icon: "moon.stars.fill",
                            title: "Calm Resting Windows",
                            description: "If a quiz is failed, non-emergency apps rest during cooldown. Take the retry quiz anytime."
                        )

                        featureRow(
                            icon: "phone.circle.fill",
                            title: "Always-Reachable Emergency Calls",
                            description: "Phone and family emergency contacts are never shielded."
                        )

                        featureRow(
                            icon: "lock.shield.fill",
                            title: "Zero Private Data",
                            description: "MeritScreen cannot see what you type, read, or watch inside other apps."
                        )
                    }
                    .padding(MeritSpacing.large)
                    .background(MeritColor.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))

                    // Status Pill
                    HStack(spacing: MeritSpacing.xSmall) {
                        Image(systemName: authManager.isAuthorized ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                            .foregroundColor(authManager.isAuthorized ? .green : .orange)
                        Text(statusLabel)
                            .font(MeritTypography.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(authManager.isAuthorized ? .green : .orange)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background((authManager.isAuthorized ? Color.green : Color.orange).opacity(0.12))
                    .clipShape(Capsule())

                    if let err = authManager.errorMessage {
                        Text(err)
                            .font(MeritTypography.caption)
                            .foregroundColor(MeritColor.destructive)
                            .multilineTextAlignment(.center)
                    }

                    // Action Buttons
                    VStack(spacing: MeritSpacing.medium) {
                        if !authManager.isAuthorized {
                            MeritButton("Authorize Screen Time", style: .primary) {
                                Task {
                                    _ = await authManager.requestAuthorization(for: .individual)
                                }
                            }
                        }

                        MeritButton(
                            authManager.isAuthorized ? "Continue" : "Continue without Screen Time",
                            style: authManager.isAuthorized ? .primary : .secondary
                        ) {
                            onComplete()
                        }
                    }
                    .padding(.bottom, MeritSpacing.xLarge)
                }
                .padding(.horizontal, MeritSpacing.large)
                .responsiveContainer(maxWidth: 520)
            }
            .background(MeritColor.groupedBackground.ignoresSafeArea())
            .navigationTitle("Screen Time Setup")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") {
                        onComplete()
                    }
                }
            }
            .onAppear {
                authManager.refreshStatus()
            }
        }
    }

    private func featureRow(icon: String, title: String, description: String) -> some View {
        HStack(alignment: .top, spacing: MeritSpacing.medium) {
            Image(systemName: icon)
                .font(.system(size: 22))
                .foregroundColor(MeritColor.accent)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)

                Text(description)
                    .font(MeritTypography.caption)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .lineSpacing(2)
            }
        }
    }

    private var statusLabel: String {
        switch authManager.status {
        case .approved: return "Screen Time Supervision Active"
        case .denied: return "Permission Denied in iOS Settings"
        case .notDetermined: return "Requires Authorization"
        @unknown default: return "Not Configured"
        }
    }
}
