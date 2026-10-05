import SwiftUI

/// Child Sticker Book & Explorer Collection.
/// Displays earned sticker badges, XP level, and next rewards.
public struct ChildStickerBookView: View {
    public let stickers: [ChildSticker]
    public let totalXp: Int
    public let explorerLevel: Int
    public let onDismiss: () -> Void

    public init(
        stickers: [ChildSticker],
        totalXp: Int,
        explorerLevel: Int,
        onDismiss: @escaping () -> Void
    ) {
        self.stickers = stickers
        self.totalXp = totalXp
        self.explorerLevel = explorerLevel
        self.onDismiss = onDismiss
    }

    private let columns = [
        GridItem(.adaptive(minimum: 100, maximum: 140), spacing: MeritSpacing.medium)
    ]

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: MeritSpacing.xLarge) {
                    // Level Header Card
                    VStack(spacing: MeritSpacing.small) {
                        Text("🌟 Explorer Level \(explorerLevel)")
                            .font(MeritTypography.title2)
                            .foregroundColor(MeritColor.label)

                        Text("\(totalXp) XP Earned")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.accent)

                        ProgressView(value: Double(totalXp % 50), total: 50.0)
                            .tint(MeritColor.accent)
                            .padding(.horizontal, MeritSpacing.large)

                        Text("Pass quizzes after app blocks to earn XP and new stickers!")
                            .font(MeritTypography.caption)
                            .foregroundColor(MeritColor.secondaryLabel)
                            .multilineTextAlignment(.center)
                    }
                    .padding(MeritSpacing.large)
                    .frame(maxWidth: .infinity)
                    .background(MeritColor.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))

                    // Stickers Grid
                    VStack(alignment: .leading, spacing: MeritSpacing.medium) {
                        Text("Unlocked Badges (\(stickers.count))")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)

                        LazyVGrid(columns: columns, spacing: MeritSpacing.medium) {
                            ForEach(stickers) { sticker in
                                VStack(spacing: MeritSpacing.small) {
                                    Text(sticker.emoji)
                                        .font(.system(size: 44))
                                        .frame(width: 72, height: 72)
                                        .background(MeritColor.secondaryFill)
                                        .clipShape(Circle())

                                    Text(sticker.title)
                                        .font(MeritTypography.caption)
                                        .fontWeight(.semibold)
                                        .foregroundColor(MeritColor.label)
                                        .multilineTextAlignment(.center)
                                        .lineLimit(2)

                                    Text(sticker.stage)
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(MeritColor.secondaryLabel)
                                }
                                .padding(MeritSpacing.small)
                                .frame(maxWidth: .infinity)
                                .background(MeritColor.cardBackground)
                                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
                            }
                        }
                    }
                }
                .padding(MeritSpacing.large)
                .responsiveContainer(maxWidth: 600)
            }
            .background(MeritColor.groupedBackground.ignoresSafeArea())
            .navigationTitle("Sticker Album")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        onDismiss()
                    }
                }
            }
        }
    }
}
