import SwiftUI

public struct P16_RewardsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: PolicyEditorViewModel

    public init(childId: String) {
        _viewModel = State(wrappedValue: PolicyEditorViewModel(childId: childId))
    }

    public var body: some View {
        Form {
            if let error = viewModel.error {
                Section {
                    StateErrorCard(message: error)
                }
            }

            Section {
                Toggle("Sticker Album & XP Rewards", isOn: Binding(
                    get: { viewModel.policy.rewardsEnabled },
                    set: { val in viewModel.update { $0.rewardsEnabled = val } }
                ))

                Picker("Bonus Time On Quiz Pass", selection: Binding(
                    get: { viewModel.policy.extraMinutesOnPass },
                    set: { val in viewModel.update { $0.extraMinutesOnPass = val } }
                )) {
                    Text("No Extra Minutes").tag(0)
                    Text("+5 minutes").tag(5)
                    Text("+10 minutes").tag(10)
                    Text("+15 minutes (Standard)").tag(15)
                    Text("+30 minutes").tag(30)
                }
            } header: {
                Text("Learning Incentives")
            } footer: {
                Text("Passing quizzes awards XP points that unlock collectible stickers in the child's sticker album.")
                    .font(MeritTypography.caption1)
            }

            Section {
                Toggle("Weekend Bonus Time (+5 min/block)", isOn: Binding(
                    get: { viewModel.policy.weekendBonusEnabled },
                    set: { val in viewModel.update { $0.weekendBonusEnabled = val } }
                ))
            } header: {
                Text("Weekend Perks")
            } footer: {
                Text("On Saturdays and Sundays, children receive extra time allowance per completed block.")
                    .font(MeritTypography.caption1)
            }

            Section {
                MeritButton(
                    "Save Rewards Settings",
                    icon: "checkmark",
                    style: .primary,
                    isLoading: viewModel.isSaving
                ) {
                    viewModel.save()
                }
            }
        }
        .navigationTitle("Rewards & Incentives")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: viewModel.isSaved) { _, saved in
            if saved {
                _ = viewModel.consumeSaved()
                dismiss()
            }
        }
    }
}
