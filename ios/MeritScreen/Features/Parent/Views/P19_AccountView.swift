import SwiftUI

public struct P19_AccountView: View {
    @State private var viewModel = AccountViewModel()
    @State private var appearanceStore = AppearanceStore.shared
    @State private var showAddChildSheet: Bool = false
    @State private var showSignOutAlert: Bool = false

    public let onSignOut: () -> Void

    public init(onSignOut: @escaping () -> Void) {
        self.onSignOut = onSignOut
    }

    public var body: some View {
        Form {
            // Profile & Family Info
            Section(header: Text("Parent & Household")) {
                HStack(spacing: MeritSpacing.medium) {
                    Circle()
                        .fill(MeritColor.accent)
                        .frame(width: 52, height: 52)
                        .overlay(
                            Image(systemName: "person.fill")
                                .font(.system(size: 26))
                                .foregroundColor(.white)
                        )

                    VStack(alignment: .leading, spacing: 4) {
                        Text(viewModel.familyName)
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)
                        Text(viewModel.email)
                            .font(MeritTypography.caption1)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                }
                .padding(.vertical, MeritSpacing.xxSmall)
            }

            // Children List
            Section(header: Text("Managed Children (\(viewModel.children.count))")) {
                ForEach(viewModel.children) { child in
                    HStack(spacing: MeritSpacing.medium) {
                        ChildAvatarBadge(avatar: child.avatar, size: 36)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(child.displayName)
                                .font(MeritTypography.body)
                                .foregroundColor(MeritColor.label)
                            Text(child.ageBand.displayLabel)
                                .font(MeritTypography.caption2)
                                .foregroundColor(MeritColor.secondaryLabel)
                        }
                    }
                }

                if viewModel.children.count < AppConfig.maxChildrenPerParent {
                    Button(action: { showAddChildSheet = true }) {
                        HStack {
                            Image(systemName: "plus.circle.fill")
                            Text("Add Another Child")
                        }
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.accent)
                    }
                }
            }

            // Appearance Preferences
            Section(header: Text("Appearance")) {
                Picker("Theme", selection: $appearanceStore.mode) {
                    ForEach(AppearanceMode.allCases, id: \.self) { mode in
                        Text(mode.displayName).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
            }

            // Change Parent PIN
            Section {
                SecureField("New 4-digit PIN", text: $viewModel.newPin)
                    .keyboardType(.numberPad)
                    .font(MeritTypography.body)

                SecureField("Confirm New PIN", text: $viewModel.confirmPin)
                    .keyboardType(.numberPad)
                    .font(MeritTypography.body)

                if let pinError = viewModel.pinError {
                    Text(pinError)
                        .font(MeritTypography.caption1)
                        .foregroundColor(MeritColor.destructive)
                }

                if viewModel.pinSaved {
                    Text("✓ Parent PIN updated successfully.")
                        .font(MeritTypography.caption1)
                        .foregroundColor(MeritColor.success)
                }

                MeritButton(
                    "Update PIN",
                    style: .outline,
                    isLoading: viewModel.isSavingPin,
                    isEnabled: viewModel.newPin.count == AppConfig.parentPinMaxLength && viewModel.confirmPin.count == AppConfig.parentPinMaxLength
                ) {
                    viewModel.saveNewPin()
                }
            } header: {
                Text("Change Parent PIN")
            } footer: {
                Text("This PIN gates parental settings, app allowlist edits, and session unpairing.")
                    .font(MeritTypography.caption1)
            }

            // Sign Out
            Section {
                Button(action: { showSignOutAlert = true }) {
                    HStack {
                        Image(systemName: "rectangle.portrait.and.arrow.right")
                        Text("Sign Out")
                        Spacer()
                    }
                    .foregroundColor(MeritColor.label)
                }
            }

            // Danger Zone: Delete Family
            Section {
                NavigationLink(destination: P20_DeleteFamilyView(viewModel: viewModel, onFamilyDeleted: onSignOut)) {
                    HStack {
                        Image(systemName: "trash.fill")
                            .foregroundColor(MeritColor.destructive)
                        Text("Delete Entire Family")
                            .foregroundColor(MeritColor.destructive)
                    }
                }
            } header: {
                Text("Danger Zone")
            } footer: {
                Text("Permanently erases all children profiles, device pairings, and historical learning data.")
                    .font(MeritTypography.caption1)
            }
        }
        .navigationTitle("Account & Settings")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showAddChildSheet) {
            AddChildSheet { _ in
                viewModel.load()
            }
        }
        .alert("Sign Out?", isPresented: $showSignOutAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Sign Out", role: .destructive) {
                onSignOut()
            }
        } message: {
            Text("You will need to verify your email again to sign back in.")
        }
    }
}
