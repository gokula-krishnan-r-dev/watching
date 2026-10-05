import SwiftUI

/// Screen C15: Child Settings & Parent On-Device Menu.
/// Allows parents to inspect policy, force sync, override fail lock,
/// change appearance, and unpair the device.
public struct C15_ChildSettingsMenu: View {
    @Bindable public var viewModel: ChildHubViewModel
    public let onUnpair: () -> Void
    public let onDismiss: () -> Void

    @State private var appearanceStore = AppearanceStore.shared
    @State private var showingUnpairAlert = false
    @State private var syncMessage: String?

    public init(
        viewModel: ChildHubViewModel,
        onUnpair: @escaping () -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.onUnpair = onUnpair
        self.onDismiss = onDismiss
    }

    public var body: some View {
        NavigationStack {
            Form {
                // Child Device Info
                Section(header: Text("Child Device")) {
                    LabeledContent("Profile Name", value: viewModel.childName)
                    LabeledContent("Age Band", value: viewModel.ageBand.displayLabel)
                    LabeledContent("Status", value: viewModel.snapshot.phase.displayLabel)
                    LabeledContent("Explorer Level", value: "Level \(viewModel.explorerLevel) (\(viewModel.totalXp) XP)")
                }

                // Active Limits & Session Controls
                Section(header: Text("Session & Restrictions")) {
                    LabeledContent("Default Block", value: "\(viewModel.policy.defaultBlockMinutes) min")
                    LabeledContent("Fail Cooldown", value: "\(viewModel.policy.defaultCooldownMinutes) min")
                    LabeledContent("Daily Ceiling", value: "\(viewModel.policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes) min")
                    LabeledContent("Used Today", value: "\(viewModel.snapshot.minutesUsedToday) min")

                    if viewModel.snapshot.phase == .shielded {
                        Button(role: .destructive) {
                            viewModel.endFailLockWithParentPin()
                        } label: {
                            Label("End Fail Lock Now", systemImage: "lock.open.fill")
                        }
                    }
                }

                // Policy Sync
                Section(header: Text("Family Sync")) {
                    Button {
                        Task {
                            await viewModel.refreshPolicy()
                            syncMessage = "Rules successfully refreshed!"
                        }
                    } label: {
                        HStack {
                            Label("Refresh Rules from Family", systemImage: "arrow.triangle.2.circlepath")
                            Spacer()
                            if viewModel.isSyncingPolicy {
                                ProgressView()
                            }
                        }
                    }
                    .disabled(viewModel.isSyncingPolicy)

                    if let message = syncMessage {
                        Text(message)
                            .font(MeritTypography.caption)
                            .foregroundColor(MeritColor.accent)
                    }
                }

                // Appearance
                Section(header: Text("Appearance")) {
                    Picker("Theme", selection: $appearanceStore.mode) {
                        ForEach(AppearanceMode.allCases) { mode in
                            Text(mode.displayName).tag(mode)
                        }
                    }
                }

                // Device Management
                Section(header: Text("Device Management")) {
                    Button(role: .destructive) {
                        showingUnpairAlert = true
                    } label: {
                        Label("Unpair Device", systemImage: "trash.fill")
                    }
                }
            }
            .navigationTitle("Device Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        onDismiss()
                    }
                }
            }
            .alert("Unpair This Device?", isPresented: $showingUnpairAlert) {
                Button("Cancel", role: .cancel) {}
                Button("Unpair", role: .destructive) {
                    ChildLocalStore.shared.clearAll()
                    ChildPairingStore.shared.clear()
                    onUnpair()
                }
            } message: {
                Text("Are you sure you want to unpair this child device? All active limits and session data will be removed.")
            }
        }
    }
}
