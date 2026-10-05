import SwiftUI

/// Parent dashboard placeholder shell with responsive iPad/iPhone layout and Appearance selector.
public struct ParentDashboardPlaceholderView: View {
    public let onResetRole: () -> Void

    @Environment(AppearanceStore.self) private var appearanceStore

    public init(onResetRole: @escaping () -> Void) {
        self.onResetRole = onResetRole
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: MeritSpacing.large) {
                    // Header card
                    VStack(alignment: .leading, spacing: MeritSpacing.xSmall) {
                        Text("Parent Dashboard")
                            .font(MeritTypography.largeTitle)
                            .foregroundColor(MeritColor.label)

                        Text("Managing family screen time and learning rules.")
                            .font(MeritTypography.subheadline)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    // Appearance Settings Card
                    VStack(alignment: .leading, spacing: MeritSpacing.medium) {
                        Label("Appearance Mode", systemImage: "paintpalette.fill")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)

                        Text("Select your preferred display appearance across the application.")
                            .font(MeritTypography.footnote)
                            .foregroundColor(MeritColor.secondaryLabel)

                        @Bindable var store = appearanceStore
                        Picker("Appearance", selection: $store.mode) {
                            ForEach(AppearanceMode.allCases) { mode in
                                Label(mode.title, systemImage: mode.iconName).tag(mode)
                            }
                        }
                        .pickerStyle(.segmented)
                    }
                    .padding(MeritSpacing.medium)
                    .background(MeritColor.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))

                    // Child status placeholder
                    VStack(alignment: .leading, spacing: MeritSpacing.medium) {
                        HStack {
                            Text("🐰 Leo's Device")
                                .font(MeritTypography.headline)
                                .foregroundColor(MeritColor.label)
                            Spacer()
                            Text("Connected")
                                .font(MeritTypography.caption)
                                .foregroundColor(MeritColor.success)
                                .padding(.horizontal, MeritSpacing.small)
                                .padding(.vertical, MeritSpacing.xxxSmall)
                                .background(MeritColor.success.opacity(0.15))
                                .clipShape(Capsule())
                        }

                        Divider()

                        HStack(spacing: MeritSpacing.large) {
                            statItem(title: "Today's Time", value: "35m")
                            statItem(title: "Quiz Pass Rate", value: "85%")
                            statItem(title: "Current Status", value: "Active")
                        }
                    }
                    .padding(MeritSpacing.medium)
                    .background(MeritColor.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))

                    // Role switch action
                    MeritButton("Switch Role / Log Out", style: .outline, action: onResetRole)
                        .padding(.top, MeritSpacing.medium)
                }
                .padding(MeritSpacing.large)
                .responsiveContainer(maxWidth: 640)
            }
            .background(MeritColor.groupedBackground.ignoresSafeArea())
            .navigationTitle("MeritScreen")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func statItem(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: MeritSpacing.xxxSmall) {
            Text(title)
                .font(MeritTypography.caption)
                .foregroundColor(MeritColor.secondaryLabel)
            Text(value)
                .font(MeritTypography.title3)
                .foregroundColor(MeritColor.label)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
