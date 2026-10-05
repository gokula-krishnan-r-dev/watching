import SwiftUI

public enum EmergencyContactType {
    case phone
    case mom
    case dad

    public var label: String {
        switch self {
        case .phone: return "Phone"
        case .mom: return "Call Mom"
        case .dad: return "Call Dad"
        }
    }
}

/// View model driving the C12 Fail Lock screen with calm resting cooldown,
/// breathing tips, emergency contacts, Retry Quiz button, and Parent PIN override.
@Observable
@MainActor
public final class FailLockViewModel {

    // MARK: - State Properties

    public var childName: String = "Leo"
    public var secondsRemaining: Int = 0
    public var totalSeconds: Int = 900 // default 15 min
    public var endsAtEpochMs: Int64 = 0
    public var finishesAtLabel: String = ""
    public var currentTipIndex: Int = 0
    public var showingParentPin: Bool = false
    public var showingRetryQuiz: Bool = false

    public var isFinished: Bool {
        secondsRemaining <= 0
    }

    public var progress: Double {
        let safeTotal = max(1, totalSeconds)
        let elapsed = safeTotal - secondsRemaining
        return min(max(Double(elapsed) / Double(safeTotal), 0.0), 1.0)
    }

    public var formattedRemainingTime: String {
        let mins = max(0, secondsRemaining / 60)
        let secs = max(0, secondsRemaining % 60)
        return String(format: "%02d:%02d", mins, secs)
    }

    public var currentTip: String {
        let tips = [
            "Take a sip of water 💧",
            "Look out the window 🌳",
            "Stretch your arms and legs 🧘",
            "Roll your shoulders backwards 🌿",
            "Take three slow deep breaths 🌬️"
        ]
        return tips[currentTipIndex % tips.count]
    }

    private let localStore: ChildLocalStore
    @ObservationIgnored private var timerTask: Task<Void, Never>?

    public init(
        localStore: ChildLocalStore = .shared,
        childName: String = "Leo"
    ) {
        self.localStore = localStore
        self.childName = childName
        loadLockData()
    }

    deinit {
        timerTask?.cancel()
    }

    public func loadLockData() {
        let snapshot = localStore.getSessionSnapshot()
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let remaining = snapshot.remainingCooldownSeconds(nowEpochMs: now)
        let total = max(60, snapshot.cooldownMinutes * 60)

        self.secondsRemaining = remaining
        self.totalSeconds = total
        self.endsAtEpochMs = snapshot.deviceShieldedUntilEpochMs ?? (now + Int64(remaining * 1000))

        if let ends = snapshot.deviceShieldedUntilEpochMs, ends > 0 {
            let date = Date(timeIntervalSince1970: Double(ends) / 1000.0)
            let formatter = DateFormatter()
            formatter.timeStyle = .short
            self.finishesAtLabel = formatter.string(from: date)
        } else {
            self.finishesAtLabel = "—"
        }
    }

    public func startTimer(onFinished: @escaping () -> Void) {
        timerTask?.cancel()
        timerTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard let self else { return }

                if self.secondsRemaining > 0 {
                    self.secondsRemaining -= 1
                    if self.secondsRemaining % 20 == 0 {
                        self.currentTipIndex += 1
                    }
                } else {
                    self.timerTask?.cancel()
                    onFinished()
                    break
                }
            }
        }
    }

    public func stopTimer() {
        timerTask?.cancel()
        timerTask = nil
    }

    public func openEmergencyDialer(type: EmergencyContactType) {
        let urlString: String
        switch type {
        case .phone: urlString = "tel://"
        case .mom: urlString = "tel://1"
        case .dad: urlString = "tel://2"
        }
        if let url = URL(string: urlString), UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        }
    }
}
