import SwiftUI

/// Screen C13: Daily Ceiling Awareness Card.
/// Notifies child when total daily screen time limit is reached.
public struct C13_DailyCeilingBanner: View {
    public let minutesUsedToday: Int
    public let dailyCeilingMinutes: Int
    public let onOpenPin: () -> Void

    public init(
        minutesUsedToday: Int,
        dailyCeilingMinutes: Int,
        onOpenPin: @escaping () -> Void
    ) {
        self.minutesUsedToday = minutesUsedToday
        self.dailyCeilingMinutes = dailyCeilingMinutes
        self.onOpenPin = onOpenPin
    }

    public var body: some View {
        VStack(spacing: MeritSpacing.medium) {
            HStack(spacing: MeritSpacing.small) {
                Image(systemName: "sun.max.fill")
                    .foregroundColor(Color.orange)
                Text("Daily Limit Reached")
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)
                Spacer()
                Text("\(minutesUsedToday)/\(dailyCeilingMinutes)m")
                    .font(MeritTypography.caption)
                    .fontWeight(.bold)
                    .foregroundColor(MeritColor.secondaryLabel)
            }

            Text("You have reached your daily ceiling of \(dailyCeilingMinutes) minutes. All applications are locked until tomorrow.")
                .font(MeritTypography.caption)
                .foregroundColor(MeritColor.secondaryLabel)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                onOpenPin()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "key.fill")
                        .font(.system(size: 11))
                    Text("Parent Bonus / PIN Override")
                        .font(MeritTypography.caption)
                        .fontWeight(.bold)
                }
                .foregroundColor(MeritColor.accent)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(MeritSpacing.large)
        .background(Color.orange.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
    }
}

// MARK: - Native App Icon Component

/// Renders authentic, high-definition branded iOS app icons for approved applications.
public struct AppIconView: View {
    public let rule: AppRule
    public let size: CGFloat

    public init(rule: AppRule, size: CGFloat = 46) {
        self.rule = rule
        self.size = size
    }

    public var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.22, style: .continuous)
                .fill(backgroundGradient)
                .frame(width: size, height: size)
                .shadow(color: Color.black.opacity(0.12), radius: 3, x: 0, y: 1.5)

            Image(systemName: iconName)
                .font(.system(size: size * 0.48, weight: .semibold))
                .foregroundColor(.white)
        }
    }

    private var normalizedId: String {
        (rule.packageOrBundleId + " " + rule.displayName).lowercased()
    }

    private var iconName: String {
        if rule.isEmergency || normalizedId.contains("phone") || normalizedId.contains("dialer") {
            return "phone.fill"
        } else if normalizedId.contains("message") || normalizedId.contains("sms") {
            return "message.fill"
        } else if normalizedId.contains("calculator") {
            return "plus.forwardslash.minus"
        } else if normalizedId.contains("duolingo") {
            return "character.book.closed.fill"
        } else if normalizedId.contains("khan") {
            return "graduationcap.fill"
        } else if normalizedId.contains("youtube") {
            return "play.rectangle.fill"
        } else if normalizedId.contains("safari") || normalizedId.contains("browser") {
            return "safari.fill"
        } else if normalizedId.contains("book") || normalizedId.contains("read") {
            return "book.fill"
        } else if normalizedId.contains("game") || normalizedId.contains("roblox") || normalizedId.contains("minecraft") {
            return "gamecontroller.fill"
        } else {
            return "app.fill"
        }
    }

    private var backgroundGradient: LinearGradient {
        if rule.isEmergency || normalizedId.contains("phone") {
            return LinearGradient(
                colors: [Color(red: 0.22, green: 0.85, blue: 0.32), Color(red: 0.15, green: 0.72, blue: 0.25)],
                startPoint: .top, endPoint: .bottom
            )
        } else if normalizedId.contains("message") {
            return LinearGradient(
                colors: [Color(red: 0.22, green: 0.85, blue: 0.32), Color(red: 0.15, green: 0.72, blue: 0.25)],
                startPoint: .top, endPoint: .bottom
            )
        } else if normalizedId.contains("calculator") {
            return LinearGradient(
                colors: [Color(red: 0.25, green: 0.25, blue: 0.27), Color(red: 0.12, green: 0.12, blue: 0.14)],
                startPoint: .top, endPoint: .bottom
            )
        } else if normalizedId.contains("duolingo") {
            return LinearGradient(
                colors: [Color(red: 0.36, green: 0.82, blue: 0.08), Color(red: 0.27, green: 0.68, blue: 0.02)],
                startPoint: .top, endPoint: .bottom
            )
        } else if normalizedId.contains("khan") {
            return LinearGradient(
                colors: [Color(red: 0.08, green: 0.75, blue: 0.58), Color(red: 0.04, green: 0.58, blue: 0.44)],
                startPoint: .top, endPoint: .bottom
            )
        } else if normalizedId.contains("youtube") {
            return LinearGradient(
                colors: [Color(red: 0.98, green: 0.18, blue: 0.18), Color(red: 0.80, green: 0.06, blue: 0.06)],
                startPoint: .top, endPoint: .bottom
            )
        } else if normalizedId.contains("safari") || normalizedId.contains("browser") {
            return LinearGradient(
                colors: [Color(red: 0.12, green: 0.54, blue: 0.98), Color(red: 0.05, green: 0.38, blue: 0.85)],
                startPoint: .top, endPoint: .bottom
            )
        } else if normalizedId.contains("book") || normalizedId.contains("read") {
            return LinearGradient(
                colors: [Color(red: 0.98, green: 0.56, blue: 0.12), Color(red: 0.88, green: 0.42, blue: 0.05)],
                startPoint: .top, endPoint: .bottom
            )
        } else if normalizedId.contains("game") || normalizedId.contains("roblox") || normalizedId.contains("minecraft") {
            return LinearGradient(
                colors: [Color(red: 0.58, green: 0.36, blue: 0.92), Color(red: 0.42, green: 0.22, blue: 0.78)],
                startPoint: .top, endPoint: .bottom
            )
        } else {
            return LinearGradient(
                colors: [MeritColor.accent, MeritColor.accent.opacity(0.8)],
                startPoint: .top, endPoint: .bottom
            )
        }
    }
}

