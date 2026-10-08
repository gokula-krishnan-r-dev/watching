import SwiftUI

/// S00: Fast brand splash presentation.
/// Loads immediately with zero network or database wait.
public struct S00_SplashView: View {
    public let onFinished: () -> Void

    @State private var opacity: Double = 0.0

    public init(onFinished: @escaping () -> Void) {
        self.onFinished = onFinished
    }

    public var body: some View {
        ZStack {
            MeritColor.background
                .ignoresSafeArea()

            VStack(spacing: MeritSpacing.large) {
                Image("WatchingSplashLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: 240, maxHeight: 120)

                Text("Watching")
                    .font(MeritTypography.title)
                    .foregroundColor(MeritColor.label)
            }
            .opacity(opacity)
        }
        .onAppear {
            withAnimation(.easeIn(duration: 0.4)) {
                opacity = 1.0
            }
            // Auto transition after a brief calm brand pause
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                onFinished()
            }
        }
    }
}

/// S01: Role Select & Primary Welcome screen.
/// Mirrors `com.meritscreen.app.rolegate.RoleSelectScreen` in Android.
public struct S01_RoleSelectView: View {
    public let onSelectParent: () -> Void
    public let onSelectChild: () -> Void
    public let onSignIn: () -> Void

    public init(
        onSelectParent: @escaping () -> Void,
        onSelectChild: @escaping () -> Void,
        onSignIn: @escaping () -> Void = {}
    ) {
        self.onSelectParent = onSelectParent
        self.onSelectChild = onSelectChild
        self.onSignIn = onSignIn
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.xLarge) {
                // Top Bar Mode Indicator Pill (matches Android Stitch UI)
                HStack {
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.shield.fill")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(MeritColor.accent)
                        Text("Parent Guardian Portal")
                            .font(MeritTypography.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(MeritColor.accent)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(MeritColor.accent.opacity(0.12))
                    .clipShape(Capsule())

                    Spacer()
                }
                .padding(.top, MeritSpacing.small)

                // Header & Brand Hero
                VStack(spacing: MeritSpacing.medium) {
                    ZStack {
                        Circle()
                            .fill(MeritColor.accent.opacity(0.12))
                            .frame(width: 96, height: 96)

                        Image("WatchingLogo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 76, height: 76)
                            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
                            .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 4)
                    }

                    Text("Screen time that\nactually works")
                        .font(MeritTypography.largeTitle)
                        .foregroundColor(MeritColor.label)
                        .multilineTextAlignment(.center)

                    Text("Healthy boundaries, calm rewards, and bite-sized learning moments.")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, MeritSpacing.medium)
                }

                // Value Propositions
                VStack(spacing: MeritSpacing.medium) {
                    valueProp(
                        icon: "hourglass.badge.plus",
                        title: "Bite-Sized Blocks",
                        description: "Apps pause after 15 minutes with gentle learning checkpoints."
                    )
                    valueProp(
                        icon: "brain.head.profile",
                        title: "Adaptive Quizzes",
                        description: "Fun, educational questions that adapt to your child's age."
                    )
                    valueProp(
                        icon: "shield.lefthalf.filled",
                        title: "Peace of Mind",
                        description: "No ads, no tracking, and complete parental control."
                    )
                }
                .padding(.vertical, MeritSpacing.xxSmall)

                Spacer(minLength: MeritSpacing.medium)

                // Action CTAs
                VStack(spacing: MeritSpacing.medium) {
                    MeritButton(
                        "Get Started as Parent",
                        icon: "person.fill.checkmark",
                        style: .primary,
                        action: onSelectParent
                    )

                    MeritButton(
                        "Set Up Child Device",
                        icon: "iphone.and.arrow.forward",
                        style: .secondary,
                        action: onSelectChild
                    )

                    Button(action: onSignIn) {
                        Text("Already have a family? Sign In")
                            .font(MeritTypography.subheadline)
                            .foregroundColor(MeritColor.accent)
                    }
                    .padding(.top, MeritSpacing.xxxSmall)
                }
                .padding(.bottom, MeritSpacing.large)
            }
            .padding(.horizontal, MeritSpacing.large)
            .responsiveContainer(maxWidth: 540)
        }
        .background(MeritColor.background.ignoresSafeArea())
    }

    private func valueProp(icon: String, title: String, description: String) -> some View {
        HStack(alignment: .top, spacing: MeritSpacing.medium) {
            ZStack {
                RoundedRectangle(cornerRadius: MeritSpacing.radiusSmall, style: .continuous)
                    .fill(MeritColor.accent.opacity(0.12))
                    .frame(width: 44, height: 44)

                Image(systemName: icon)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(MeritColor.accent)
            }

            VStack(alignment: .leading, spacing: MeritSpacing.xxxSmall) {
                Text(title)
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)

                Text(description)
                    .font(MeritTypography.subheadline)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.secondaryBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous)
                .stroke(MeritColor.separator.opacity(0.35), lineWidth: 1)
        )
    }
}
