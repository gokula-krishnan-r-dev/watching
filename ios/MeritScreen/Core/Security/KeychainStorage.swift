import Foundation
import Security

public protocol SecureStorageProtocol: Sendable {
    func get(_ key: String) -> String?
    func put(_ key: String, value: String)
    func remove(_ key: String)
}

/// Secure credential and state storage backed by the iOS Keychain.
public final class KeychainStorage: SecureStorageProtocol {
    public static let shared = KeychainStorage()

    private let service: String
    private let accessGroup: String?

    public init(
        service: String = "com.meritscreen.app.security",
        accessGroup: String? = nil
    ) {
        self.service = service
        self.accessGroup = accessGroup
    }

    public func get(_ key: String) -> String? {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        if let accessGroup = accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        guard status == errSecSuccess,
              let data = item as? Data,
              let string = String(data: data, encoding: .utf8) else {
            return nil
        }

        return string
    }

    public func put(_ key: String, value: String) {
        remove(key)

        guard let data = value.data(using: .utf8) else { return }

        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]

        if let accessGroup = accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }

        SecItemAdd(query as CFDictionary, nil)
    }

    public func remove(_ key: String) {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]

        if let accessGroup = accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }

        SecItemDelete(query as CFDictionary)
    }
}
