import SwiftUI

/// P02: 3-Slider Value Tour Screen.
/// Mirrors `com.meritscreen.feature.onboarding.ui.ValueTourScreen` in Android.
public struct P02_ValueTourView: View {
    public let onContinue: () -> Void
    public let onSkip: () -> Void
    public let onBack: () -> Void

    @State private var currentPage = 0

    private struct TourSlide: Identifiable {
        let id: Int
        let stepLabel: String
        let title: String
        let subtitle: String
        let highlight: String
        let pills: [(icon: String, text: String)]
    }

    private let slides: [TourSlide] = [
        TourSlide(
            id: 0,
            stepLabel: "Step 1 of 3",
            title: "Learn first. Then play.",
            subtitle: "Kids answer short AI-powered questions on their phone. Pass the quiz, unlock screen time. Fail, and entertainment stays paused until they try again.",
            highlight: "Watching turns screen time into a daily trainer — adaptive quizzes build math, reading, and logic skills.",
            pills: [
                ("brain.head.profile", "AI training quizzes"),
                ("iphone", "Phone as classroom"),
                ("bolt.fill", "+15 min for correct answers")
            ]
        ),
        TourSlide(
            id: 1,
            stepLabel: "Step 2 of 3",
            title: "Only apps you approve",
            subtitle: "Watching acts as the child's safe space. Entertainment stays behind the quiz gate, while learning and emergency calls always remain accessible.",
            highlight: "Parents set the allowlist once, and the device enforces it offline with zero loopholes.",
            pills: [
                ("shield.fill", "Launcher lock"),
                ("clock.badge.checkmark.fill", "Remote pause"),
                ("checkmark.seal.fill", "Parent PIN bypass")
            ]
        ),
        TourSlide(
            id: 2,
            stepLabel: "Step 3 of 3",
            title: "Healthy habits, calm nights",
            subtitle: "Set daily limits, cooldowns after tricky questions, and bedtime schedules so screens wind down without arguments.",
            highlight: "Parents steer from their phone. Kids keep learning at their pace — curiosity first, then the apps they love.",
            pills: [
                ("moon.stars.fill", "Bedtime curfew"),
                ("graduationcap.fill", "Ages 2–17 calibrated"),
                ("slider.horizontal.3", "Your rules, their pace")
            ]
        )
    ]

