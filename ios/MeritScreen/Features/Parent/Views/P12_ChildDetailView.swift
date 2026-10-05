import SwiftUI

public struct P12_ChildDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: ChildDetailViewModel

    // Edit Profile State
    @State private var editName: String = ""
    @State private var editAgeBand: AgeBand = .age7To9
    @State private var editAvatar: AvatarPreset = .rabbit
    @State private var editLanguage: String = "en"
    @State private var isEditingProfile: Bool = false

    // Delete confirmation
    @State private var showDeleteConfirmation: Bool = false
    @State private var deleteConfirmInput: String = ""

    public init(childId: String) {
        _viewModel = State(wrappedValue: ChildDetailViewModel(childId: childId))
    }

    public var body: some View {
        Group {
            switch viewModel.uiState {
            case .idle, .loading:
                LoadingView("Loading child details...")
            case .empty:
                EmptyStateView(
                    icon: "person.slash",
                    title: "Child Not Found",
                    description: "We couldn't locate this child profile."
                )
            case .error(let error):
                ErrorView(error: error) {
                    viewModel.refresh()
                }
            case .success(let detail):
                content(detail: detail)
            }
        }
        .navigationTitle(viewModel.uiState.data?.profile.displayName ?? "Child Detail")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            viewModel.load()
        }
        .onChange(of: viewModel.isDeleted) { _, deleted in
            if deleted {
                dismiss()
            }
        }
    }

    @ViewBuilder
    private func content(detail: ChildDetailUi) -> some View {
        ScrollView {
            VStack(spacing: MeritSpacing.medium) {
                // Bonus Toast Banner
                if let toast = detail.bonusToastMessage {
                    HStack {
                        Image(systemName: "sparkles")
                            .foregroundColor(.yellow)
                        Text(toast)
                            .font(MeritTypography.subheadline)
                            .foregroundColor(MeritColor.label)
                        Spacer()
                        Button(action: { viewModel.dismissBonusToast() }) {
                            Image(systemName: "xmark")
                                .font(.system(size: 14))
                                .foregroundColor(MeritColor.secondaryLabel)
                        }
                    }
                    .padding(MeritSpacing.medium)
                    .background(MeritColor.accent.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium))
                    .padding(.horizontal, MeritSpacing.large)
                }

                // Error Card
                if let error = viewModel.actionError {
                    StateErrorCard(message: error)
                        .padding(.horizontal, MeritSpacing.large)
                }

                // Child Profile Header Card
                profileHeaderCard(detail: detail)

                // Quick Supervision Controls
                quickControlsSection(detail: detail)

                // Screen Balance Card
                screenBalanceCard(detail: detail)

                // Settings Navigation Links
                settingsNavigationSection(detail: detail)

                // Edit Profile Card
                editProfileSection(detail: detail)

                // Danger Zone
                dangerZoneSection(detail: detail)

                Spacer(minLength: MeritSpacing.xLarge)
            }
            .padding(.vertical, MeritSpacing.medium)
        }
        .refreshable {
            viewModel.refresh()
        }
        .alert("Delete Child Profile?", isPresented: $showDeleteConfirmation) {
            TextField("Type '\(detail.profile.displayName)'", text: $deleteConfirmInput)
            Button("Cancel", role: .cancel) {
                deleteConfirmInput = ""
            }
            Button("Delete Permanently", role: .destructive) {
                viewModel.deleteChild(confirmationName: deleteConfirmInput)
                deleteConfirmInput = ""
            }
        } message: {
            Text("This will permanently delete \(detail.profile.displayName)'s profile, unpair devices, and wipe historical activity. Type the child's name to confirm.")
        }
    }

    // MARK: - Components

    private func profileHeaderCard(detail: ChildDetailUi) -> some View {
        HStack(spacing: MeritSpacing.medium) {
            ChildAvatarBadge(
                avatar: detail.profile.avatar,
                size: 64,
                isOnline: detail.connectionStatus == .connected
            )

            VStack(alignment: .leading, spacing: 4) {
                Text(detail.profile.displayName)
                    .font(MeritTypography.title2)
                    .foregroundColor(MeritColor.label)

                Text("\(detail.profile.ageBand.displayLabel) • \(detail.curriculumLevelText)")
                    .font(MeritTypography.caption1)
                    .foregroundColor(MeritColor.secondaryLabel)

                HStack(spacing: 6) {
                    Circle()
                        .fill(detail.connectionStatus == .connected ? MeritColor.success : (detail.isPaused ? MeritColor.warning : MeritColor.tertiaryLabel))
                        .frame(width: 8, height: 8)

                    Text(detail.connectionStatusLabel)
                        .font(MeritTypography.caption2)
                        .foregroundColor(MeritColor.secondaryLabel)

                    if let battery = detail.batteryPercent {
                        Text("• \(battery)%")
                            .font(MeritTypography.caption2)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                }
            }

            Spacer()
        }
        .padding(MeritSpacing.large)
        .background(MeritColor.secondaryFill)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge))
        .padding(.horizontal, MeritSpacing.large)
    }

    private func quickControlsSection(detail: ChildDetailUi) -> some View {
        HStack(spacing: MeritSpacing.medium) {
            Button(action: { viewModel.togglePause() }) {
                HStack(spacing: MeritSpacing.small) {
                    Image(systemName: detail.isPaused ? "play.circle.fill" : "pause.circle.fill")
                        .font(.system(size: 20))
                    Text(detail.isPaused ? "Resume Apps" : "Pause Apps")
                        .font(MeritTypography.headline)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, MeritSpacing.medium)
                .background(detail.isPaused ? MeritColor.success : MeritColor.destructive)
                .foregroundColor(.white)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium))
            }

            Button(action: { viewModel.grantBonus(minutes: 15) }) {
                HStack(spacing: MeritSpacing.small) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 20))
                    Text("+15m Bonus")
                        .font(MeritTypography.headline)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, MeritSpacing.medium)
                .background(MeritColor.accent)
                .foregroundColor(.white)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium))
            }
        }
        .padding(.horizontal, MeritSpacing.large)
    }

    private func screenBalanceCard(detail: ChildDetailUi) -> some View {
        VStack(spacing: MeritSpacing.small) {
            HStack {
                Text("Today's Screen Time")
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)
                Spacer()
                Text("\(detail.remainingMinutes)m left of \(detail.dailyCeilingMinutes)m")
                    .font(MeritTypography.subheadline)
                    .foregroundColor(MeritColor.secondaryLabel)
            }

            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(MeritColor.tertiaryFill)
                        .frame(height: 10)

                    let ratio = CGFloat(min(1.0, Double(detail.todayMinutes) / Double(max(detail.dailyCeilingMinutes, 1))))
                    RoundedRectangle(cornerRadius: 6)
                        .fill(ratio > 0.85 ? MeritColor.warning : MeritColor.accent)
                        .frame(width: max(0, geometry.size.width * ratio), height: 10)
                }
            }
            .frame(height: 10)

            HStack {
                Text("\(detail.todayMinutes)m Used")
                    .font(MeritTypography.caption2)
                    .foregroundColor(MeritColor.secondaryLabel)
                Spacer()
                Text("\(detail.allowedAppCount) Apps Supervised")
                    .font(MeritTypography.caption2)
                    .foregroundColor(MeritColor.secondaryLabel)
            }
        }
        .padding(MeritSpacing.large)
        .background(MeritColor.secondaryFill)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge))
        .padding(.horizontal, MeritSpacing.large)
    }

    private func settingsNavigationSection(detail: ChildDetailUi) -> some View {
        VStack(spacing: 1) {
            NavigationLink(destination: P13_AllowlistView(childId: detail.profile.childId)) {
                settingsRow(icon: "apps.iphone", title: "App Allowlist", subtitle: "\(detail.allowedAppCount) supervised apps")
            }

            NavigationLink(destination: P14_TimeLimitsView(childId: detail.profile.childId)) {
                settingsRow(icon: "hourglass", title: "Time Limits & Fail Lock", subtitle: "\(detail.dailyCeilingMinutes)m daily ceiling • 15m blocks")
            }

            NavigationLink(destination: P15_QuizSettingsView(childId: detail.profile.childId)) {
                settingsRow(icon: "sparkles", title: "Quiz & AI Settings", subtitle: "\(detail.policy.quizMode.displayLabel) • Adaptive")
            }

            NavigationLink(destination: P16_RewardsView(childId: detail.profile.childId)) {
                settingsRow(icon: "star.circle.fill", title: "Rewards & Stickers", subtitle: detail.policy.rewardsEnabled ? "Stickers active • +\(detail.policy.extraMinutesOnPass)m on pass" : "Rewards disabled")
            }

            NavigationLink(destination: P17_ReportsView(childId: detail.profile.childId)) {
                settingsRow(icon: "chart.bar.xaxis", title: "Activity Reports & Insights", subtitle: "Daily screen time • Quiz pass rate • AI topics")
            }
        }
        .background(MeritColor.secondaryFill)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge))
        .padding(.horizontal, MeritSpacing.large)
    }

    private func settingsRow(icon: String, title: String, subtitle: String) -> some View {
        HStack(spacing: MeritSpacing.medium) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(MeritColor.accent)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.label)
                Text(subtitle)
                    .font(MeritTypography.caption2)
                    .foregroundColor(MeritColor.secondaryLabel)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.system(size: 14))
                .foregroundColor(MeritColor.tertiaryLabel)
        }
        .padding(MeritSpacing.medium)
        .contentShape(Rectangle())
    }

    private func editProfileSection(detail: ChildDetailUi) -> some View {
        VStack(alignment: .leading, spacing: MeritSpacing.medium) {
            Button(action: {
                if !isEditingProfile {
                    editName = detail.profile.displayName
                    editAgeBand = detail.profile.ageBand
                    editAvatar = detail.profile.avatar
                    editLanguage = detail.profile.language
                }
                isEditingProfile.toggle()
            }) {
                HStack {
                    Image(systemName: "pencil")
                    Text(isEditingProfile ? "Cancel Profile Editing" : "Edit Profile Details")
                        .font(MeritTypography.headline)
                    Spacer()
                    Image(systemName: isEditingProfile ? "chevron.up" : "chevron.down")
                }
                .foregroundColor(MeritColor.accent)
            }

            if isEditingProfile {
                VStack(spacing: MeritSpacing.small) {
                    TextField("Child Name", text: $editName)
                        .padding(MeritSpacing.small)
                        .background(MeritColor.tertiaryFill)
                        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusSmall))

                    Picker("Age Band", selection: $editAgeBand) {
                        ForEach(AgeBand.allCases, id: \.self) { band in
                            Text(band.displayLabel).tag(band)
                        }
                    }

                    Picker("Avatar", selection: $editAvatar) {
                        ForEach(AvatarPreset.allCases, id: \.self) { preset in
                            Text("\(preset.emoji) \(preset.label)").tag(preset)
                        }
                    }

                    MeritButton("Save Profile", style: .primary, isLoading: viewModel.isSavingProfile) {
                        viewModel.updateProfile(
                            name: editName,
                            ageBand: editAgeBand,
                            avatar: editAvatar,
                            language: editLanguage
                        )
                        isEditingProfile = false
                    }
                }
            }
        }
        .padding(MeritSpacing.large)
        .background(MeritColor.secondaryFill)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge))
        .padding(.horizontal, MeritSpacing.large)
    }

    private func dangerZoneSection(detail: ChildDetailUi) -> some View {
        VStack(alignment: .leading, spacing: MeritSpacing.small) {
            Text("Danger Zone")
                .font(MeritTypography.caption1)
                .foregroundColor(MeritColor.secondaryLabel)
                .textCase(.uppercase)

            Button(role: .destructive, action: { showDeleteConfirmation = true }) {
                HStack {
                    Image(systemName: "trash")
                    Text("Delete \(detail.profile.displayName)'s Profile")
                        .font(MeritTypography.body)
                    Spacer()
                }
                .padding(MeritSpacing.medium)
                .background(MeritColor.destructive.opacity(0.12))
                .foregroundColor(MeritColor.destructive)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium))
            }
        }
        .padding(.horizontal, MeritSpacing.large)
    }
}
