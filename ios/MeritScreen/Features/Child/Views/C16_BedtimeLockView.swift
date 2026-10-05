import SwiftUI

/// Screen C16: Bedtime Lock Screen (Bedtime Rest Mode).
///
/// Parity with Android's BedtimeLockPane:
/// 1. Calm night sleep mascot (🌜) and silent night mode banner.
/// 2. Morning wake-up countdown and schedule label.
/// 3. Emergency contacts (Call Mom, Call Dad) are always reachable.
/// 4. Parent PIN override allows bypassing the bedtime lock.
public struct C16_BedtimeLockView: View {
    public let childName: String
    public let bedtimeEndLabel: String
    public let onOpenParentPin: () -> Void
    public let onCallMom: () -> Void
    public let onCallDad: () -> Void

    public init(
        childName: String,
        bedtimeEndLabel: String = "7:00 AM",
        onOpenParentPin: @escaping () -> Void,
        onCallMom: @escaping () -> Void = {},
        onCallDad: @escaping () -> Void = {}
    ) {
        self.childName = childName
        self.bedtimeEndLabel = bedtimeEndLabel
        self.onOpenParentPin = onOpenParentPin
        self.onCallMom = onCallMom
        self.onCallDad = onCallDad
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: MeritSpacing.large) {
                // Top Status Indicator
                HStack {
                    HStack(spacing: 6) {
                        Image(systemName: "moon.zzz.fill")
                            .font(.system(size: 13))
                            .foregroundColor(MeritColor.accent)
                        Text("BEDTIME MODE ACTIVE")
                            .font(MeritTypography.caption)
                            .fontWeight(.bold)
                            .foregroundColor(MeritColor.accent)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(MeritColor.accent.opacity(0.12))
                    .clipShape(Capsule())

                    Spacer()

                    Text("Silent")
                        .font(MeritTypography.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(MeritColor.secondaryFill)
                        .clipShape(Capsule())
                }
                .padding(.top, MeritSpacing.large)

                // Sleep Mascot
                ZStack {
                    Circle()
                        .fill(MeritColor.accent.opacity(0.15))
                        .frame(width: 120, height: 120)

                    Text("🌜")
                        .font(.system(size: 54))
                }

                // Sleep Copy
                VStack(spacing: MeritSpacing.xSmall) {
                    Text("Time to dream, \(childName) 😴")
                        .font(MeritTypography.title2)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.label)
                        .multilineTextAlignment(.center)

                    Text("All play and video apps are softly resting until tomorrow morning at \(bedtimeEndLabel).")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, MeritSpacing.medium)
                }

                // Morning Wake Up Schedule Card
                VStack(spacing: MeritSpacing.medium) {
                    HStack(spacing: 10) {
                        ZStack {
                            Circle()
                                .fill(MeritColor.secondaryFill)
                                .frame(width: 44, height: 44)
                            Text("☀️")
                                .font(.system(size: 22))
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text("WAKES UP AT")
                                .font(MeritTypography.caption)
                                .foregroundColor(MeritColor.secondaryLabel)
                            Text(bedtimeEndLabel)
                                .font(MeritTypography.headline)
                                .fontWeight(.bold)
                                .foregroundColor(MeritColor.label)
                        }

                        Spacer()

                        Text("Scheduled Sleep")
                            .font(MeritTypography.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(MeritColor.accent)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(MeritColor.accent.opacity(0.12))
                            .clipShape(Capsule())
                    }
                }
                .padding(MeritSpacing.large)
                .frame(maxWidth: .infinity)
                .background(MeritColor.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))

                // Emergency Contacts Card
                VStack(spacing: MeritSpacing.medium) {
                    HStack {
                        HStack(spacing: MeritSpacing.small) {
                            Image(systemName: "phone.fill")
                                .foregroundColor(MeritColor.accent)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Emergency Contacts")
                                    .font(MeritTypography.headline)
                                    .foregroundColor(MeritColor.label)
                                Text("Always Unlocked & Reachable")
                                    .font(MeritTypography.caption)
                                    .foregroundColor(MeritColor.accent)
                            }
                        }
                        Spacer()
                    }

                    HStack(spacing: MeritSpacing.medium) {
                        MeritButton("Call Mom", icon: "phone.fill", style: .secondary) {
                            onCallMom()
                            if let url = URL(string: "tel://") {
                                #if canImport(UIKit)
                                UIApplication.shared.open(url)
                                #endif
                            }
                        }

                        MeritButton("Call Dad", icon: "phone.fill", style: .secondary) {
                            onCallDad()
                            if let url = URL(string: "tel://") {
                                #if canImport(UIKit)
                                UIApplication.shared.open(url)
                                #endif
                            }
                        }
                    }
                }
                .padding(MeritSpacing.large)
                .frame(maxWidth: .infinity)
                .background(MeritColor.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))

                // Parent PIN Override
                Button {
                    onOpenParentPin()
                } label: {
                    Text("Parent override? Enter PIN")
                        .font(MeritTypography.callout)
                        .fontWeight(.medium)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .padding(.vertical, MeritSpacing.small)
                }
                .padding(.bottom, MeritSpacing.large)
            }
            .padding(.horizontal, MeritSpacing.large)
            .responsiveContainer(maxWidth: 520)
        }
        .background(MeritColor.groupedBackground.ignoresSafeArea())
    }
}
