import SwiftUI

public struct P14_TimeLimitsView: View {
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
                VStack(alignment: .leading, spacing: MeritSpacing.small) {
                    HStack {
                        Text("Daily Ceiling Allowance")
                            .font(MeritTypography.body)
                        Spacer()
                        Text("\(viewModel.policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes) min")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.accent)
                    }

                    Slider(
                        value: Binding(
                            get: { Double(viewModel.policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes) },
                            set: { newValue in
                                let rounded = Int((newValue / 15.0).rounded() * 15.0)
                                viewModel.update { $0.dailyCeilingMinutes = rounded }
                            }
                        ),
                        in: 30...360,
                        step: 15
                    )
                    .tint(MeritColor.accent)

                    Text("Max total recreational and learning screen time allowed per day.")
                        .font(MeritTypography.caption1)
                        .foregroundColor(MeritColor.secondaryLabel)
                }
                .padding(.vertical, MeritSpacing.xxSmall)
            } header: {
                Text("Daily Limit")
            }

            Section {
                Picker("App Block Length", selection: Binding(
                    get: { viewModel.policy.defaultBlockMinutes },
                    set: { val in viewModel.update { $0.defaultBlockMinutes = val } }
                )) {
                    Text("15 minutes").tag(15)
                    Text("20 minutes").tag(20)
                    Text("30 minutes").tag(30)
                    Text("45 minutes").tag(45)
                    Text("60 minutes").tag(60)
                }

                Picker("Fail Lock Cooldown", selection: Binding(
                    get: { viewModel.policy.defaultCooldownMinutes },
                    set: { val in viewModel.update { $0.defaultCooldownMinutes = val } }
                )) {
                    Text("5 minutes").tag(5)
                    Text("10 minutes").tag(10)
                    Text("15 minutes").tag(15)
                    Text("30 minutes").tag(30)
                    Text("45 minutes").tag(45)
                }

                Toggle("Allow Practice During Cooldown", isOn: Binding(
                    get: { viewModel.policy.allowRetryDuringCooldown },
                    set: { val in viewModel.update { $0.allowRetryDuringCooldown = val } }
                ))
            } header: {
                Text("Session & Lock Behavior")
            } footer: {
                Text("When time runs out or a quiz is failed, apps lock safely. Children can review educational explanations while cooling down.")
                    .font(MeritTypography.caption1)
            }

            Section {
                Toggle("Bedtime Schedule Lock", isOn: Binding(
                    get: { viewModel.policy.bedtimeEnabled },
                    set: { val in viewModel.update { $0.bedtimeEnabled = val } }
                ))

                if viewModel.policy.bedtimeEnabled {
                    HStack {
                        Text("Bedtime Start")
                        Spacer()
                        Text(viewModel.policy.bedtimeStartLabel)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }

                    HStack {
                        Text("Morning Wake")
                        Spacer()
                        Text(viewModel.policy.bedtimeEndLabel)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                }
            } header: {
                Text("Bedtime Schedule")
            } footer: {
                Text("During bedtime hours, all non-emergency apps remain locked with a calming sleep view.")
                    .font(MeritTypography.caption1)
            }

            Section {
                MeritButton(
                    "Save Time Limits",
                    icon: "checkmark",
                    style: .primary,
                    isLoading: viewModel.isSaving
                ) {
                    viewModel.save()
                }
            }
        }
        .navigationTitle("Time Limits & Lock")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: viewModel.isSaved) { _, saved in
            if saved {
                _ = viewModel.consumeSaved()
                dismiss()
            }
        }
    }
}
