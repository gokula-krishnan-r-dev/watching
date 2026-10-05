import SwiftUI
import Charts

/// Parent Reports & Analytics Screen (P17).
/// Displays daily screen time charts via Swift Charts, app allocations,
/// quiz performance, AI skill growth, and balanced habit scores.
public struct P17_ReportsView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var viewModel: ReportsViewModel

    private let initialChildId: String?

    public init(childId: String? = nil) {
        self.initialChildId = childId
        _viewModel = State(initialValue: ReportsViewModel())
    }

    public var body: some View {
        Group {
            switch viewModel.uiState {
            case .idle, .loading:
                LoadingView("Generating activity reports...")
            case .empty:
                EmptyStateView(
                    icon: "chart.bar.xaxis",
                    title: "No Activity Reports Yet",
                    description: "Screen time and learning insights will appear here once your child begins using their device.",
                    actionTitle: "Refresh"
                ) {
                    viewModel.refresh()
                }
            case .error(let error):
                ErrorView(error: error) {
                    viewModel.refresh()
                }
            case .success(let data):
                reportsContent(data: data)
            }
        }
        .navigationTitle("Activity Reports")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: { viewModel.refresh() }) {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }
        .onAppear {
            viewModel.load(preselectedChildId: initialChildId)
        }
    }

    // MARK: - Main Content Layout

    @ViewBuilder
    private func reportsContent(data: ReportsUi) -> some View {
        ScrollView {
            VStack(spacing: MeritSpacing.medium) {
                // Child Selector (if multiple children)
                if data.children.count > 1 {
                    childSelectorBar(data: data)
                }

                // Time Range Segmented Selector
                rangeSegmentedPicker(data: data)

                if horizontalSizeClass == .regular {
                    // iPad Adaptive 2-Column Grid
                    HStack(alignment: .top, spacing: MeritSpacing.large) {
                        VStack(spacing: MeritSpacing.medium) {
                            balancedScoreCard(data: data)
                            screenTimeChartCard(data: data)
                            appAllocationsCard(data: data)
                        }
                        .frame(maxWidth: .infinity)

                        VStack(spacing: MeritSpacing.medium) {
                            focusSessionsCard(data: data)
                            aiMasteryCard(data: data)
                            cooldownSummaryCard(data: data)
                        }
                        .frame(maxWidth: .infinity)
                    }
                } else {
                    // iPhone Single Column Scroll
                    balancedScoreCard(data: data)
                    metricsSummaryRow(data: data)
                    screenTimeChartCard(data: data)
                    appAllocationsCard(data: data)
                    focusSessionsCard(data: data)
                    aiMasteryCard(data: data)
                    cooldownSummaryCard(data: data)
                }
            }
            .padding(.horizontal, MeritSpacing.medium)
            .padding(.vertical, MeritSpacing.small)
        }
        .background(MeritColor.background.ignoresSafeArea())
        .refreshable {
            viewModel.refresh()
        }
    }

    // MARK: - Child Selector Bar

    private func childSelectorBar(data: ReportsUi) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: MeritSpacing.small) {
                ForEach(data.children) { child in
                    let isSelected = child.childId == data.selectedChildId
                    Button(action: { viewModel.selectChild(child.childId) }) {
                        HStack(spacing: 8) {
                            ChildAvatarBadge(avatar: child.avatar, size: 28)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(child.displayName)
                                    .font(MeritTypography.footnote)
                                    .fontWeight(.bold)
                                    .foregroundColor(isSelected ? MeritColor.accent : MeritColor.label)
                                Text(child.ageLabel)
                                    .font(MeritTypography.caption2)
                                    .foregroundColor(MeritColor.secondaryLabel)
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(isSelected ? MeritColor.accent.opacity(0.12) : MeritColor.cardBackground)
                        .clipShape(Capsule())
                        .overlay(
                            Capsule()
                                .stroke(isSelected ? MeritColor.accent : MeritColor.separator.opacity(0.3), lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 4)
        }
    }

    // MARK: - Time Range Picker

    private func rangeSegmentedPicker(data: ReportsUi) -> some View {
        Picker("Range", selection: Binding(
            get: { data.selectedDays },
            set: { viewModel.selectDays($0) }
        )) {
            Text("Today").tag(1)
            Text("7 Days").tag(7)
            Text("30 Days").tag(30)
        }
        .pickerStyle(.segmented)
        .padding(.horizontal, 4)
    }

    // MARK: - Balanced Score Card

    private func balancedScoreCard(data: ReportsUi) -> some View {
        CardContainer {
            VStack(alignment: .leading, spacing: MeritSpacing.small) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Balanced Habit Score")
                            .font(MeritTypography.caption1)
                            .foregroundColor(MeritColor.secondaryLabel)
                            .textCase(.uppercase)
                            .tracking(0.5)

                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            if let score = data.balancedScore {
                                Text("\(score)")
                                    .font(.system(size: 38, weight: .heavy, design: .rounded))
                                    .foregroundColor(scoreColor(score))
                                Text("/100")
                                    .font(MeritTypography.title3)
                                    .foregroundColor(MeritColor.secondaryLabel)
                            } else {
                                Text("—")
                                    .font(.system(size: 38, weight: .bold))
                                    .foregroundColor(MeritColor.secondaryLabel)
                            }

                            Spacer()

                            Text(data.balancedScoreLabel)
                                .font(MeritTypography.headline)
                                .fontWeight(.bold)
                                .foregroundColor(data.balancedScore != nil ? scoreColor(data.balancedScore!) : MeritColor.secondaryLabel)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(
                                    (data.balancedScore != nil ? scoreColor(data.balancedScore!) : MeritColor.secondaryLabel)
                                        .opacity(0.12)
                                )
                                .clipShape(Capsule())
                        }
                    }
                }

                Text(data.balancedScoreDescription)
                    .font(MeritTypography.subheadline)
                    .foregroundColor(MeritColor.label)

                Divider()
                    .padding(.vertical, 2)

                HStack(spacing: MeritSpacing.medium) {
                    Label("\(data.streakDays)-day active streak", systemImage: "flame.fill")
                        .font(MeritTypography.footnote)
                        .fontWeight(.semibold)
                        .foregroundColor(Color.orange)

                    Spacer()

                    if let eduPct = data.educationalPercent {
                        Label("\(eduPct)% educational", systemImage: "graduationcap.fill")
                            .font(MeritTypography.footnote)
                            .fontWeight(.semibold)
                            .foregroundColor(Color.green)
                    }
                }
            }
        }
    }

    // MARK: - Metrics Summary Row

    private func metricsSummaryRow(data: ReportsUi) -> some View {
        HStack(spacing: MeritSpacing.small) {
            metricBox(
                title: "Total Screen Time",
                value: data.totalScreenTimeFormatted,
                caption: "Period total",
                icon: "clock.fill",
                tint: MeritColor.accent
            )

            metricBox(
                title: "Daily Average",
                value: data.dailyAverageFormatted,
                caption: data.trendPercentage.map { "\($0 >= 0 ? "+" : "")\($0)% vs prior" } ?? "Steady",
                icon: "chart.line.uptrend.xyaxis",
                tint: (data.trendPercentage ?? 0) <= 0 ? Color.green : Color.orange
            )

            metricBox(
                title: "Learning Focus",
                value: data.totalAiFocusTimeFormatted,
                caption: "\(data.focusSessionsPassed) quizzes passed",
                icon: "brain.head.profile",
                tint: Color.purple
            )
        }
    }

    private func metricBox(title: String, value: String, caption: String, icon: String, tint: Color) -> some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(tint)

                Text(value)
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundColor(MeritColor.label)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)

                Text(title)
                    .font(MeritTypography.caption2)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .lineLimit(1)

                Text(caption)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(tint)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: - Screen Time Swift Chart Card

    private func screenTimeChartCard(data: ReportsUi) -> some View {
        CardContainer {
            VStack(alignment: .leading, spacing: MeritSpacing.medium) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Screen Time Distribution")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)
                        Text("Minutes used per calendar day")
                            .font(MeritTypography.caption1)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }

                    Spacer()

                    Text("Avg: \(data.dailyAverageFormatted)/day")
                        .font(MeritTypography.caption1)
                        .fontWeight(.semibold)
                        .foregroundColor(MeritColor.accent)
                }

                if data.report.daily.allSatisfy({ $0.minutes == 0 }) {
                    VStack(spacing: 8) {
                        Image(systemName: "chart.bar")
                            .font(.system(size: 32))
                            .foregroundColor(MeritColor.secondaryLabel)
                        Text("No screen time recorded in this period")
                            .font(MeritTypography.subheadline)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 180)
                } else {
                    Chart {
                        ForEach(data.report.daily) { point in
                            BarMark(
                                x: .value("Day", ReportsAggregator.shortDayLabel(day: point.day)),
                                y: .value("Minutes", point.minutes)
                            )
                            .foregroundStyle(
                                LinearGradient(
                                    colors: [MeritColor.accent, MeritColor.accent.opacity(0.65)],
                                    startPoint: .top,
                                    endPoint: .bottom
                                )
                            )
                            .cornerRadius(5)
                        }

                        if data.report.averageMinutesPerDay > 0 {
                            RuleMark(y: .value("Average", data.report.averageMinutesPerDay))
                                .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                                .foregroundStyle(Color.orange.opacity(0.85))
                                .annotation(position: .top, alignment: .trailing) {
                                    Text("Avg")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundColor(Color.orange)
                                        .padding(.horizontal, 4)
                                        .padding(.vertical, 1)
                                        .background(Color.orange.opacity(0.15))
                                        .clipShape(RoundedRectangle(cornerRadius: 3))
                                }
                        }
                    }
                    .chartYAxis {
                        AxisMarks(position: .leading) { value in
                            AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                                .foregroundStyle(MeritColor.separator.opacity(0.3))
                            AxisValueLabel {
                                if let minutes = value.as(Int.self) {
                                    Text("\(minutes)m")
                                        .font(.system(size: 10))
                                        .foregroundColor(MeritColor.secondaryLabel)
                                }
                            }
                        }
                    }
                    .chartXAxis {
                        AxisMarks { value in
                            AxisValueLabel {
                                if let label = value.as(String.self) {
                                    Text(label)
                                        .font(.system(size: 10, weight: .medium))
                                        .foregroundColor(MeritColor.secondaryLabel)
                                }
                            }
                        }
                    }
                    .frame(height: 190)
                }
            }
        }
    }

    // MARK: - App Allocations Card

    private func appAllocationsCard(data: ReportsUi) -> some View {
        CardContainer {
            VStack(alignment: .leading, spacing: MeritSpacing.medium) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("App Activity Breakdown")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)
                        Text("Top applications by screen time")
                            .font(MeritTypography.caption1)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }

                    Spacer()

                    if let edu = data.educationalPercent {
                        Text("\(edu)% Educational")
                            .font(MeritTypography.caption2)
                            .fontWeight(.bold)
                            .foregroundColor(Color.green)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.green.opacity(0.12))
                            .clipShape(Capsule())
                    }
                }

                if data.appAllocations.isEmpty {
                    Text("No per-app usage reported yet")
                        .font(MeritTypography.subheadline)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .padding(.vertical, 12)
                } else {
                    // Category Distribution Multi-Color Progress Bar
                    categoryDistributionBar(data: data)

                    // Top App List
                    VStack(spacing: 8) {
                        ForEach(data.appAllocations) { app in
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(categoryColor(app.categoryType))
                                    .frame(width: 10, height: 10)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(app.displayName)
                                        .font(MeritTypography.subheadline)
                                        .fontWeight(.semibold)
                                        .foregroundColor(MeritColor.label)
                                        .lineLimit(1)
                                    Text(app.categoryLabel)
                                        .font(MeritTypography.caption2)
                                        .foregroundColor(MeritColor.secondaryLabel)
                                }

                                Spacer()

                                VStack(alignment: .trailing, spacing: 2) {
                                    Text("\(app.minutes)m")
                                        .font(MeritTypography.subheadline)
                                        .fontWeight(.bold)
                                        .foregroundColor(MeritColor.label)
                                    Text("\(app.percentage)%")
                                        .font(MeritTypography.caption2)
                                        .foregroundColor(MeritColor.secondaryLabel)
                                }
                            }
                            .padding(.vertical, 4)

                            if app.id != data.appAllocations.last?.id {
                                Divider()
                                    .opacity(0.5)
                            }
                        }
                    }
                }
            }
        }
    }

    private func categoryDistributionBar(data: ReportsUi) -> some View {
        GeometryReader { geo in
            HStack(spacing: 2) {
                ForEach(data.appAllocations) { app in
                    let width = max(4, geo.size.width * CGFloat(app.percentage) / 100.0)
                    RoundedRectangle(cornerRadius: 3)
                        .fill(categoryColor(app.categoryType))
                        .frame(width: width)
                }
            }
        }
        .frame(height: 8)
        .clipShape(RoundedRectangle(cornerRadius: 4))
    }

    // MARK: - Focus Sessions & Quiz Results Card

    private func focusSessionsCard(data: ReportsUi) -> some View {
        CardContainer {
            VStack(alignment: .leading, spacing: MeritSpacing.medium) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Quiz Breaks & Learning Focus")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)
                        Text("Adaptive quiz interrupts during app sessions")
                            .font(MeritTypography.caption1)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }

                    Spacer()

                    if let passPct = data.quizPassPercent {
                        Text("\(passPct)% Pass Rate")
                            .font(MeritTypography.caption1)
                            .fontWeight(.bold)
                            .foregroundColor(passPct >= 75 ? Color.green : Color.orange)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background((passPct >= 75 ? Color.green : Color.orange).opacity(0.12))
                            .clipShape(Capsule())
                    }
                }

                // Summary Pill
                HStack(spacing: MeritSpacing.medium) {
                    Label("\(data.focusSessionsPassed)/\(data.focusSessionCount) Passed", systemImage: "checkmark.seal.fill")
                        .font(MeritTypography.footnote)
                        .foregroundColor(Color.green)

                    Spacer()

                    Label("+\(data.extraMinutesEarned)m Earned", systemImage: "gift.fill")
                        .font(MeritTypography.footnote)
                        .foregroundColor(MeritColor.accent)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(MeritColor.secondaryFill)
                .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusSmall))

                if data.focusSessions.isEmpty {
                    Text("No quiz attempts in this period yet")
                        .font(MeritTypography.subheadline)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .padding(.vertical, 8)
                } else {
                    VStack(spacing: 8) {
                        ForEach(data.focusSessions) { session in
                            HStack(spacing: 12) {
                                Image(systemName: session.passed ? "checkmark.circle.fill" : "xmark.circle.fill")
                                    .font(.system(size: 20))
                                    .foregroundColor(session.passed ? Color.green : Color.orange)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(session.title)
                                        .font(MeritTypography.subheadline)
                                        .fontWeight(.semibold)
                                        .foregroundColor(MeritColor.label)
                                    Text("Score: \(session.scoreLabel)")
                                        .font(MeritTypography.caption2)
                                        .foregroundColor(MeritColor.secondaryLabel)
                                }

                                Spacer()

                                if session.passed && session.extraMinutes > 0 {
                                    Text("+\(session.extraMinutes)m")
                                        .font(MeritTypography.footnote)
                                        .fontWeight(.bold)
                                        .foregroundColor(MeritColor.accent)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 3)
                                        .background(MeritColor.accent.opacity(0.12))
                                        .clipShape(Capsule())
                                }
                            }
                            .padding(.vertical, 2)
                        }
                    }
                }
            }
        }
    }

    // MARK: - AI Mastery & Skill Growth Card

    private func aiMasteryCard(data: ReportsUi) -> some View {
        CardContainer {
            VStack(alignment: .leading, spacing: MeritSpacing.medium) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("AI Topic Mastery & Concepts")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)
                    Text("Real-time curriculum progression")
                        .font(MeritTypography.caption1)
                        .foregroundColor(MeritColor.secondaryLabel)
                }

                if data.aiTopics.isEmpty {
                    Text("Topic mastery data will build as learning sessions complete")
                        .font(MeritTypography.subheadline)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .padding(.vertical, 8)
                } else {
                    VStack(spacing: 10) {
                        ForEach(data.aiTopics) { topic in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(topic.topic)
                                        .font(MeritTypography.subheadline)
                                        .fontWeight(.semibold)
                                        .foregroundColor(MeritColor.label)

                                    Spacer()

                                    Text(topic.tierLabel)
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(topic.weak ? Color.orange : MeritColor.accent)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background((topic.weak ? Color.orange : MeritColor.accent).opacity(0.12))
                                        .clipShape(Capsule())
                                }

                                Text(topic.levelLabel)
                                    .font(MeritTypography.caption2)
                                    .foregroundColor(MeritColor.secondaryLabel)
                            }
                        }
                    }
                }

                // Strengths & Growth Areas
                if !data.demonstratedStrengths.isEmpty || !data.growthRecommended.isEmpty {
                    Divider()
                        .padding(.vertical, 2)

                    if !data.demonstratedStrengths.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Demonstrated Strengths")
                                .font(MeritTypography.caption1)
                                .fontWeight(.bold)
                                .foregroundColor(Color.green)

                            ForEach(data.demonstratedStrengths, id: \.self) { strength in
                                HStack(spacing: 6) {
                                    Image(systemName: "star.fill")
                                        .font(.system(size: 10))
                                        .foregroundColor(Color.green)
                                    Text(strength)
                                        .font(MeritTypography.caption1)
                                        .foregroundColor(MeritColor.label)
                                }
                            }
                        }
                    }

                    if !data.growthRecommended.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Growth & Practice Focus")
                                .font(MeritTypography.caption1)
                                .fontWeight(.bold)
                                .foregroundColor(Color.orange)

                            ForEach(data.growthRecommended, id: \.self) { hint in
                                HStack(spacing: 6) {
                                    Image(systemName: "lightbulb.fill")
                                        .font(.system(size: 10))
                                        .foregroundColor(Color.orange)
                                    Text(hint)
                                        .font(MeritTypography.caption1)
                                        .foregroundColor(MeritColor.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: - Cooldown & Rest Summary Card

    private func cooldownSummaryCard(data: ReportsUi) -> some View {
        CardContainer {
            VStack(alignment: .leading, spacing: MeritSpacing.small) {
                HStack {
                    Image(systemName: "pause.circle.fill")
                        .font(.system(size: 18))
                        .foregroundColor(Color.orange)
                    Text("Calm Cooldown History")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)
                }

                Text("\(data.cooldownIncidentsCount) calm resting window\(data.cooldownIncidentsCount == 1 ? "" : "s") triggered after quiz interruptions. Average cooldown rest: \(data.avgCooldownRestMinutes) minutes.")
                    .font(MeritTypography.subheadline)
                    .foregroundColor(MeritColor.secondaryLabel)
            }
        }
    }

    // MARK: - Helpers

    private func scoreColor(_ score: Int) -> Color {
        switch score {
        case 80...: return Color.green
        case 65..<80: return Color.blue
        case 45..<65: return Color.orange
        default: return Color.red
        }
    }

    private func categoryColor(_ category: AppCategoryType) -> Color {
        switch category {
        case .learn: return Color.green
        case .media: return Color.purple
        case .language: return Color.blue
        case .logic: return Color.indigo
        case .general: return Color.gray
        }
    }
}

private struct CardContainer<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: MeritSpacing.medium) {
            content
        }
        .padding(MeritSpacing.large)
        .background(MeritColor.secondaryFill)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge))
    }
}

