import SwiftUI

/// P05: Timeline & Schedule Configuration Screen.
/// Mirrors `com.meritscreen.feature.onboarding.ui.TimelineScheduleScreen` in Android.
public struct P05_TimelineScheduleView: View {
    public let childName: String
    public let avatar: AvatarPreset
    public let initialBudgetMinutes: Int
    public let initialQuizFreqMinutes: Int
    public let initialBedtimeEnabled: Bool
    public let initialBedtimeStart: String
    public let initialBedtimeEnd: String
    public let initialCooldownMinutes: Int
    public let onContinue: (
        _ budgetMinutes: Int,
        _ quizFreqMinutes: Int,
        _ bedtimeEnabled: Bool,
        _ bedtimeStart: String,
        _ bedtimeEnd: String,
        _ cooldownMinutes: Int
    ) -> Void
    public let onBack: () -> Void

    @State private var budgetMinutes: Int
    @State private var quizFreqMinutes: Int
    @State private var bedtimeEnabled: Bool
    @State private var bedtimeStart: Date
    @State private var bedtimeEnd: Date
    @State private var cooldownMinutes: Int

    public init(
        childName: String,
        avatar: AvatarPreset = .rabbit,
        initialBudgetMinutes: Int = 90,
        initialQuizFreqMinutes: Int = 30,
        initialBedtimeEnabled: Bool = true,
        initialBedtimeStart: String = "20:30",
        initialBedtimeEnd: String = "07:00",
        initialCooldownMinutes: Int = 10,
        onContinue: @escaping (
            _ budgetMinutes: Int,
            _ quizFreqMinutes: Int,
            _ bedtimeEnabled: Bool,
            _ bedtimeStart: String,
            _ bedtimeEnd: String,
            _ cooldownMinutes: Int
        ) -> Void,
        onBack: @escaping () -> Void
    ) {
        self.childName = childName
        self.avatar = avatar
        self.initialBudgetMinutes = initialBudgetMinutes
        self.initialQuizFreqMinutes = initialQuizFreqMinutes
        self.initialBedtimeEnabled = initialBedtimeEnabled
        self.initialBedtimeStart = initialBedtimeStart
        self.initialBedtimeEnd = initialBedtimeEnd
        self.initialCooldownMinutes = initialCooldownMinutes
        self.onContinue = onContinue
        self.onBack = onBack

        _budgetMinutes = State(initialValue: initialBudgetMinutes)
        _quizFreqMinutes = State(initialValue: initialQuizFreqMinutes)
        _bedtimeEnabled = State(initialValue: initialBedtimeEnabled)
        _cooldownMinutes = State(initialValue: initialCooldownMinutes)

        // Parse initial times
        let calendar = Calendar.current
        let today = Date()

        let startParts = initialBedtimeStart.split(separator: ":").compactMap { Int($0) }
        let endParts = initialBedtimeEnd.split(separator: ":").compactMap { Int($0) }

        var startComps = calendar.dateComponents([.year, .month, .day], from: today)
        startComps.hour = startParts.count > 0 ? startParts[0] : 20
        startComps.minute = startParts.count > 1 ? startParts[1] : 30
        _bedtimeStart = State(initialValue: calendar.date(from: startComps) ?? today)

        var endComps = calendar.dateComponents([.year, .month, .day], from: today)
        endComps.hour = endParts.count > 0 ? endParts[0] : 7
        endComps.minute = endParts.count > 1 ? endParts[1] : 0
        _bedtimeEnd = State(initialValue: calendar.date(from: endComps) ?? today)
    }

