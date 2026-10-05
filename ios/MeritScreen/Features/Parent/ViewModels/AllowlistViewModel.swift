import SwiftUI
import Observation
import FamilyControls

@Observable
@MainActor
public final class AllowlistViewModel {
    public var rules: [AppRule] = []
    public var installedApps: [InstalledAppSummary] = []
    public var activitySelection: FamilyActivitySelection = ScreenTimeSharedStore.shared.getActivitySelection()
    public var isLoading: Bool = false
    public var isRefreshing: Bool = false
    public var isSaving: Bool = false
    public var error: String? = nil
    public var searchQuery: String = ""

    private let childId: String
    private let parentSessionRepo: ParentSessionRepository
    private let parentControlStore: ParentControlStoreProtocol

    public init(
        childId: String,
        parentSessionRepo: ParentSessionRepository = .shared,
        parentControlStore: ParentControlStoreProtocol = FirestoreParentControlStore.shared
    ) {
        self.childId = childId
        self.parentSessionRepo = parentSessionRepo
        self.parentControlStore = parentControlStore
        load()
    }

    public var filteredRules: [AppRule] {
        if searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return rules
        }
        let query = searchQuery.lowercased()
        return rules.filter {
            $0.displayName.lowercased().contains(query) ||
            $0.packageOrBundleId.lowercased().contains(query)
        }
    }

    public var pickableApps: [InstalledAppSummary] {
        let existing = Set(rules.map { $0.packageOrBundleId.lowercased() })
        return installedApps.filter { !existing.contains($0.packageName.lowercased()) }
    }

    public func load() {
        isLoading = true
        error = nil
        Task {
            await fetchData()
            isLoading = false
        }
    }

    public func refresh() {
        isRefreshing = true
        Task {
            await fetchData()
            isRefreshing = false
        }
    }

    public func toggleAllowed(rule: AppRule) {
        guard let familyId = parentSessionRepo.current()?.familyId ?? Optional("sample_family") else { return }

        var updated = rule
        updated.allowed.toggle()

        if let idx = rules.firstIndex(where: { $0.appId == rule.appId }) {
            rules[idx] = updated
        }

        Task {
            do {
                try await parentControlStore.upsertAppRule(familyId: familyId, childId: childId, rule: updated)
            } catch {
                self.error = "Could not update app rule."
                await fetchData()
            }
        }
    }

    public func toggleInstalledApp(app: InstalledAppSummary, allowed: Bool) {
        guard let familyId = parentSessionRepo.current()?.familyId ?? Optional("sample_family") else { return }

        let rule = AppRule(
            appId: "rule_" + app.packageName.replacingOccurrences(of: ".", with: "_"),
            packageOrBundleId: app.packageName,
            displayName: app.appName,
            allowed: allowed
        )

        rules.append(rule)

        Task {
            do {
                try await parentControlStore.upsertAppRule(familyId: familyId, childId: childId, rule: rule)
            } catch {
                self.error = "Could not add app rule."
                await fetchData()
            }
        }
    }

    public func addCustomApp(packageOrBundleId: String, displayName: String) {
        let trimmedId = packageOrBundleId.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !trimmedId.isEmpty else {
            error = "Enter an app Bundle ID (e.g. com.duolingo.DuolingoMobile)."
            return
        }

        guard let familyId = parentSessionRepo.current()?.familyId ?? Optional("sample_family") else { return }

        let rule = AppRule(
            appId: "rule_" + trimmedId.replacingOccurrences(of: ".", with: "_"),
            packageOrBundleId: trimmedId,
            displayName: trimmedName.isEmpty ? trimmedId : trimmedName,
            allowed: true
        )

        rules.append(rule)

        Task {
            do {
                try await parentControlStore.upsertAppRule(familyId: familyId, childId: childId, rule: rule)
            } catch {
                self.error = "Failed to save custom app rule."
                await fetchData()
            }
        }
    }

    public func deleteRule(rule: AppRule) {
        guard let familyId = parentSessionRepo.current()?.familyId ?? Optional("sample_family") else { return }

        rules.removeAll { $0.appId == rule.appId }

        Task {
            do {
                try await parentControlStore.deleteAppRule(familyId: familyId, childId: childId, appId: rule.appId)
            } catch {
                self.error = "Failed to delete app rule."
                await fetchData()
            }
        }
    }

    public func updateActivitySelection(_ newSelection: FamilyActivitySelection) {
        self.activitySelection = newSelection
        ScreenTimeSharedStore.shared.saveActivitySelection(newSelection)
    }

    private func fetchData() async {
        let familyId = parentSessionRepo.current()?.familyId ?? "sample_family"
        do {
            let r = try await parentControlStore.listAppRules(familyId: familyId, childId: childId)
            let i = try await parentControlStore.listInstalledApps(familyId: familyId, childId: childId)
            rules = r
            installedApps = i
        } catch {
            self.error = error.localizedDescription
        }
    }
}
