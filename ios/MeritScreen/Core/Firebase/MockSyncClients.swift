import Foundation

public final class MockPushTokenProvider: @unchecked Sendable, PushTokenProviderProtocol {
    public var mockToken: String? = "mock_fcm_token_12345"

    public init(mockToken: String? = "mock_fcm_token_12345") {
        self.mockToken = mockToken
    }

    public func currentToken() async throws -> String? {
        return mockToken
    }
}

public final class MockDeviceRegistryClient: @unchecked Sendable, DeviceRegistryClientProtocol {
    public var registeredTokens: [String: String] = [:]
    public var heartbeats: [(deviceId: String, info: DeviceHeartbeatInfo)] = []
    public var remoteStatus: RemoteDeviceStatus = RemoteDeviceStatus(exists: true, revoked: false, launcherDefault: false)

    public init() {}

    public func registerPushToken(
        familyId: String,
        childId: String,
        deviceId: String,
        token: String
    ) async throws {
        registeredTokens[deviceId] = token
    }

    public func heartbeat(
        familyId: String,
        childId: String,
        deviceId: String,
        info: DeviceHeartbeatInfo
    ) async throws {
        heartbeats.append((deviceId: deviceId, info: info))
    }

    public func fetchStatus(
        familyId: String,
        childId: String,
        deviceId: String
    ) async throws -> RemoteDeviceStatus {
        return remoteStatus
    }
}

public final class MockParentDeviceRegistryClient: @unchecked Sendable, ParentDeviceRegistryClientProtocol {
    public var registeredDevices: [String: ParentDeviceInfo] = [:]

    public init() {}

    public func registerParentDevice(
        familyId: String,
        installationId: String,
        info: ParentDeviceInfo
    ) async throws {
        registeredDevices[installationId] = info
    }
}

public final class MockUsageUploadClient: @unchecked Sendable, UsageUploadClientProtocol {
    public var uploadedUsages: [(childId: String, date: String, minutes: Int, appMinutes: [String: Int])] = []
    public var uploadedAttempts: [(childId: String, attempt: RemoteQuizAttempt)] = []

    public init() {}

    public func uploadUsage(
        familyId: String,
        childId: String,
        dateString: String,
        minutesUsed: Int,
        appMinutes: [String: Int]
    ) async throws {
        uploadedUsages.append((childId: childId, date: dateString, minutes: minutesUsed, appMinutes: appMinutes))
    }

    public func uploadQuizAttempt(
        familyId: String,
        childId: String,
        attempt: RemoteQuizAttempt
    ) async throws {
        uploadedAttempts.append((childId: childId, attempt: attempt))
    }
}
