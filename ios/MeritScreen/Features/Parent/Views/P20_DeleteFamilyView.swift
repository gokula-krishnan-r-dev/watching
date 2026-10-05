import SwiftUI

public struct P20_DeleteFamilyView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: AccountViewModel
    public let onFamilyDeleted: () -> Void

    public init(viewModel: AccountViewModel, onFamilyDeleted: @escaping () -> Void) {
        _viewModel = State(wrappedValue: viewModel)
        self.onFamilyDeleted = onFamilyDeleted
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: MeritSpacing.large) {
                // Hazard Banner
                HStack(alignment: .top, spacing: MeritSpacing.medium) {
                    Image(systemName: "exclamationmark.octagon.fill")
                        .font(.system(size: 36))
                        .foregroundColor(MeritColor.destructive)

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Permanent Data Destruction")
                            .font(MeritTypography.title3)
                            .foregroundColor(MeritColor.destructive)

                        Text("Deleting this family cannot be undone. All child profiles, policy rules, device pairings, and learning progress will be permanently erased from cloud and local storage.")
                            .font(MeritTypography.body)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                }
                .padding(MeritSpacing.large)
                .background(MeritColor.destructive.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge))

                if let error = viewModel.deleteError {
                    StateErrorCard(message: error)
                }

                VStack(alignment: .leading, spacing: MeritSpacing.small) {
                    Text("Type DELETE to confirm")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)

                    TextField("DELETE", text: $viewModel.deleteConfirmationText)
                        .autocapitalization(.allCharacters)
                        .disableAutocorrection(true)
                        .padding(MeritSpacing.medium)
                        .background(MeritColor.secondaryFill)
                        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium))
                }

                MeritButton(
                    "Permanently Delete Family",
                    icon: "trash.fill",
                    style: .destructive,
                    isLoading: viewModel.isDeletingFamily,
                    isEnabled: viewModel.deleteConfirmationText.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() == "DELETE"
                ) {
                    viewModel.deleteFamily()
                }

                Spacer()
            }
            .padding(MeritSpacing.large)
        }
        .navigationTitle("Delete Family")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: viewModel.familyDeleted) { _, deleted in
            if deleted {
                onFamilyDeleted()
            }
        }
    }
}