    public init(
        onContinue: @escaping () -> Void,
        onSkip: @escaping () -> Void = {},
        onBack: @escaping () -> Void = {}
    ) {
        self.onContinue = onContinue
        self.onSkip = onSkip
        self.onBack = onBack
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Top Bar
            HStack {
                // Step pill
                HStack(spacing: 6) {
                    Image(systemName: "book.pages.fill")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(MeritColor.accent)
                    Text(slides[currentPage].stepLabel)
                        .font(MeritTypography.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(MeritColor.accent)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(MeritColor.accent.opacity(0.12))
                .clipShape(Capsule())

                Spacer()

                Button("Skip", action: onSkip)
                    .font(MeritTypography.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
            }
            .padding(.horizontal, MeritSpacing.large)
            .padding(.top, MeritSpacing.small)
            .padding(.bottom, MeritSpacing.medium)

            // Carousel Tabs
            TabView(selection: $currentPage) {
                ForEach(slides) { slide in
                    slideContent(slide)
                        .tag(slide.id)
                        .padding(.horizontal, MeritSpacing.large)
                }
            }
            .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))

            // Bottom Navigation Area
            VStack(spacing: MeritSpacing.medium) {
                // Animated Page Indicator Dots
                HStack(spacing: 8) {
                    ForEach(0..<slides.count, id: \.self) { index in
                        Capsule()
                            .fill(currentPage == index ? MeritColor.accent : MeritColor.fill)
                            .frame(width: currentPage == index ? 24 : 8, height: 8)
                            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: currentPage)
                    }
                }
                .padding(.top, MeritSpacing.small)

                // Continue CTA Button
                MeritButton(
                    currentPage < slides.count - 1 ? "Continue" : "Get Started",
                    icon: "arrow.right",
                    style: .primary
                ) {
                    if currentPage < slides.count - 1 {
                        withAnimation {
                            currentPage += 1
                        }
                    } else {
                        onContinue()
                    }
                }
            }
            .padding(.horizontal, MeritSpacing.large)
            .padding(.bottom, MeritSpacing.large)
        }
        .background(MeritColor.background.ignoresSafeArea())
        .responsiveContainer(maxWidth: 540)
    }

    // MARK: - Slide Content
    @ViewBuilder
    private func slideContent(_ slide: TourSlide) -> some View {
        VStack(spacing: MeritSpacing.medium) {
            // Mock preview card
            Group {
                switch slide.id {
                case 0:
                    quizPreviewCard
                case 1:
                    allowlistPreviewCard
                default:
                    bedtimePreviewCard
                }
            }
            .frame(maxWidth: .infinity)

            // Text section
            VStack(alignment: .leading, spacing: MeritSpacing.small) {
                Text(slide.title)
                    .font(MeritTypography.title)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.label)

                Text(slide.subtitle)
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .fixedSize(horizontal: false, vertical: true)

                Text(slide.highlight)
                    .font(MeritTypography.footnote)
                    .fontWeight(.medium)
                    .foregroundColor(MeritColor.accent)
                    .fixedSize(horizontal: false, vertical: true)

                // Feature Pills
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 8)], alignment: .leading, spacing: 8) {
                    ForEach(slide.pills, id: \.text) { pill in
                        HStack(spacing: 6) {
                            Image(systemName: pill.icon)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(MeritColor.accent)
                            Text(pill.text)
                                .font(MeritTypography.caption)
                                .fontWeight(.medium)
                                .foregroundColor(MeritColor.label)
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(MeritColor.secondaryBackground)
                        .clipShape(Capsule())
                    }
                }
                .padding(.top, 4)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Spacer(minLength: 0)
        }
    }

    // MARK: - Mock Preview 1: Quiz Card
    private var quizPreviewCard: some View {
        VStack(spacing: MeritSpacing.small) {
            // Header
            HStack {
                HStack(spacing: 8) {
                    ZStack {
                        Circle()
                            .fill(MeritColor.accent.opacity(0.15))
                            .frame(width: 32, height: 32)
                        Image(systemName: "plus.forwardslash.minus")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(MeritColor.accent)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text("AI QUIZ • GRADE 3 MATH")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(MeritColor.secondaryLabel)
                        Text("Daily Skill Unlock")
                            .font(MeritTypography.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(MeritColor.label)
                    }
                }

                Spacer()

                HStack(spacing: 4) {
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(MeritColor.accent)
                    Text("+15 min")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(MeritColor.accent)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(MeritColor.accent.opacity(0.12))
                .clipShape(Capsule())
            }

            // Question Box
            VStack(alignment: .leading, spacing: 4) {
                Text("Question 2 of 3")
                    .font(.system(size: 11))
                    .foregroundColor(MeritColor.secondaryLabel)
                Text("Maya shares 24 blueberries equally among 4 friends. How many does each get?")
                    .font(MeritTypography.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(MeritColor.label)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
            .background(MeritColor.background)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            // Options
            HStack(spacing: 8) {
                Text("4 berries")
                    .font(MeritTypography.caption)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(MeritColor.background)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                HStack(spacing: 4) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                    Text("6 berries")
                        .font(MeritTypography.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(MeritColor.accent)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.secondaryBackground)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(MeritColor.separator.opacity(0.3), lineWidth: 1)
        )
    }

    // MARK: - Mock Preview 2: Allowlist Card
    private var allowlistPreviewCard: some View {
        VStack(spacing: MeritSpacing.small) {
            HStack {
                HStack(spacing: 8) {
                    ZStack {
                        Circle()
                            .fill(MeritColor.accent.opacity(0.15))
                            .frame(width: 32, height: 32)
                        Image(systemName: "checkmark.shield.fill")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(MeritColor.accent)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text("APP ALLOWLIST RULES")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(MeritColor.secondaryLabel)
                        Text("Curated Child Environment")
                            .font(MeritTypography.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(MeritColor.label)
                    }
                }
                Spacer()
                Text("Enforced")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(MeritColor.accent)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(MeritColor.accent.opacity(0.12))
                    .clipShape(Capsule())
            }

            VStack(spacing: 6) {
                mockAppRow(icon: "graduationcap.fill", name: "Khan Academy Kids", status: "Allowed Always", color: MeritColor.accent)
                mockAppRow(icon: "character.book.closed.fill", name: "Duolingo ABC", status: "Allowed Always", color: MeritColor.accent)
                mockAppRow(icon: "gamecontroller.fill", name: "Roblox", status: "Quiz-Locked", color: MeritColor.warning)
            }
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.secondaryBackground)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(MeritColor.separator.opacity(0.3), lineWidth: 1)
        )
    }

    private func mockAppRow(icon: String, name: String, status: String, color: Color) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(color)
                .frame(width: 26, height: 26)
                .background(color.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))

            Text(name)
                .font(MeritTypography.caption)
                .fontWeight(.medium)
                .foregroundColor(MeritColor.label)

            Spacer()

            Text(status)
                .font(.system(size: 10, weight: .semibold))
                .foregroundColor(color)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(color.opacity(0.1))
                .clipShape(Capsule())
        }
        .padding(8)
        .background(MeritColor.background)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    // MARK: - Mock Preview 3: Bedtime Card
    private var bedtimePreviewCard: some View {
        VStack(spacing: MeritSpacing.small) {
            HStack {
                HStack(spacing: 8) {
                    ZStack {
                        Circle()
                            .fill(MeritColor.calmRest.opacity(0.15))
                            .frame(width: 32, height: 32)
                        Image(systemName: "moon.stars.fill")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(MeritColor.calmRest)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text("DAILY BALANCE & BEDTIME")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(MeritColor.secondaryLabel)
                        Text("Calm Nights Guarantee")
                            .font(MeritTypography.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(MeritColor.label)
                    }
                }
                Spacer()
                Text("8:30 PM – 7:00 AM")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(MeritColor.calmRest)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(MeritColor.calmRest.opacity(0.12))
                    .clipShape(Capsule())
            }

            // Visual Timeline Bar
            VStack(alignment: .leading, spacing: 4) {
                GeometryReader { geo in
                    HStack(spacing: 2) {
                        // School / Active (40%)
                        RoundedRectangle(cornerRadius: 4)
                            .fill(MeritColor.secondaryFill)
                            .frame(width: geo.size.width * 0.40)
                        // Play & Quizzes (25%)
                        RoundedRectangle(cornerRadius: 4)
                            .fill(MeritColor.accent)
                            .frame(width: geo.size.width * 0.25)
                        // Sleep Curfew (35%)
                        RoundedRectangle(cornerRadius: 4)
                            .fill(MeritColor.calmRest)
                            .frame(width: geo.size.width * 0.35)
                    }
                }
                .frame(height: 12)

                HStack {
                    Text("School / Day")
                        .font(.system(size: 9))
                        .foregroundColor(MeritColor.secondaryLabel)
                    Spacer()
                    Text("Play (90m)")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundColor(MeritColor.accent)
                    Spacer()
                    Text("Bedtime Lock")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundColor(MeritColor.calmRest)
                }
            }
            .padding(10)
            .background(MeritColor.background)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.secondaryBackground)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(MeritColor.separator.opacity(0.3), lineWidth: 1)
        )
    }
}
