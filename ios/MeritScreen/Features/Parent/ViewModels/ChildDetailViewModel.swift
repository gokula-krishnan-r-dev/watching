import SwiftUI
import Observation

public enum DeviceConnectionStatus: Sendable, Equatable {
    case connected
    case disconnected
    case paused
    case unpaired

    public var displayLabel: String {
        switch self {
        case .connected: return "Connected"
        case .disconnected: return "Offline"
        case .paused: return "Paused"
        case .unpaired: return "Unpaired"
        }
    }
}

public struct ChildDetailUi: Sendable, Equatable {
    public var profile: FamilyChildProfile
    public var policy: ChildPolicy
    public var devices: [DeviceSummary]
    public var primaryDevice: DeviceSummary?
    public var connectionStatus: DeviceConnectionStatus
    public var connectionStatusLabel: String
    public var deviceModelAndOs: String
    public var batteryPercent: Int?
    public var lastSyncedLabel: String
    public var todayMinutes: Int
    public var dailyCeilingMinutes: Int
    public var remainingMinutes: Int
    public var isPaused: Bool
    public var quizScorePercent: Int
    public var quizScoreFraction: String
    public var curriculumLevelText: String
    public var allowedAppCount: Int
    public var stickersUnlockedTotal: Int
    public var explorerLevel: Int
    public var bonusToastMessage: String?

    public init(
        profile: FamilyChildProfile,
        policy: ChildPolicy,
        devices: [DeviceSummary] = [],
        primaryDevice: DeviceSummary? = nil,
        connectionStatus: DeviceConnectionStatus = .connected,
        connectionStatusLabel: String = "Connected",
        deviceModelAndOs: String = "Apple Device",
        batteryPercent: Int? = nil,
        lastSyncedLabel: String = "Just now",
        todayMinutes: Int = 0,
        dailyCeilingMinutes: Int = 120,
        remainingMinutes: Int = 120,
        isPaused: Bool = false,
        quizScorePercent: Int = 0,
        quizScoreFraction: String = "—",
        curriculumLevelText: String = "Level 1 · Fundamentals",
        allowedAppCount: Int = 0,
        stickersUnlockedTotal: Int = 0,
        explorerLevel: Int = 1,
        bonusToastMessage: String? = nil
    ) {
        self.profile = profile
        self.policy = policy
        self.devices = devices
        self.primaryDevice = primaryDevice
        self.connectionStatus = connectionStatus
        self.connectionStatusLabel = connectionStatusLabel
        self.deviceModelAndOs = deviceModelAndOs
        self.batteryPercent = batteryPercent
        self.lastSyncedLabel = lastSyncedLabel
        self.todayMinutes = todayMinutes
        self.dailyCeilingMinutes = dailyCeilingMinutes
        self.remainingMinutes = remainingMinutes
        self.isPaused = isPaused
        self.quizScorePercent = quizScorePercent
        self.quizScoreFraction = quizScoreFraction
        self.curriculumLevelText = curriculumLevelText
        self.allowedAppCount = allowedAppCount
        self.stickersUnlockedTotal = stickersUnlockedTotal
        self.explorerLevel = explorerLevel
        self.bonusToastMessage = bonusToastMessage
    }
}

