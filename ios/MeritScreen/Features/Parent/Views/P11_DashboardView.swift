import SwiftUI

public struct P11_DashboardView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var viewModel = DashboardViewModel()
    @State private var showAddChildSheet: Bool = false
    @State private var pulseAlpha: Double = 0.4

    public let onSignOut: () -> Void
    public let onPairDevice: (String) -> Void
    public let onViewReports: ((String?) -> Void)?

    public init(
        onSignOut: @escaping () -> Void,
        onPairDevice: @escaping (String) -> Void,
        onViewReports: ((String?) -> Void)? = nil
    ) {
        self.onSignOut = onSignOut
        self.onPairDevice = onPairDevice
        self.onViewReports = onViewReports
        let autoAddChild = ProcessInfo.processInfo.arguments.contains("-testAddChild") ||
            ProcessInfo.processInfo.arguments.contains("-addChildStep") ||
            ProcessInfo.processInfo.environment["TEST_ADD_CHILD"] != nil
        self._showAddChildSheet = State(initialValue: autoAddChild)
    }

    public var body: some View {
        Group {
            switch viewModel.uiState {
            case .idle, .loading:
                LoadingView("Connecting to family devices...")
            case .empty:
                EmptyStateView(
                    icon: "person.2.badge.gearshape",
                    title: "No Children Added",
                    description: "Add a child profile to track personalized AI education, adaptive learning milestones, and screen balance.",
                    actionTitle: "Add Child Profile"
                ) {
                    showAddChildSheet = true
                }
            case .error(let error):
                ErrorView(error: error) {
                    viewModel.refresh()
                }
            case .success(let data):
                dashboardScaffold(data: data)
            }
        }
        .sheet(isPresented: $showAddChildSheet) {
            AddChildSheet { createdChild in
                viewModel.refresh()
                if let childId = createdChild?.childId {
                    viewModel.selectChild(childId)
                }
            }
        }
        .onAppear {
            viewModel.load()
            Task {
                _ = await ParentPushTokenRegistrar.shared.registerParentTokenIfNeeded()
            }
            withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                pulseAlpha = 1.0
            }
        }
    }

    // MARK: - Main Dashboard Scaffold

    @ViewBuilder
    private func dashboardScaffold(data: DashboardUi) -> some View {
        ScrollView {
            VStack(spacing: MeritSpacing.medium) {
                // Top Household Header
                householdHeader(data: data)

                // Child Chip Selector
                childChipSelector(data: data)

                // Layout switcher based on device size class (iPad vs iPhone)
                if horizontalSizeClass == .regular {
                    // iPad 2-Column Responsive Layout
                    HStack(alignment: .top, spacing: MeritSpacing.large) {
                        VStack(spacing: MeritSpacing.medium) {
                            if let rec = data.analytics.aiRecommendation {
                                aiInsightCard(recommendation: rec, analytics: data.analytics)
                            }
                            kpiGrid(analytics: data.analytics)
                            trendChartCard(data: data)
                        }
                        .frame(maxWidth: .infinity)

                        VStack(spacing: MeritSpacing.medium) {
                            deviceSupervisionSection(data: data)
                            activityFeedCard(events: data.recentEvents)
                        }
                        .frame(maxWidth: .infinity)
                    }
                } else {
                    // iPhone Single Column Scroll
                    if let rec = data.analytics.aiRecommendation {
                        aiInsightCard(recommendation: rec, analytics: data.analytics)
                    }
                    kpiGrid(analytics: data.analytics)
                    trendChartCard(data: data)
                    deviceSupervisionSection(data: data)
                    activityFeedCard(events: data.recentEvents)
                }

                Spacer(minLength: MeritSpacing.xLarge)
            }
            .padding(.horizontal, MeritSpacing.large)
            .padding(.vertical, MeritSpacing.medium)
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                HStack(spacing: 8) {
                    Image(systemName: "shield.checkerboard")
                        .font(.system(size: 20))
                        .foregroundColor(MeritColor.accent)
                    Text("WATCHING")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .tracking(1.2)
                        .foregroundColor(MeritColor.accent)
                }
            }

            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 12) {
                    // Sync badge
                    HStack(spacing: 5) {
                        Circle()
                            .fill(MeritColor.success)
                            .frame(width: 7, height: 7)
                            .opacity(pulseAlpha)
                        Text(data.syncStatusTime)
                            .font(MeritTypography.caption2)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(MeritColor.secondaryFill)
                    .clipShape(Capsule())

                    // Account Button
                    NavigationLink(destination: P19_AccountView(onSignOut: onSignOut)) {
                        Circle()
                            .fill(MeritColor.accent)
                            .frame(width: 32, height: 32)
                            .overlay(
                                Image(systemName: "person.fill")
                                    .font(.system(size: 14))
                                    .foregroundColor(.white)
                            )
                    }
                }
            }
        }
        .refreshable {
            viewModel.refresh()
        }
    }

    // MARK: - Header & Selector

    private func householdHeader(data: DashboardUi) -> some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Image(systemName: "house.fill")
                        .font(.system(size: 11))
                        .foregroundColor(MeritColor.accent)
                    Text(data.familyName)
                        .font(MeritTypography.caption1)
                        .fontWeight(.semibold)
                        .foregroundColor(MeritColor.accent)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(MeritColor.accent.opacity(0.12))
                .clipShape(Capsule())

                Text("Hi, \(data.greetingName)")
                    .font(MeritTypography.title1)
                    .foregroundColor(MeritColor.label)

                Text("Family AI Education & Supervision Hub")
                    .font(MeritTypography.caption1)
                    .foregroundColor(MeritColor.secondaryLabel)
            }

            Spacer()

            HStack(spacing: 6) {
                Circle()
                    .fill(data.protectedDevicesCount > 0 ? MeritColor.success : MeritColor.tertiaryLabel)
                    .frame(width: 6, height: 6)
                Image(systemName: "ipad.and.iphone")
                    .font(.system(size: 12))
                    .foregroundColor(MeritColor.accent)
                Text("\(data.protectedDevicesCount) \(data.protectedDevicesCount == 1 ? "Device" : "Devices")")
                    .font(MeritTypography.caption1)
                    .fontWeight(.semibold)
                    .foregroundColor(MeritColor.label)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(MeritColor.secondaryFill)
            .clipShape(Capsule())
        }
    }

    private func childChipSelector(data: DashboardUi) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: MeritSpacing.small) {
                // "All Children" chip
                let isAllSelected = data.selectedChildId == nil
                Button(action: { viewModel.selectChild(nil) }) {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(isAllSelected ? MeritColor.accent : MeritColor.secondaryFill)
                            .frame(width: 28, height: 28)
                            .overlay(
                                Image(systemName: "person.2.fill")
                                    .font(.system(size: 12))
                                    .foregroundColor(isAllSelected ? .white : MeritColor.secondaryLabel)
                            )
                        VStack(alignment: .leading, spacing: 1) {
                            Text("All Children")
                                .font(MeritTypography.footnote)
                                .fontWeight(.bold)
                                .foregroundColor(isAllSelected ? MeritColor.accent : MeritColor.label)
                            Text("\(data.children.count) Profiles")
                                .font(MeritTypography.caption2)
                                .foregroundColor(MeritColor.secondaryLabel)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(isAllSelected ? MeritColor.accent.opacity(0.12) : MeritColor.secondaryFill)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium))
                    .overlay(
                        RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium)
                            .stroke(isAllSelected ? MeritColor.accent : Color.clear, lineWidth: 1.5)
                    )
                }
                .buttonStyle(.plain)

                // Per-child chips
                ForEach(data.children) { card in
                    let isSelected = data.selectedChildId == card.profile.childId
                    NavigationLink(destination: P12_ChildDetailView(childId: card.profile.childId)) {
                        HStack(spacing: 8) {
                            ChildAvatarBadge(
                                avatar: card.profile.avatar,
                                size: 30,
                                isOnline: card.isDeviceActive
                            )

                            VStack(alignment: .leading, spacing: 1) {
                                Text(card.profile.displayName)
                                    .font(MeritTypography.footnote)
                                    .fontWeight(.bold)
                                    .foregroundColor(isSelected ? MeritColor.accent : MeritColor.label)
                                Text(card.gradeStandard.isEmpty ? card.profile.ageBand.displayLabel : card.gradeStandard)
                                    .font(MeritTypography.caption2)
                                    .foregroundColor(MeritColor.secondaryLabel)
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(isSelected ? MeritColor.accent.opacity(0.12) : MeritColor.secondaryFill)
                        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium))
                        .overlay(
                            RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium)
                                .stroke(isSelected ? MeritColor.accent : Color.clear, lineWidth: 1.5)
                        )
                    }
                    .buttonStyle(.plain)
                }

                // Add Child chip
                if data.canAddChild {
                    Button(action: { showAddChildSheet = true }) {
                        HStack(spacing: 6) {
                            Image(systemName: "plus.circle.fill")
                                .font(.system(size: 18))
                                .foregroundColor(MeritColor.accent)
                            Text("Add")
                                .font(MeritTypography.footnote)
                                .fontWeight(.semibold)
                                .foregroundColor(MeritColor.accent)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(MeritColor.secondaryFill)
                        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - AI Insights & KPI Grid

    private func aiInsightCard(recommendation: AiRecommendation, analytics: LearningAnalyticsOverview) -> some View {
        VStack(alignment: .leading, spacing: MeritSpacing.small) {
            HStack {
                HStack(spacing: 4) {
                    Image(systemName: "sparkles")
                        .foregroundColor(MeritColor.accent)
                    Text("AI LEARNING INSIGHT")
                        .font(MeritTypography.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(MeritColor.accent)
                }
                Spacer()
                Text(recommendation.priorityLevel)
                    .font(MeritTypography.caption2)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(MeritColor.accent.opacity(0.15))
                    .foregroundColor(MeritColor.accent)
                    .clipShape(Capsule())
            }

            Text(recommendation.title)
                .font(MeritTypography.headline)
                .foregroundColor(MeritColor.label)

            Text(recommendation.description)
                .font(MeritTypography.caption1)
                .foregroundColor(MeritColor.secondaryLabel)

            // Milestone progress indicator
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("Next Goal")
                        .font(MeritTypography.caption2)
                        .foregroundColor(MeritColor.secondaryLabel)
                    Spacer()
                    Text(analytics.nextMilestoneLabel)
                        .font(MeritTypography.caption2)
                        .fontWeight(.semibold)
                        .foregroundColor(MeritColor.label)
                }

                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(MeritColor.tertiaryFill)
                            .frame(height: 6)
                        Capsule()
                            .fill(MeritColor.accent)
                            .frame(width: max(0, geo.size.width * CGFloat(analytics.nextMilestoneProgress)), height: 6)
                    }
                }
                .frame(height: 6)
            }
            .padding(.top, 4)
        }
        .padding(MeritSpacing.large)
        .background(MeritColor.secondaryFill)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge))
    }

    private func kpiGrid(analytics: LearningAnalyticsOverview) -> some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: MeritSpacing.medium) {
            kpiCard(icon: "brain.head.profile", value: analytics.formattedAiTime, label: "AI Training", subtitle: "Educational focus")
            kpiCard(icon: "checkmark.seal.fill", value: "\(analytics.modulesCompleted)", label: "Modules", subtitle: "Quizzes passed")
            kpiCard(icon: "chart.line.uptrend.xyaxis", value: "\(analytics.accuracyPercent)%", label: "Mastery Rate", subtitle: analytics.difficultyTrend)
            kpiCard(icon: "flame.fill", value: "\(analytics.currentStreakDays)d", label: "Learning Streak", subtitle: "Consecutive days")
        }
    }

    private func kpiCard(icon: String, value: String, label: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Image(systemName: icon)
                    .font(.system(size: 16))
                    .foregroundColor(MeritColor.accent)
                Spacer()
            }
            Text(value)
                .font(MeritTypography.title2)
                .foregroundColor(MeritColor.label)
            Text(label)
                .font(MeritTypography.caption1)
                .fontWeight(.bold)
                .foregroundColor(MeritColor.label)
            Text(subtitle)
                .font(MeritTypography.caption2)
                .foregroundColor(MeritColor.secondaryLabel)
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.secondaryFill)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium))
    }

    // MARK: - Trend Chart

    private func trendChartCard(data: DashboardUi) -> some View {
        VStack(alignment: .leading, spacing: MeritSpacing.medium) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("AI Training & Balance")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)
                    Text("Educational time vs screen activity")
                        .font(MeritTypography.caption2)
                        .foregroundColor(MeritColor.secondaryLabel)
                }
                Spacer()
                Picker("Range", selection: Binding(
                    get: { data.selectedTimeRange },
                    set: { viewModel.setTimeRange($0) }
                )) {
                    ForEach(DashboardTimeRange.allCases, id: \.self) { range in
                        Text(range.rawValue).tag(range)
                    }
                }
                .pickerStyle(.segmented)
                .frame(width: 170)
            }

            // Simple responsive bar visualization
            let maxMinutes = max(60, data.analytics.dailyTrend.map { max($0.totalScreenMinutes, $0.aiTrainingMinutes) }.max() ?? 60)
            let chartHeight: CGFloat = 80

            HStack(alignment: .bottom, spacing: 8) {
                ForEach(data.analytics.dailyTrend) { point in
                    VStack(spacing: 4) {
                        ZStack(alignment: .bottom) {
                            let totalHeight = point.totalScreenMinutes > 0
                                ? max(6, CGFloat(point.totalScreenMinutes) / CGFloat(maxMinutes) * chartHeight)
                                : CGFloat(2)
                            let aiHeight = point.aiTrainingMinutes > 0
                                ? min(totalHeight, max(4, CGFloat(point.aiTrainingMinutes) / CGFloat(maxMinutes) * chartHeight))
                                : CGFloat(0)

                            RoundedRectangle(cornerRadius: 4)
                                .fill(point.totalScreenMinutes > 0 ? MeritColor.tertiaryFill : MeritColor.tertiaryFill.opacity(0.3))
                                .frame(height: totalHeight)

                            if aiHeight > 0 {
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(MeritColor.accent)
                                    .frame(height: aiHeight)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: chartHeight, alignment: .bottom)

                        Text(point.dayLabel)
                            .font(MeritTypography.caption2)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                }
            }
            .frame(height: 104)
            .padding(.top, 4)

            // Legend
            HStack(spacing: 16) {
                HStack(spacing: 4) {
                    Circle().fill(MeritColor.accent).frame(width: 8, height: 8)
                    Text("AI Training").font(MeritTypography.caption2).foregroundColor(MeritColor.secondaryLabel)
                }
                HStack(spacing: 4) {
                    Circle().fill(MeritColor.tertiaryFill).frame(width: 8, height: 8)
                    Text("App Usage").font(MeritTypography.caption2).foregroundColor(MeritColor.secondaryLabel)
                }
            }

            Divider()
                .padding(.vertical, 2)

            if let onViewReports {
                Button(action: { onViewReports(data.selectedChildId) }) {
                    HStack {
                        Text("View Detailed Activity Reports")
                            .font(MeritTypography.footnote)
                            .fontWeight(.semibold)
                        Image(systemName: "arrow.right")
                            .font(.system(size: 11, weight: .bold))
                    }
                    .foregroundColor(MeritColor.accent)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                }
            } else {
                NavigationLink(destination: P17_ReportsView(childId: data.selectedChildId)) {
                    HStack {
                        Text("View Detailed Activity Reports")
                            .font(MeritTypography.footnote)
                            .fontWeight(.semibold)
                        Image(systemName: "arrow.right")
                            .font(.system(size: 11, weight: .bold))
                    }
                    .foregroundColor(MeritColor.accent)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                }
            }
        }
        .padding(MeritSpacing.large)
        .background(MeritColor.secondaryFill)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge))
    }

    // MARK: - Device Supervision

    private func deviceSupervisionSection(data: DashboardUi) -> some View {
        VStack(alignment: .leading, spacing: MeritSpacing.small) {
            HStack {
                Text("Device Supervision")
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)
                Spacer()
                Text("\(data.children.count) Protected")
                    .font(MeritTypography.caption1)
                    .foregroundColor(MeritColor.secondaryLabel)
            }

            ForEach(data.children) { child in
                deviceSupervisionCard(child: child)
            }
        }
    }

    private func deviceSupervisionCard(child: DashboardChildCard) -> some View {
        VStack(spacing: MeritSpacing.small) {
            NavigationLink(destination: P12_ChildDetailView(childId: child.profile.childId)) {
                HStack(spacing: MeritSpacing.medium) {
                    ChildAvatarBadge(
                        avatar: child.profile.avatar,
                        size: 46,
                        isOnline: child.isDeviceActive
                    )

                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(child.profile.displayName)
                                .font(MeritTypography.headline)
                                .foregroundColor(MeritColor.label)

                            if let model = child.deviceModel {
                                Text(model)
                                    .font(MeritTypography.caption2)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(MeritColor.tertiaryFill)
                                    .clipShape(Capsule())
                            }
                        }

                        Text("\(child.remainingMinutes)m remaining of \(child.dailyCeilingMinutes)m")
                            .font(MeritTypography.caption1)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.system(size: 14))
                        .foregroundColor(MeritColor.tertiaryLabel)
                }
            }
            .buttonStyle(.plain)

            // Progress bar
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(MeritColor.tertiaryFill)
                        .frame(height: 6)
                    let ratio = CGFloat(min(1.0, Double(child.todayMinutes) / Double(max(child.dailyCeilingMinutes, 1))))
                    RoundedRectangle(cornerRadius: 4)
                        .fill(child.isPaused ? MeritColor.destructive : (ratio > 0.85 ? MeritColor.warning : MeritColor.accent))
                        .frame(width: max(0, geo.size.width * ratio), height: 6)
                }
            }
            .frame(height: 6)

            // Quick Actions row
            HStack(spacing: MeritSpacing.small) {
                Button(action: { viewModel.togglePause(childId: child.profile.childId) }) {
                    HStack(spacing: 4) {
                        Image(systemName: child.isPaused ? "play.fill" : "pause.fill")
                            .font(.system(size: 11))
                        Text(child.isPaused ? "Resume" : "Pause")
                            .font(MeritTypography.caption1)
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(child.isPaused ? MeritColor.success.opacity(0.15) : MeritColor.destructive.opacity(0.15))
                    .foregroundColor(child.isPaused ? MeritColor.success : MeritColor.destructive)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusSmall))
                }

                Button(action: { viewModel.grantBonus(childId: child.profile.childId, bonusMinutes: 15) }) {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                            .font(.system(size: 11))
                        Text("+15m Bonus")
                            .font(MeritTypography.caption1)
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(MeritColor.accent.opacity(0.15))
                    .foregroundColor(MeritColor.accent)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusSmall))
                }

                Button(action: { onPairDevice(child.profile.childId) }) {
                    HStack(spacing: 4) {
                        Image(systemName: "qrcode")
                            .font(.system(size: 11))
                        Text("Pair")
                            .font(MeritTypography.caption1)
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(MeritColor.secondaryFill)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusSmall))
                }
            }
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.secondaryFill)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge))
    }

    // MARK: - Activity Feed

    private func activityFeedCard(events: [DashboardActivityEvent]) -> some View {
        VStack(alignment: .leading, spacing: MeritSpacing.small) {
            Text("Recent Learning Milestones")
                .font(MeritTypography.headline)
                .foregroundColor(MeritColor.label)

            VStack(spacing: 1) {
                ForEach(events) { event in
                    HStack(spacing: MeritSpacing.small) {
                        ZStack {
                            Circle()
                                .fill(event.eventType == .quizPass ? MeritColor.success.opacity(0.15) : MeritColor.accent.opacity(0.15))
                                .frame(width: 32, height: 32)
                            Image(systemName: event.eventType == .quizPass ? "checkmark" : (event.eventType == .limitReached ? "hourglass" : "shield.checkered"))
                                .font(.system(size: 13))
                                .foregroundColor(event.eventType == .quizPass ? MeritColor.success : MeritColor.accent)
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text(event.title)
                                .font(MeritTypography.caption1)
                                .fontWeight(.semibold)
                                .foregroundColor(MeritColor.label)
                            Text(event.subtitle)
                                .font(MeritTypography.caption2)
                                .foregroundColor(MeritColor.secondaryLabel)
                        }

                        Spacer()

                        if let badge = event.extraMinutesBadge {
                            Text(badge)
                                .font(MeritTypography.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(MeritColor.success)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(MeritColor.success.opacity(0.12))
                                .clipShape(Capsule())
                        } else {
                            Text(event.timeLabel)
                                .font(MeritTypography.caption2)
                                .foregroundColor(MeritColor.secondaryLabel)
                        }
                    }
                    .padding(.vertical, MeritSpacing.small)
                }
            }
        }
        .padding(MeritSpacing.large)
        .background(MeritColor.secondaryFill)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge))
    }
}