// MARK: - Fullscreen Daily Limit Lock View

/// Screen C17: Full-screen Daily Limit Lockout.
/// Locks all device applications when the child reaches their daily screen time limit.
/// Matches iOS Human Interface Guidelines and BedtimeLock parity.
public struct C17_DailyLimitLockView: View {
    public let childName: String
    public let minutesUsedToday: Int
    public let dailyCeilingMinutes: Int
    public let onGrantBonus: (Int) -> Void
    public let onOpenParentPin: () -> Void
    public let onEmergencyCall: () -> Void

    @State private var showingPinSheet = ProcessInfo.processInfo.arguments.contains("-testDailyLimitPinSheet")
    @State private var showingBonusSelector = ProcessInfo.processInfo.arguments.contains("-testDailyLimitBonusSheet")
    @State private var selectedBonusMinutes: Int = 15

    public init(
        childName: String,
        minutesUsedToday: Int,
        dailyCeilingMinutes: Int,
        onGrantBonus: @escaping (Int) -> Void = { _ in },
        onOpenParentPin: @escaping () -> Void = {},
        onEmergencyCall: @escaping () -> Void = {}
    ) {
        self.childName = childName
        self.minutesUsedToday = minutesUsedToday
        self.dailyCeilingMinutes = dailyCeilingMinutes
        self.onGrantBonus = onGrantBonus
        self.onOpenParentPin = onOpenParentPin
        self.onEmergencyCall = onEmergencyCall
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                // Top Status Pill
                HStack {
                    HStack(spacing: 6) {
                        Image(systemName: "sun.max.fill")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.orange)
                        Text("DAILY LIMIT REACHED")
                            .font(MeritTypography.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.orange)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(Color.orange.opacity(0.15))
                    .clipShape(Capsule())

                    Spacer()

                    Text("Device Locked")
                        .font(MeritTypography.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(MeritColor.secondaryFill)
                        .clipShape(Capsule())
                }
                .padding(.top, MeritSpacing.large)

                // Hero Mascot Badge
                ZStack {
                    Circle()
                        .fill(
                            RadialGradient(
                                colors: [Color.orange.opacity(0.25), Color.orange.opacity(0.05)],
                                center: .center,
                                startRadius: 20,
                                endRadius: 70
                            )
                        )
                        .frame(width: 140, height: 140)

                    Circle()
                        .stroke(Color.orange.opacity(0.3), lineWidth: 2)
                        .frame(width: 120, height: 120)

                    Text("🌅")
                        .font(.system(size: 60))
                }
                .padding(.vertical, MeritSpacing.medium)

                // Headline & Supportive Copy
                VStack(spacing: MeritSpacing.small) {
                    Text("Great Job Today, \(childName)!")
                        .font(MeritTypography.largeTitle)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.label)
                        .multilineTextAlignment(.center)

                    Text("You've reached your daily screen time ceiling of \(dailyCeilingMinutes) minutes. All apps are now locked so you can rest, play, and disconnect.")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, MeritSpacing.medium)
                }