@Observable
@MainActor
public final class ChildDetailViewModel {
    public var uiState: UiState<ChildDetailUi> = .loading
    public var isDeleting: Bool = false
    public var isDeleted: Bool = false
    public var isSavingProfile: Bool = false
    public var profileSaved: Bool = false
    public var actionError: String? = nil

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
    }

    public func load() {
        Task {
            await fetchDetail()
        }
    }

    public func refresh() {
        Task {
            await fetchDetail()
        }
    }

    public func togglePause() {
        guard let familyId = parentSessionRepo.current()?.familyId ?? Optional("sample_family") else { return }
        guard case .success(var current) = uiState else { return }

        let newPaused = !current.isPaused
        current.isPaused = newPaused
        current.policy.paused = newPaused
        current.connectionStatus = newPaused ? .paused : .connected
        current.connectionStatusLabel = newPaused ? "Paused by Parent" : "Connected"
        uiState = .success(current)

        Task {
            do {
                try await parentControlStore.setChildPaused(familyId: familyId, childId: childId, paused: newPaused)
            } catch {
                actionError = "Failed to update pause state."
                await fetchDetail()
            }
        }
    }

    public func grantBonus(minutes: Int = AppConfig.bonusTimeMinutesDefault) {
        guard let familyId = parentSessionRepo.current()?.familyId ?? Optional("sample_family") else { return }
        guard case .success(var current) = uiState else { return }

        current.dailyCeilingMinutes += minutes
        current.remainingMinutes += minutes
        current.bonusToastMessage = "✨ \(minutes) minutes added to \(current.profile.displayName)'s daily ceiling!"
        uiState = .success(current)

        Task {
            do {
                try await parentControlStore.addBonusTime(familyId: familyId, childId: childId, bonusMinutes: minutes)
            } catch {
                actionError = "Failed to add bonus minutes."
                await fetchDetail()
            }
        }
    }

    public func dismissBonusToast() {
        if case .success(var current) = uiState {
            current.bonusToastMessage = nil
            uiState = .success(current)
        }
    }

    public func updateProfile(name: String, ageBand: AgeBand, avatar: AvatarPreset, language: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            actionError = "Enter your child's name."
            return
        }

        guard let familyId = parentSessionRepo.current()?.familyId ?? Optional("sample_family") else { return }

        isSavingProfile = true
        actionError = nil

        Task {
            do {
                let updated = try await parentControlStore.updateChild(
                    familyId: familyId,
                    childId: childId,
                    child: FamilyDraftChild(
                        localId: childId,
                        name: trimmed,
                        ageBand: ageBand,
                        avatar: avatar,
                        language: language
                    )
                )
                if case .success(var current) = uiState {
                    current.profile = updated
                    uiState = .success(current)
                }
                profileSaved = true
            } catch {
                actionError = error.localizedDescription
            }
            isSavingProfile = false
        }
    }

    public func deleteChild(confirmationName: String) {
        guard case .success(let current) = uiState else { return }
        if confirmationName.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() != current.profile.displayName.lowercased() {
            actionError = "Type \(current.profile.displayName) to confirm."
            return
        }

        guard let familyId = parentSessionRepo.current()?.familyId ?? Optional("sample_family") else { return }

        isDeleting = true
        actionError = nil

        Task {
            do {
                try await parentControlStore.deleteChild(familyId: familyId, childId: childId)
                isDeleted = true
            } catch {
                actionError = error.localizedDescription
            }
            isDeleting = false
        }
    }

    public func revokeDevice(deviceId: String) {
        guard let familyId = parentSessionRepo.current()?.familyId ?? Optional("sample_family") else { return }

        Task {
            do {
                try await parentControlStore.setDeviceRevoked(familyId: familyId, childId: childId, deviceId: deviceId, revoked: true)
                await fetchDetail()
            } catch {
                actionError = "Failed to disconnect device."
            }
        }
    }

    private func fetchDetail() async {
        let familyId = parentSessionRepo.current()?.familyId ?? "sample_family"

        do {
            let children = try await parentControlStore.listChildren(familyId: familyId)
            guard let profile = children.first(where: { $0.childId == childId }) ?? children.first else {
                uiState = .error(AppError.notFound("We couldn't find that child profile."))
                return
            }

            let policy = try await parentControlStore.getPolicy(familyId: familyId, childId: profile.childId)
            let devices = try await parentControlStore.listDevices(familyId: familyId, childId: profile.childId).filter { !$0.revoked }
            let rules = try await parentControlStore.listAppRules(familyId: familyId, childId: profile.childId)
            let usage = try await parentControlStore.listUsageDays(familyId: familyId, childId: profile.childId, limit: 1)
            let quizzes = try await parentControlStore.listQuizAttempts(familyId: familyId, childId: profile.childId, limit: 10)
            let skills = try await parentControlStore.getSkillState(familyId: familyId, childId: profile.childId)

            let ceiling = policy.dailyCeilingMinutes ?? AppConfig.defaultDailyCeilingMinutes
            let used = usage.first?.minutesUsed ?? 0
            let remaining = max(0, ceiling - used)
            let primary = devices.first
            let isOnline = primary?.lastSeenAtEpochMs.map {
                (Int64(Date().timeIntervalSince1970 * 1000) - $0) < Int64(AppConfig.deviceOnlineThresholdMinutes * 60_000)
            } ?? false

            let status: DeviceConnectionStatus
            let statusLabel: String
            if policy.paused {
                status = .paused
                statusLabel = "Paused by Parent"
            } else if primary == nil {
                status = .unpaired
                statusLabel = "No Device Paired"
            } else if isOnline {
                status = .connected
                statusLabel = "Online & Supervised"
            } else {
                status = .disconnected
                statusLabel = "Offline (Local Rules Enforced)"
            }

            let lastQuiz = quizzes.first
            let scoreFraction = lastQuiz.map { "\($0.score)/\($0.total)" } ?? "—"
            let scorePercent = lastQuiz.map { $0.total > 0 ? ($0.score * 100) / $0.total : 0 } ?? 0
            let maxLevel = skills.map(\.level).max() ?? 1
            let passedQuizzes = quizzes.filter(\.passed).count

            let lastSyncedLabel: String = {
                guard let epoch = primary?.lastSeenAtEpochMs else { return isOnline ? "Just now" : "Never" }
                let diffSec = max(0, Int((Int64(Date().timeIntervalSince1970 * 1000) - epoch) / 1000))
                if diffSec < 60 { return "Just now" }
                let mins = diffSec / 60
                if mins < 60 { return "\(mins)m ago" }
                let hours = mins / 60
                if hours < 24 { return "\(hours)h ago" }
                return "\(hours / 24)d ago"
            }()

            let detail = ChildDetailUi(
                profile: profile,
                policy: policy,
                devices: devices,
                primaryDevice: primary,
                connectionStatus: status,
                connectionStatusLabel: statusLabel,
                deviceModelAndOs: primary != nil ? "\(primary!.model) • \(primary!.osVersion)" : "No Device Paired",
                batteryPercent: primary?.batteryPercent,
                lastSyncedLabel: lastSyncedLabel,
                todayMinutes: used,
                dailyCeilingMinutes: ceiling,
                remainingMinutes: remaining,
                isPaused: policy.paused,
                quizScorePercent: scorePercent,
                quizScoreFraction: scoreFraction,
                curriculumLevelText: "Level \(maxLevel) · \(profile.ageBand.displayLabel)",
                allowedAppCount: rules.filter(\.allowed).count,
                stickersUnlockedTotal: passedQuizzes,
                explorerLevel: maxLevel,
                bonusToastMessage: nil
            )
            uiState = .success(detail)
        } catch {
            uiState = .error(AppError.unknown("Could not load child details."))
        }
    }
}
