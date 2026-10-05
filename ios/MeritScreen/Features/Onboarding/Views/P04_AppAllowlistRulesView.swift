import SwiftUI

/// P04: App Allowlist Configuration Screen.
/// Mirrors `com.meritscreen.feature.onboarding.ui.OnboardingAllowlistScreen` in Android.
public struct P04_AppAllowlistRulesView: View {
    @Binding public var rules: [OnboardingAppRule]
    public let ctaTitle: String
    public let ctaIcon: String
    public let stepPillLabel: String
    public let onContinue: ([OnboardingAppRule]) -> Void
    public let onBack: () -> Void

    @State private var searchQuery: String = ""
    @State private var selectedCategory: OnboardingAppCategory? = nil

    private var filteredRules: [OnboardingAppRule] {
        rules.filter { rule in
            let matchesCategory = selectedCategory == nil || rule.category == selectedCategory
            let matchesSearch = searchQuery.trimmingCharacters(in: .whitespaces).isEmpty ||
                rule.name.localizedCaseInsensitiveContains(searchQuery) ||
                rule.category.rawValue.localizedCaseInsensitiveContains(searchQuery)
            return matchesCategory && matchesSearch
        }
    }

    public init(
        rules: Binding<[OnboardingAppRule]>,
        ctaTitle: String = "Continue to Parent PIN",
        ctaIcon: String = "lock.fill",
        stepPillLabel: String = "Step 4 of 5 • App Rules",
        onContinue: @escaping ([OnboardingAppRule]) -> Void,
        onBack: @escaping () -> Void
    ) {
        self._rules = rules
        self.ctaTitle = ctaTitle
        self.ctaIcon = ctaIcon
        self.stepPillLabel = stepPillLabel
        self.onContinue = onContinue
        self.onBack = onBack
    }

    public var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: MeritSpacing.large) {
                    // Top Bar
                    HStack {
                        Button(action: onBack) {
                            HStack(spacing: 4) {
                                Image(systemName: "chevron.left")
                                Text("Back")
                            }
                            .font(MeritTypography.subheadline)
                            .foregroundColor(MeritColor.accent)
                        }

                        Spacer()

                        HStack(spacing: 6) {
                            Image(systemName: "square.grid.2x2.fill")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(MeritColor.accent)
                            Text(stepPillLabel)
                                .font(MeritTypography.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(MeritColor.accent)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(MeritColor.accent.opacity(0.12))
                        .clipShape(Capsule())
                    }
                    .padding(.top, MeritSpacing.small)

                    // Header
                    VStack(alignment: .leading, spacing: MeritSpacing.xSmall) {
                        Text("App Allowlist Rules")
                            .font(MeritTypography.largeTitle)
                            .fontWeight(.bold)
                            .foregroundColor(MeritColor.label)

                        Text("Choose which apps are always allowed, and which require a solved quiz block.")
                            .font(MeritTypography.body)
                            .foregroundColor(MeritColor.secondaryLabel)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    // Preset Quick Pickers
                    VStack(alignment: .leading, spacing: MeritSpacing.small) {
                        Text("Quick Rule Presets")
                            .font(MeritTypography.headline)
                            .foregroundColor(MeritColor.label)

                        HStack(spacing: 8) {
                            presetButton(title: "Educational", icon: "book.closed.fill") {
                                applyPreset(.educationalOnly)
                            }
                            presetButton(title: "Balanced", icon: "scale.3d") {
                                applyPreset(.balanced)
                            }
                            presetButton(title: "Strict", icon: "lock.shield.fill") {
                                applyPreset(.strict)
                            }
                        }
                    }

                    // Search Box
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(MeritColor.secondaryLabel)

                        TextField("Search approved or blocked apps...", text: $searchQuery)
                            .font(MeritTypography.body)

                        if !searchQuery.isEmpty {
                            Button(action: { searchQuery = "" }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(MeritColor.secondaryLabel)
                            }
                        }
                    }
                    .padding(MeritSpacing.medium)
                    .background(MeritColor.secondaryBackground)
                    .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))

                    // Category Filter Pills
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            filterPill(label: "All", isSelected: selectedCategory == nil) {
                                selectedCategory = nil
                            }
                            ForEach(OnboardingAppCategory.allCases, id: \.self) { cat in
                                filterPill(label: cat.rawValue, isSelected: selectedCategory == cat) {
                                    selectedCategory = cat
                                }
                            }
                        }
                    }

                    // App Rules List
                    VStack(spacing: 10) {
                        ForEach(filteredRules) { rule in
                            appRuleRow(rule)
                        }
                    }

                    Spacer(minLength: MeritSpacing.medium)
                }
                .padding(.horizontal, MeritSpacing.large)
                .responsiveContainer(maxWidth: 540)
            }

            // Bottom Sticky Action Button
            VStack(spacing: 0) {
                Divider()
                MeritButton(
                    ctaTitle,
                    icon: ctaIcon,
                    style: .primary
                ) {
                    onContinue(rules)
                }
                .padding(.horizontal, MeritSpacing.large)
                .padding(.vertical, MeritSpacing.medium)
                .responsiveContainer(maxWidth: 540)
            }
            .background(MeritColor.background)
        }
        .background(MeritColor.background.ignoresSafeArea())
    }

    private func appRuleRow(_ rule: OnboardingAppRule) -> some View {
        let index = rules.firstIndex(where: { $0.id == rule.id })

        return HStack(spacing: MeritSpacing.medium) {
            ZStack {
                Circle()
                    .fill(rule.category == .educational ? MeritColor.accent.opacity(0.15) : MeritColor.fill)
                    .frame(width: 44, height: 44)

                Image(systemName: rule.iconName)
                    .font(.system(size: 18))
                    .foregroundColor(rule.category == .educational ? MeritColor.accent : MeritColor.label)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(rule.name)
                    .font(MeritTypography.headline)
                    .foregroundColor(MeritColor.label)

                HStack(spacing: 6) {
                    Text(rule.category.rawValue)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(MeritColor.secondaryLabel)

                    if rule.isSystemLocked {
                        Text("• Always Allowed")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(MeritColor.accent)
                    }
                }
            }

            Spacer()

            if rule.isSystemLocked {
                Image(systemName: "lock.fill")
                    .font(.system(size: 14))
                    .foregroundColor(MeritColor.secondaryLabel)
                    .padding(.trailing, 8)
            } else if let idx = index {
                Toggle("", isOn: $rules[idx].isAllowed)
                    .labelsHidden()
                    .tint(MeritColor.accent)
            }
        }
        .padding(MeritSpacing.medium)
        .background(MeritColor.secondaryBackground)
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusMedium, style: .continuous))
    }

    private func presetButton(title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: {
            action()
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 12))
                Text(title)
                    .font(MeritTypography.caption)
                    .fontWeight(.semibold)
            }
            .foregroundColor(MeritColor.accent)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(MeritColor.accent.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func filterPill(label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: {
            action()
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }) {
            Text(label)
                .font(MeritTypography.caption)
                .fontWeight(isSelected ? .bold : .regular)
                .foregroundColor(isSelected ? .white : MeritColor.label)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(isSelected ? MeritColor.accent : MeritColor.secondaryBackground)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private enum PresetType {
        case educationalOnly
        case balanced
        case strict
    }

    private func applyPreset(_ type: PresetType) {
        for i in 0..<rules.count {
            if rules[i].isSystemLocked { continue }
            switch type {
            case .educationalOnly:
                rules[i].isAllowed = (rules[i].category == .educational)
            case .balanced:
                rules[i].isAllowed = true
            case .strict:
                rules[i].isAllowed = false
            }
        }
    }
}
