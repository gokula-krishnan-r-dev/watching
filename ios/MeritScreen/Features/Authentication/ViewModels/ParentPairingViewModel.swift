import Foundation
import Observation

@MainActor
@Observable
public final class ParentPairingViewModel {
    public let childId: String
    public var offer: PairingTokenInfo?
    public var child: FamilyChildProfile?
    public var isLoading: Bool = true
    public var error: AppError?
    public var devicePaired: Bool = false
    public var pairedDevice: FamilyDeviceSummary?
    public var remainingSeconds: Int = 600

    private let pairingClient: PairingClientProtocol
    private let familyStore: FamilyStoreProtocol
    private let sessionRepository: ParentSessionRepository
    private let networkMonitor: NetworkMonitorProtocol
    @ObservationIgnored private var countdownTask: Task<Void, Never>?
    @ObservationIgnored private var pollTask: Task<Void, Never>?

    public init(
        childId: String,
        pairingClient: PairingClientProtocol = FirebasePairingClient.shared,
        familyStore: FamilyStoreProtocol = FirestoreFamilyStore.shared,
        sessionRepository: ParentSessionRepository = .shared,
        networkMonitor: NetworkMonitorProtocol = NetworkMonitor.shared
    ) {
        self.childId = childId
        self.pairingClient = pairingClient
        self.familyStore = familyStore
        self.sessionRepository = sessionRepository
        self.networkMonitor = networkMonitor
    }

    deinit {
        countdownTask?.cancel()
        pollTask?.cancel()
    }

    @MainActor
    public func start() async {
        await refresh()
    }

    @MainActor
    public func refresh() async {
        countdownTask?.cancel()
        pollTask?.cancel()
        isLoading = true
        error = nil

        guard networkMonitor.isCurrentlyOnline else {
            isLoading = false
            error = .network("Internet connection required to create pairing code.")
            return
        }

        do {
            if FirebaseAuthClient.shared.currentUser == nil {
                _ = try? await FirebaseAuthClient.shared.ensureAuthenticatedParent()
            }
            var targetFamilyId: String? = nil
            if let uid = FirebaseAuthClient.shared.currentUser?.uid {
                targetFamilyId = try? await FirestoreFamilyStore.shared.getUserFamilyId(uid: uid)
            }
            if targetFamilyId == nil || targetFamilyId == "sample_family" {
                targetFamilyId = (sessionRepository.current()?.familyId == "sample_family") ? nil : sessionRepository.current()?.familyId
            }
            let tokenInfo = try await pairingClient.createPairingToken(childId: childId, familyId: targetFamilyId)
            self.offer = tokenInfo

            let expiryDate = Date(timeIntervalSince1970: TimeInterval(tokenInfo.expiresAtEpochMs / 1000))
            self.remainingSeconds = max(0, Int(expiryDate.timeIntervalSinceNow))

            // Load child profile details
            let children = try await familyStore.listChildren(familyId: tokenInfo.familyId)
            self.child = children.first(where: { $0.childId == childId })

            self.isLoading = false
            startCountdown()
            startDevicePolling(familyId: tokenInfo.familyId)
        } catch {
            self.isLoading = false
            self.error = AppErrorMapper.map(error)
        }
    }

    private func startCountdown() {
        countdownTask?.cancel()
        countdownTask = Task { @MainActor [weak self] in
            while let self = self, self.remainingSeconds > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard !Task.isCancelled else { break }
                self.remainingSeconds -= 1
            }
        }
    }

    private func startDevicePolling(familyId: String) {
        pollTask?.cancel()
        pollTask = Task { @MainActor [weak self] in
            while let self = self, !self.devicePaired {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                guard !Task.isCancelled else { break }

                if let devices = try? await self.familyStore.listDevices(familyId: familyId, childId: self.childId),
                   let device = devices.first(where: { !$0.revoked }) {
                    self.pairedDevice = device
                    self.devicePaired = true
                    break
                }
            }
        }
    }
}