    public var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: MeritSpacing.large) {
                    // Top Bar
                    HStack {
                        Button(action: onBack) {
                            HStack(spacing: 4) {
                                Image(systemName: "chevron.left")
                                Text("Back")
                            }
                            .font(MeritTypography.subheadline)
                            .foregroundColor(MeritColor.accent)
                        }

                        Spacer()

                        HStack(spacing: 6) {
                            Text(avatar.emoji)
                                .font(.system(size: 14))
                            Text("Step 2 of 5 • For \(childName)")
                                .font(MeritTypography.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(MeritColor.accent)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(MeritColor.accent.opacity(0.12))
                        .clipShape(Capsule())
                    }
                    .padding(.top, MeritSpacing.small)

                    // Header
                    VStack(alignment: .leading, spacing: MeritSpacing.xSmall) {
                        Text("Timeline & Schedule")
                            .font(MeritTypography.largeTitle)
                            .fontWeight(.bold)
                            .foregroundColor(MeritColor.label)

                        Text("Calm, clear guardrails for balance between learning, free discovery, and sleep.")
                            .font(MeritTypography.body)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    // Section 1: 24h Day Breakdown Visualizer
                    dayBreakdownCard

                    // Section 2: Daily Screen Time Budget
                    dailyBudgetCard

                    // Section 3: Micro-Learning Pace / Quiz Frequency
                    quizFrequencyCard

                    // Section 4: Bedtime Curfew Card
                    bedtimeCurfewCard

                    // Section 5: Cooldown on Failed Questions
                    cooldownCard

                    Spacer(minLength: MeritSpacing.medium)
                }
                .padding(.horizontal, MeritSpacing.large)
                .responsiveContainer(maxWidth: 540)
            }

            // Bottom Sticky Action Button
            VStack(spacing: 0) {
                Divider()
                MeritButton(
                    "Continue to QR Pairing",
                    icon: "qrcode",
                    style: .primary
                ) {
                    let formatter = DateFormatter()
                    formatter.dateFormat = "HH:mm"
                    let startStr = formatter.string(from: bedtimeStart)
                    let endStr = formatter.string(from: bedtimeEnd)

                    onContinue(
                        budgetMinutes,
                        quizFreqMinutes,
                        bedtimeEnabled,
                        startStr,
                        endStr,
                        cooldownMinutes
                    )
                }
                .padding(.horizontal, MeritSpacing.large)
                .padding(.vertical, MeritSpacing.medium)
                .responsiveContainer(maxWidth: 540)
            }
            .background(MeritColor.background)
        }
        .background(MeritColor.background.ignoresSafeArea())
    }

    // MARK: - Section 1: 24h Visualizer
    private var dayBreakdownCard: some View {
        VStack(spacing: MeritSpacing.medium) {
            HStack {
                HStack(spacing: 8) {
                    Image(systemName: "sun.and.horizon.fill")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(MeritColor.accent)
                    Text("Daily 24-Hour Balance")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)
                }
                Spacer()
                Text("\(budgetMinutes)m Play Budget")
                    .font(MeritTypography.caption)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.accent)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(MeritColor.accent.opacity(0.12))
                    .clipShape(Capsule())
            }

            // Continuous Visual Bar
            GeometryReader { geo in
                HStack(spacing: 3) {
                    // Sleep (approx 10h = 42%)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(MeritColor.calmRest)
                        .frame(width: geo.size.width * 0.40)
                    // School / Offline active (approx 8.5h = 35%)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(MeritColor.secondaryFill)
                        .frame(width: geo.size.width * 0.35)
                    // MeritScreen Learning & Play budget (fraction of 24h)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(MeritColor.accent)
                        .frame(width: max(24, geo.size.width * CGFloat(budgetMinutes) / 1440.0 * 2.5))
                    // Family Wind-down rest
                    RoundedRectangle(cornerRadius: 4)
                        .fill(MeritColor.fill)
                        .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 14)

            // Legend labels
            HStack {
                HStack(spacing: 4) {
                    Circle().fill(MeritColor.calmRest).frame(width: 8, height: 8)
                    Text("Sleep Curfew")
                        .font(.system(size: 11))
                        .foregroundColor(MeritColor.secondaryLabel)
                }
                Spacer()
                HStack(spacing: 4) {
                    Circle().fill(MeritColor.secondaryFill).frame(width: 8, height: 8)
                    Text("School / Offline")
                        .font(.system(size: 11))
                        .foregroundColor(MeritColor.secondaryLabel)
                }
                Spacer()
                HStack(spacing: 4) {
                    Circle().fill(MeritColor.accent).frame(width: 8, height: 8)
                    Text("Screen Budget")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(MeritColor.accent)
                }
            }
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.secondaryBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous)
                .stroke(MeritColor.separator.opacity(0.3), lineWidth: 1)
        )
    }

    // MARK: - Section 2: Daily Budget
    private var dailyBudgetCard: some View {
        VStack(alignment: .leading, spacing: MeritSpacing.medium) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Daily Screen Time Limit")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)
                    Text("Total entertainment and game time per calendar day.")
                        .font(MeritTypography.footnote)
                        .foregroundColor(MeritColor.secondaryLabel)
                }
                Spacer()
                Text("\(budgetMinutes / 60)h \(budgetMinutes % 60)m")
                    .font(MeritTypography.title3)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.accent)
            }

            // Slider
            Slider(
                value: Binding(
                    get: { Double(budgetMinutes) },
                    set: { budgetMinutes = Int($0) }
                ),
                in: 30...240,
                step: 15
            )
            .tint(MeritColor.accent)

            // Preset Pills
            HStack(spacing: 8) {
                ForEach([45, 60, 90, 120], id: \.self) { preset in
                    Button(action: {
                        budgetMinutes = preset
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }) {
                        Text("\(preset) min\(preset == 90 ? " (Default)" : "")")
                            .font(MeritTypography.caption)
                            .fontWeight(budgetMinutes == preset ? .bold : .regular)
                            .foregroundColor(budgetMinutes == preset ? .white : MeritColor.label)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(budgetMinutes == preset ? MeritColor.accent : MeritColor.background)
                            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.secondaryBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
    }

    // MARK: - Section 3: Quiz Frequency
    private var quizFrequencyCard: some View {
        VStack(alignment: .leading, spacing: MeritSpacing.medium) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Micro-Learning Pace")
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)
                Text("Apps pause after this interval for a quick 60-second educational question.")
                    .font(MeritTypography.footnote)
                    .foregroundColor(MeritColor.secondaryLabel)
            }

            HStack(spacing: 8) {
                ForEach([15, 30, 45, 60], id: \.self) { interval in
                    Button(action: {
                        quizFreqMinutes = interval
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }) {
                        VStack(spacing: 2) {
                            Text("Every")
                                .font(.system(size: 10))
                                .foregroundColor(quizFreqMinutes == interval ? .white.opacity(0.8) : MeritColor.secondaryLabel)
                            Text("\(interval)m")
                                .font(MeritTypography.headline)
                                .fontWeight(.bold)
                                .foregroundColor(quizFreqMinutes == interval ? .white : MeritColor.label)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(quizFreqMinutes == interval ? MeritColor.accent : MeritColor.background)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.secondaryBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
    }

    // MARK: - Section 4: Bedtime Curfew
    private var bedtimeCurfewCard: some View {
        VStack(alignment: .leading, spacing: MeritSpacing.medium) {
            Toggle(isOn: $bedtimeEnabled) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Bedtime Blackout Curfew")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)
                    Text("Screen locks automatically to safeguard deep sleep.")
                        .font(MeritTypography.footnote)
                        .foregroundColor(MeritColor.secondaryLabel)
                }
            }
            .tint(MeritColor.calmRest)

            if bedtimeEnabled {
                Divider()
                HStack(spacing: MeritSpacing.large) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Bedtime (Wind Down)")
                            .font(MeritTypography.caption)
                            .foregroundColor(MeritColor.secondaryLabel)
                        DatePicker("", selection: $bedtimeStart, displayedComponents: .hourAndMinute)
                            .labelsHidden()
                    }

                    Spacer()

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Wake Up (Unlock)")
                            .font(MeritTypography.caption)
                            .foregroundColor(MeritColor.secondaryLabel)
                        DatePicker("", selection: $bedtimeEnd, displayedComponents: .hourAndMinute)
                            .labelsHidden()
                    }
                }
            }
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.secondaryBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
    }

    // MARK: - Section 5: Cooldown
    private var cooldownCard: some View {
        VStack(alignment: .leading, spacing: MeritSpacing.medium) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Cooldown on 3 Missed Answers")
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)
                Text("Brief pause if 3 consecutive answers are missed, preventing rapid guessing.")
                    .font(MeritTypography.footnote)
                    .foregroundColor(MeritColor.secondaryLabel)
            }

            HStack(spacing: 8) {
                ForEach([5, 10, 15], id: \.self) { mins in
                    Button(action: {
                        cooldownMinutes = mins
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }) {
                        Text("\(mins) min cooldown")
                            .font(MeritTypography.caption)
                            .fontWeight(cooldownMinutes == mins ? .bold : .regular)
                            .foregroundColor(cooldownMinutes == mins ? .white : MeritColor.label)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(cooldownMinutes == mins ? MeritColor.accent : MeritColor.background)
                            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.secondaryBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
    }
}
