import SwiftUI
import FamilyControls

public struct P13_AllowlistView: View {
    @State private var viewModel: AllowlistViewModel
    @State private var showActivityPicker: Bool = false
    @State private var showAddCustom: Bool = false
    @State private var customBundleId: String = ""
    @State private var customDisplayName: String = ""

    public init(childId: String) {
        _viewModel = State(wrappedValue: AllowlistViewModel(childId: childId))
    }

    public var body: some View {
        List {
            // Screen Time platform note
            Section {
                HStack(alignment: .top, spacing: MeritSpacing.small) {
                    Image(systemName: "shield.lefthalf.filled")
                        .foregroundColor(MeritColor.accent)
                        .font(.system(size: 24))
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Cross-Platform Supervision")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)
                        Text("On iOS child devices, Apple Screen Time shields enforce system boundaries. On Android devices managed from this iPhone, package allowlist rules apply immediately via cloud sync.")
                            .font(MeritTypography.caption1)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                }
                .padding(.vertical, MeritSpacing.xxSmall)
            }

            if let error = viewModel.error {
                Section {
                    StateErrorCard(message: error)
                }
            }

            // iOS Native Screen Time Activity Picker
            Section(header: Text("iOS Screen Time Selection")) {
                Button {
                    showActivityPicker = true
                } label: {
                    HStack {
                        Image(systemName: "apps.iphone")
                            .foregroundColor(MeritColor.accent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Select Apps via Screen Time")
                                .font(MeritTypography.body)
                                .foregroundColor(MeritColor.label)
                            Text("\(viewModel.activitySelection.applicationTokens.count) apps, \(viewModel.activitySelection.categoryTokens.count) categories selected")
                                .font(MeritTypography.caption)
                                .foregroundColor(MeritColor.secondaryLabel)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                }
            }

            // Existing Rules Section
            Section(header: Text("Supervised Apps (\(viewModel.filteredRules.count))")) {
                if viewModel.filteredRules.isEmpty {
                    Text(viewModel.searchQuery.isEmpty ? "No apps configured yet." : "No apps match '\(viewModel.searchQuery)'.")
                        .font(MeritTypography.subheadline)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .padding(.vertical, MeritSpacing.small)
                } else {
                    ForEach(viewModel.filteredRules) { rule in
                        HStack(spacing: MeritSpacing.small) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(rule.allowed ? MeritColor.accent.opacity(0.15) : MeritColor.destructive.opacity(0.15))
                                    .frame(width: 36, height: 36)
                                Image(systemName: rule.allowed ? "checkmark.circle.fill" : "nosign")
                                    .foregroundColor(rule.allowed ? MeritColor.accent : MeritColor.destructive)
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                Text(rule.displayName)
                                    .font(MeritTypography.body)
                                    .foregroundColor(MeritColor.label)
                                Text(rule.packageOrBundleId)
                                    .font(MeritTypography.caption2)
                                    .foregroundColor(MeritColor.secondaryLabel)
                            }

                            Spacer()

                            Toggle("", isOn: Binding(
                                get: { rule.allowed },
                                set: { _ in viewModel.toggleAllowed(rule: rule) }
                            ))
                            .labelsHidden()
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                viewModel.deleteRule(rule: rule)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                }
            }

            // Add From Device Inventory
            if !viewModel.pickableApps.isEmpty {
                Section(header: Text("Detected on Child Device")) {
                    ForEach(viewModel.pickableApps) { app in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(app.appName)
                                    .font(MeritTypography.body)
                                Text(app.category)
                                    .font(MeritTypography.caption2)
                                    .foregroundColor(MeritColor.secondaryLabel)
                            }
                            Spacer()
                            Button("Allow") {
                                viewModel.toggleInstalledApp(app: app, allowed: true)
                            }
                            .font(MeritTypography.footnote)
                            .buttonStyle(.borderedProminent)
                            .tint(MeritColor.accent)
                        }
                    }
                }
            }

            // Add Custom Rule
            Section(header: Text("Custom App Bundle")) {
                if showAddCustom {
                    VStack(alignment: .leading, spacing: MeritSpacing.small) {
                        TextField("Bundle / Package ID (e.g. com.example.app)", text: $customBundleId)
                            .font(MeritTypography.body)
                            .autocapitalization(.none)
                            .disableAutocorrection(true)

                        TextField("Display Name (e.g. Example App)", text: $customDisplayName)
                            .font(MeritTypography.body)

                        HStack {
                            Button("Cancel") {
                                showAddCustom = false
                                customBundleId = ""
                                customDisplayName = ""
                            }
                            .foregroundColor(MeritColor.secondaryLabel)

                            Spacer()

                            Button("Add Rule") {
                                viewModel.addCustomApp(packageOrBundleId: customBundleId, displayName: customDisplayName)
                                showAddCustom = false
                                customBundleId = ""
                                customDisplayName = ""
                            }
                            .fontWeight(.bold)
                            .disabled(customBundleId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        }
                        .padding(.top, MeritSpacing.xxSmall)
                    }
                    .padding(.vertical, MeritSpacing.xxSmall)
                } else {
                    Button(action: { showAddCustom = true }) {
                        HStack {
                            Image(systemName: "plus.circle.fill")
                            Text("Add Custom App Rule")
                        }
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.accent)
                    }
                }
            }
        }
        .navigationTitle("App Allowlist")
        .searchable(text: $viewModel.searchQuery, prompt: "Search supervised apps")
        .refreshable {
            viewModel.refresh()
        }
        .familyActivityPicker(
            isPresented: $showActivityPicker,
            selection: $viewModel.activitySelection
        )
        .onChange(of: viewModel.activitySelection) { _, newSelection in
            viewModel.updateActivitySelection(newSelection)
        }
    }
}