                // Daily Usage Summary Card
                VStack(spacing: MeritSpacing.medium) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("TOTAL SCREEN TIME")
                                .font(MeritTypography.caption)
                                .fontWeight(.bold)
                                .foregroundColor(MeritColor.secondaryLabel)
                            Text("\(minutesUsedToday) of \(dailyCeilingMinutes) min used")
                                .font(MeritTypography.headline)
                                .foregroundColor(MeritColor.label)
                        }

                        Spacer()

                        Image(systemName: "lock.shield.fill")
                            .font(.system(size: 28))
                            .foregroundColor(.orange)
                    }

                    ProgressView(value: 1.0, total: 1.0)
                        .tint(.orange)

                    HStack(spacing: 6) {
                        Image(systemName: "clock.arrow.circlepath")
                            .font(.system(size: 13))
                            .foregroundColor(MeritColor.secondaryLabel)
                        Text("Screen time resets tomorrow morning at 6:00 AM")
                            .font(MeritTypography.caption)
                            .foregroundColor(MeritColor.secondaryLabel)
                        Spacer()
                    }
                }
                .padding(MeritSpacing.large)
                .background(MeritColor.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous)
                        .stroke(Color.orange.opacity(0.2), lineWidth: 1)
                )

                // Action Buttons
                VStack(spacing: MeritSpacing.medium) {
                    MeritButton(
                        "Parent Bonus / PIN Override",
                        icon: "key.fill",
                        style: .primary
                    ) {
                        showingPinSheet = true
                    }

                    Button(action: onEmergencyCall) {
                        HStack(spacing: 8) {
                            Image(systemName: "phone.fill")
                                .foregroundColor(.green)
                            Text("Call Parents / Emergency")
                                .font(MeritTypography.subheadline)
                                .fontWeight(.semibold)
                                .foregroundColor(MeritColor.label)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(MeritColor.secondaryBackground)
                        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                    }
                }
                .padding(.top, MeritSpacing.small)
                .padding(.bottom, MeritSpacing.xLarge)
            }
            .padding(.horizontal, MeritSpacing.large)
            .responsiveContainer(maxWidth: 540)
        }
        .background(MeritColor.groupedBackground.ignoresSafeArea())
        .sheet(isPresented: $showingPinSheet) {
            C14_ParentPinSheet(
                onSuccess: {
                    showingPinSheet = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        showingBonusSelector = true
                    }
                },
                onDismiss: {
                    showingPinSheet = false
                }
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showingBonusSelector) {
            bonusSelectorSheet
                .presentationDetents([.medium, .large])
        }
    }

    private var bonusSelectorSheet: some View {
        NavigationStack {
            VStack(spacing: MeritSpacing.large) {
                VStack(spacing: MeritSpacing.small) {
                    Text("🎁")
                        .font(.system(size: 44))
                    Text("Parent Screen Time Bonus")
                        .font(MeritTypography.title2)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.label)
                    Text("Select additional screen time to grant \(childName) for today.")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                }
                .padding(.top, MeritSpacing.medium)

                VStack(spacing: MeritSpacing.small) {
                    bonusOptionRow(minutes: 15, title: "+15 Minutes", subtitle: "Quick screen time extension", icon: "bolt.fill", badge: "RECOMMENDED")
                    bonusOptionRow(minutes: 30, title: "+30 Minutes", subtitle: "Half-hour extension", icon: "clock.badge.checkmark.fill")
                    bonusOptionRow(minutes: 60, title: "+1 Hour", subtitle: "Extended study & play", icon: "star.fill")
                    bonusOptionRow(minutes: 720, title: "Unlock Rest of Today", subtitle: "Disable lockout until tomorrow 6:00 AM", icon: "lock.open.fill")
                }

                Spacer()

                MeritButton("Grant Extra Time & Unlock", icon: "checkmark.circle.fill", style: .primary) {
                    let mins = selectedBonusMinutes
                    showingBonusSelector = false
                    onGrantBonus(mins)
                    onOpenParentPin()
                }
                .padding(.bottom, MeritSpacing.medium)
            }
            .padding(.horizontal, MeritSpacing.large)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        showingBonusSelector = false
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func bonusOptionRow(minutes: Int, title: String, subtitle: String, icon: String, badge: String? = nil) -> some View {
        let isSelected = selectedBonusMinutes == minutes
        Button {
            selectedBonusMinutes = minutes
        } label: {
            HStack(spacing: MeritSpacing.medium) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundColor(isSelected ? .white : MeritColor.accent)
                    .frame(width: 44, height: 44)
                    .background(isSelected ? MeritColor.accent : MeritColor.accent.opacity(0.12))
                    .clipShape(Circle())

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(title)
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)

                        if let badge {
                            Text(badge)
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(.orange)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.orange.opacity(0.15))
                                .clipShape(Capsule())
                        }
                    }

                    Text(subtitle)
                        .font(MeritTypography.caption)
                        .foregroundColor(MeritColor.secondaryLabel)
                }

                Spacer()

                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 20))
                    .foregroundColor(isSelected ? MeritColor.accent : MeritColor.secondaryLabel)
            }
            .padding(MeritSpacing.medium)
            .background(isSelected ? MeritColor.accent.opacity(0.08) : MeritColor.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous)
                    .stroke(isSelected ? MeritColor.accent : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
    }
}
