import SwiftUI

/// P06: Setup Complete Celebration & Handoff Screen.
/// Mirrors `com.meritscreen.feature.onboarding.ui.SetupCompleteScreen` in Android.
public struct P06_SetupCompleteView: View {
    public let childName: String
    public let grade: String
    public let avatar: AvatarPreset
    public let allowanceMinutes: Int
    public let allowlistCount: Int
    public let quizIntervalMinutes: Int
    public let cooldownMinutes: Int
    public let bedtimeRange: String
    public let onOpenDashboard: () -> Void
    public let autoRedirect: Bool

    @State private var countdownSeconds: Int = 5
    @State private var progress: CGFloat = 0.0

    public init(
        childName: String = "your child",
        grade: String = "Elementary",
        avatar: AvatarPreset = .rabbit,
        allowanceMinutes: Int = 90,
        allowlistCount: Int = 5,
        quizIntervalMinutes: Int = 30,
        cooldownMinutes: Int = 10,
        bedtimeRange: String = "8:30 PM – 7:00 AM",
        autoRedirect: Bool = true,
        onOpenDashboard: @escaping () -> Void
    ) {
        self.childName = childName
        self.grade = grade
        self.avatar = avatar
        self.allowanceMinutes = allowanceMinutes
        self.allowlistCount = allowlistCount
        self.quizIntervalMinutes = quizIntervalMinutes
        self.cooldownMinutes = cooldownMinutes
        self.bedtimeRange = bedtimeRange
        self.autoRedirect = autoRedirect
        self.onOpenDashboard = onOpenDashboard
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                Spacer(minLength: MeritSpacing.medium)

                // Glowing Check Badge
                ZStack {
                    Circle()
                        .fill(MeritColor.accent.opacity(0.15))
                        .frame(width: 88, height: 88)

                    Circle()
                        .fill(MeritColor.accent)
                        .frame(width: 64, height: 64)
                        .shadow(color: MeritColor.accent.opacity(0.3), radius: 12, x: 0, y: 6)

                    Image(systemName: "checkmark")
                        .font(.system(size: 30, weight: .bold))
                        .foregroundColor(.white)
                }
                .padding(.top, MeritSpacing.small)

                // Status Badge Pill
                HStack(spacing: 6) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(MeritColor.accent)
                    Text("Profile & Rules Ready!")
                        .font(MeritTypography.caption)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.accent)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(MeritColor.accent.opacity(0.12))
                .clipShape(Capsule())

                // Title & Subtitle
                VStack(spacing: MeritSpacing.xSmall) {
                    Text("\(childName)'s Guardian Shield is Active")
                        .font(MeritTypography.title)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.label)
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)

                    Text("All initial boundaries and micro-learning intervals are configured and saved to your family vault.")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, MeritSpacing.medium)
                }

                // Bento Summary Card
                VStack(spacing: MeritSpacing.medium) {
                    // Profile Row
                    HStack(spacing: 12) {
                        Text(avatar.emoji)
                            .font(.system(size: 36))
                            .frame(width: 52, height: 52)
                            .background(MeritColor.accent.opacity(0.12))
                            .clipShape(Circle())

                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(childName) (\(grade))")
                                .font(MeritTypography.headline)
                                .foregroundColor(MeritColor.label)

                            Text("Curiosity Quests & Focus active")
                                .font(MeritTypography.caption)
                                .foregroundColor(MeritColor.accent)
                        }

                        Spacer()

                        Image(systemName: "lock.shield.fill")
                            .font(.system(size: 18))
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                    .padding(MeritSpacing.medium)
                    .background(MeritColor.background)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))

                    Divider()

                    // Rule Rows
                    let hours = allowanceMinutes / 60
                    let mins = allowanceMinutes % 60
                    let allowanceStr = hours > 0 ? "\(hours)h \(mins)m daily limit" : "\(mins)m daily limit"

                    summaryRow(
                        icon: "hourglass",
                        title: "Daily Allowance",
                        subtitle: allowanceStr,
                        color: MeritColor.accent
                    )

                    summaryRow(
                        icon: "square.grid.2x2.fill",
                        title: "Allowlist",
                        subtitle: "\(allowlistCount) approved apps (educational safe list)",
                        color: MeritColor.accent
                    )

                    summaryRow(
                        icon: "brain.head.profile",
                        title: "Learning Gate",
                        subtitle: "Quiz every \(quizIntervalMinutes)m • \(cooldownMinutes)m fail-cooldown",
                        color: MeritColor.accent
                    )

                    summaryRow(
                        icon: "moon.stars.fill",
                        title: "Bedtime Curfew",
                        subtitle: "\(bedtimeRange) blackout",
                        color: MeritColor.calmRest
                    )
                }
                .padding(MeritSpacing.large)
                .background(MeritColor.secondaryBackground)
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .stroke(MeritColor.separator.opacity(0.3), lineWidth: 1)
                )

                // Auto redirect countdown bar
                if autoRedirect {
                    VStack(spacing: 6) {
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(MeritColor.fill)
                                    .frame(height: 6)

                                Capsule()
                                    .fill(MeritColor.accent)
                                    .frame(width: geo.size.width * progress, height: 6)
                                    .animation(.linear(duration: 1.0), value: progress)
                            }
                        }
                        .frame(height: 6)

                        Text("Redirecting to dashboard in \(countdownSeconds)s...")
                            .font(MeritTypography.caption)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                    .padding(.horizontal, MeritSpacing.small)
                }

                Spacer(minLength: MeritSpacing.medium)

                // Open Dashboard CTA
                MeritButton(
                    "Open Parent Dashboard",
                    icon: "arrow.right",
                    style: .primary,
                    action: onOpenDashboard
                )
                .padding(.bottom, MeritSpacing.large)
            }
            .padding(.horizontal, MeritSpacing.large)
            .responsiveContainer(maxWidth: 540)
        }
        .background(MeritColor.background.ignoresSafeArea())
        .onAppear {
            if autoRedirect {
                Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
                    if countdownSeconds > 1 {
                        countdownSeconds -= 1
                        progress = CGFloat(5 - countdownSeconds) / 5.0
                    } else {
                        timer.invalidate()
                        onOpenDashboard()
                    }
                }
            }
        }
    }

    private func summaryRow(icon: String, title: String, subtitle: String, color: Color) -> some View {
        HStack(spacing: MeritSpacing.medium) {
            ZStack {
                Circle()
                    .fill(color.opacity(0.12))
                    .frame(width: 36, height: 36)
                Image(systemName: icon)
                    .font(.system(size: 15))
                    .foregroundColor(color)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(MeritTypography.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(MeritColor.label)

                Text(subtitle)
                    .font(MeritTypography.footnote)
                    .foregroundColor(MeritColor.secondaryLabel)
            }

            Spacer()
        }
    }
}
