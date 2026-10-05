import SwiftUI

/// Top-level navigation tab for parent portal.
public enum ParentNavTab: String, Hashable, Sendable, CaseIterable {
    case home = "Home"
    case reports = "Reports"
    case settings = "Settings"

    public var sfSymbol: String {
        switch self {
        case .home: return "house.fill"
        case .reports: return "chart.bar.xaxis"
        case .settings: return "gearshape.fill"
        }
    }
}

private struct ChildPairingSheetTarget: Identifiable {
    let id: String
}

/// Native iOS/iPadOS tab container hosting Home (Dashboard), Reports, and Settings.
public struct ParentMainTabView: View {
    @State private var selectedTab: ParentNavTab = .home
    @State private var reportsChildId: String? = nil
    @State private var pairingTarget: ChildPairingSheetTarget? = nil

    public let onSignOut: () -> Void
    public let onPairDevice: ((String) -> Void)?

    public init(
        initialTab: ParentNavTab = .home,
        onSignOut: @escaping () -> Void,
        onPairDevice: ((String) -> Void)? = nil
    ) {
        _selectedTab = State(initialValue: initialTab)
        self.onSignOut = onSignOut
        self.onPairDevice = onPairDevice
    }

    public var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                P11_DashboardView(
                    onSignOut: onSignOut,
                    onPairDevice: { childId in
                        pairingTarget = ChildPairingSheetTarget(id: childId)
                        onPairDevice?(childId)
                    },
                    onViewReports: { childId in
                        reportsChildId = childId
                        selectedTab = .reports
                    }
                )
            }
            .tabItem {
                Label(ParentNavTab.home.rawValue, systemImage: ParentNavTab.home.sfSymbol)
            }
            .tag(ParentNavTab.home)

            NavigationStack {
                P17_ReportsView(childId: reportsChildId)
            }
            .tabItem {
                Label(ParentNavTab.reports.rawValue, systemImage: ParentNavTab.reports.sfSymbol)
            }
            .tag(ParentNavTab.reports)

            NavigationStack {
                P19_AccountView(onSignOut: onSignOut)
            }
            .tabItem {
                Label(ParentNavTab.settings.rawValue, systemImage: ParentNavTab.settings.sfSymbol)
            }
            .tag(ParentNavTab.settings)
        }
        .tint(MeritColor.accent)
        .sheet(item: $pairingTarget) { target in
            P08_ParentPairingView(
                childId: target.id,
                onFinished: {
                    pairingTarget = nil
                },
                onBack: {
                    pairingTarget = nil
                }
            )
        }
    }
}
