import Foundation

/// Secure pairing credential held locally on the child device.
public struct ChildPairingCredential: Codable, Sendable, Equatable {
    public let familyId: String
    public let childId: String
    public let deviceId: String
    public let parentPinHash: String
    public let displayName: String
    public let ageBand: String

    public init(
        familyId: String,
        childId: String,
        deviceId: String,
        parentPinHash: String,
        displayName: String,
        ageBand: String = ""
    ) {
        self.familyId = familyId
        self.childId = childId
        self.deviceId = deviceId
        self.parentPinHash = parentPinHash
        self.displayName = displayName
        self.ageBand = ageBand
    }
}

/// Secure storage for child pairing credentials and device identity.
public final class ChildPairingStore: @unchecked Sendable {
    public static let shared = ChildPairingStore()

    private let secureStorage: SecureStorageProtocol
    private let deviceIdKey = "meritscreen.child_device_id"
    private let credentialKey = "meritscreen.child_pairing_credential"
    private let lock = NSLock()
    private var cachedCredential: ChildPairingCredential?

    public init(secureStorage: SecureStorageProtocol = KeychainStorage.shared) {
        self.secureStorage = secureStorage
        loadCredential()
    }

    /// Retrieves the persistent unique device ID, or mints and stores a new one.
    public func getOrCreateDeviceId() -> String {
        lock.lock()
        defer { lock.unlock() }

        if let existing = secureStorage.get(deviceIdKey), !existing.isEmpty {
            return existing
        }
        let freshId = UUID().uuidString.lowercased().replacingOccurrences(of: "-", with: "")
        secureStorage.put(deviceIdKey, value: freshId)
        return freshId
    }

    public func current() -> ChildPairingCredential? {
        lock.lock()
        defer { lock.unlock() }
        return cachedCredential
    }

    public func set(_ credential: ChildPairingCredential) {
        lock.lock()
        defer { lock.unlock() }
        cachedCredential = credential
        if let data = try? JSONEncoder().encode(credential),
           let jsonString = String(data: data, encoding: .utf8) {
            secureStorage.put(credentialKey, value: jsonString)
        }
    }

    public func clear() {
        lock.lock()
        defer { lock.unlock() }
        cachedCredential = nil
        secureStorage.remove(credentialKey)
    }

    private func loadCredential() {
        if let jsonString = secureStorage.get(credentialKey),
           let data = jsonString.data(using: .utf8),
           let credential = try? JSONDecoder().decode(ChildPairingCredential.self, from: data) {
            cachedCredential = credential
        }
    }
}
