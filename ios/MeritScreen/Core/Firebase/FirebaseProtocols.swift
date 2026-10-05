import Foundation

public struct AuthUser: Sendable, Equatable {
    public let uid: String
    public let email: String?

    public init(uid: String, email: String?) {
        self.uid = uid
        self.email = email
    }
}

public protocol AuthClientProtocol: Sendable {
    var currentUser: AuthUser? { get }
    func sendEmailOtp(email: String) async throws
    func verifyEmailOtp(email: String, code: String) async throws -> AuthUser
    func signInWithCustomToken(_ customToken: String) async throws -> AuthUser
    func signOut() throws
}

public struct PairingTokenInfo: Sendable, Equatable {
    public let code: String
    public let secret: String
    public let expiresAtEpochMs: Int64
    public let qrPayload: String
    public let childId: String
    public let familyId: String

    public init(
        code: String,
        secret: String,
        expiresAtEpochMs: Int64,
        qrPayload: String,
        childId: String,
        familyId: String
    ) {
        self.code = code
        self.secret = secret
        self.expiresAtEpochMs = expiresAtEpochMs
        self.qrPayload = qrPayload
        self.childId = childId
        self.familyId = familyId
    }
}

public struct ChildPairingResult: Sendable, Equatable {
    public let customToken: String
    public let familyId: String
    public let childId: String
    public let deviceId: String
    public let parentPinHash: String
    public let displayName: String
    public let ageBand: String

    public init(
        customToken: String,
        familyId: String,
        childId: String,
        deviceId: String,
        parentPinHash: String,
        displayName: String,
        ageBand: String
    ) {
        self.customToken = customToken
        self.familyId = familyId
        self.childId = childId
        self.deviceId = deviceId
        self.parentPinHash = parentPinHash
        self.displayName = displayName
        self.ageBand = ageBand
    }
}

public protocol PairingClientProtocol: Sendable {
    func createPairingToken(childId: String) async throws -> PairingTokenInfo
    func createPairingToken(childId: String, familyId: String?) async throws -> PairingTokenInfo
    func consumePairingToken(code: String, deviceId: String, secret: String?) async throws -> ChildPairingResult
}

extension PairingClientProtocol {
    public func createPairingToken(childId: String) async throws -> PairingTokenInfo {
        try await createPairingToken(childId: childId, familyId: nil)
    }

    public func createPairingToken(childId: String, familyId: String?) async throws -> PairingTokenInfo {
        try await createPairingToken(childId: childId)
    }
}

public protocol ChildPolicyClientProtocol: Sendable {
    func fetchCurrentPolicy(familyId: String, childId: String) async throws -> ChildPolicy
}

// MARK: - Phase i7: Real-Time Synchronization Protocols & Models

public struct DeviceHeartbeatInfo: Sendable, Equatable {
    public let launcherDefault: Bool
    public let model: String
    public let batteryPercent: Int?
    public let osVersion: String?
    public let appVersion: String?

    public init(
        launcherDefault: Bool = false,
        model: String,
        batteryPercent: Int? = nil,
        osVersion: String? = nil,
        appVersion: String? = nil
    ) {
        self.launcherDefault = launcherDefault
        self.model = model
        self.batteryPercent = batteryPercent
        self.osVersion = osVersion
        self.appVersion = appVersion
    }
}

public struct RemoteDeviceStatus: Sendable, Equatable {
    public let exists: Bool
    public let revoked: Bool
    public let launcherDefault: Bool

    public init(exists: Bool, revoked: Bool, launcherDefault: Bool = false) {
        self.exists = exists
        self.revoked = revoked
        self.launcherDefault = launcherDefault
    }
}

public protocol DeviceRegistryClientProtocol: Sendable {
    func registerPushToken(familyId: String, childId: String, deviceId: String, token: String) async throws
    func heartbeat(familyId: String, childId: String, deviceId: String, info: DeviceHeartbeatInfo) async throws
    func fetchStatus(familyId: String, childId: String, deviceId: String) async throws -> RemoteDeviceStatus
}

public struct ParentNotificationPrefs: Codable, Sendable, Equatable {
    public var dailySummary: Bool
    public var timeUp: Bool
    public var quizFailedRepeatedly: Bool
    public var quizPassedOptional: Bool
    public var deviceOffline: Bool
    public var pairingEvents: Bool
    public var launcherLost: Bool

    public init(
        dailySummary: Bool = true,
        timeUp: Bool = true,
        quizFailedRepeatedly: Bool = true,
        quizPassedOptional: Bool = false,
        deviceOffline: Bool = true,
        pairingEvents: Bool = true,
        launcherLost: Bool = true
    ) {
        self.dailySummary = dailySummary
        self.timeUp = timeUp
        self.quizFailedRepeatedly = quizFailedRepeatedly
        self.quizPassedOptional = quizPassedOptional
        self.deviceOffline = deviceOffline
        self.pairingEvents = pairingEvents
        self.launcherLost = launcherLost
    }
}

public struct ParentDeviceInfo: Sendable, Equatable {
    public let uid: String
    public let fcmToken: String
    public let platform: String
    public let appVersion: String
    public let model: String
    public let notificationPrefs: ParentNotificationPrefs

    public init(
        uid: String,
        fcmToken: String,
        platform: String = "ios",
        appVersion: String,
        model: String,
        notificationPrefs: ParentNotificationPrefs = ParentNotificationPrefs()
    ) {
        self.uid = uid
        self.fcmToken = fcmToken
        self.platform = platform
        self.appVersion = appVersion
        self.model = model
        self.notificationPrefs = notificationPrefs
    }
}

public protocol ParentDeviceRegistryClientProtocol: Sendable {
    func registerParentDevice(familyId: String, installationId: String, info: ParentDeviceInfo) async throws
}

public struct RemoteQuizAttempt: Codable, Sendable, Equatable {
    public let attemptId: String
    public let appId: String
    public let passed: Bool
    public let scorePercent: Int
    public let questionsAnswered: Int
    public let ageBand: String

    public init(
        attemptId: String,
        appId: String,
        passed: Bool,
        scorePercent: Int,
        questionsAnswered: Int,
        ageBand: String
    ) {
        self.attemptId = attemptId
        self.appId = appId
        self.passed = passed
        self.scorePercent = scorePercent
        self.questionsAnswered = questionsAnswered
        self.ageBand = ageBand
    }
}

public protocol UsageUploadClientProtocol: Sendable {
    func uploadUsage(familyId: String, childId: String, dateString: String, minutesUsed: Int, appMinutes: [String: Int]) async throws
    func uploadQuizAttempt(familyId: String, childId: String, attempt: RemoteQuizAttempt) async throws
}

public protocol PushTokenProviderProtocol: Sendable {
    func currentToken() async throws -> String?
}
