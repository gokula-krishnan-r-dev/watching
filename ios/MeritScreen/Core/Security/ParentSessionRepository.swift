import Foundation

/// Active authenticated parent session.
public struct ParentSession: Codable, Sendable, Equatable {
    public let uid: String
    public let familyId: String
    public let childIds: [String]

    public init(uid: String, familyId: String, childIds: [String]) {
        self.uid = uid
        self.familyId = familyId
        self.childIds = childIds
    }
}

/// Thread-safe repository managing the parent session state and persistence.
public final class ParentSessionRepository: @unchecked Sendable {
    public static let shared = ParentSessionRepository()

    private let userDefaults: UserDefaults
    private let key = "meritscreen.parent_session"
    private let lock = NSLock()
    private var cachedSession: ParentSession?

    public init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
        loadFromDisk()
    }

    public func current() -> ParentSession? {
        lock.lock()
        defer { lock.unlock() }
        return cachedSession
    }

    public func set(_ session: ParentSession) {
        lock.lock()
        defer { lock.unlock() }
        cachedSession = session
        if let data = try? JSONEncoder().encode(session) {
            userDefaults.set(data, forKey: key)
        }
    }

    public func clear() {
        lock.lock()
        defer { lock.unlock() }
        cachedSession = nil
        userDefaults.removeObject(forKey: key)
    }

    private func loadFromDisk() {
        if let data = userDefaults.data(forKey: key),
           let session = try? JSONDecoder().decode(ParentSession.self, from: data) {
            cachedSession = session
        }
    }
}
