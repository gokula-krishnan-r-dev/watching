import Foundation

public enum PushMessageType: String, Sendable, Equatable {
    // Child Control Plane (Data-only)
    case policySync = "policy_sync"
    case deviceRevoked = "device_revoked"
    case pinSync = "pin_sync"
    case familyDeleted = "family_deleted"

    // Parent Awareness Plane (Display + Data)
    case parentChildPaired = "parent_child_paired"
    case parentTimeUp = "parent_time_up"
    case parentFailLock = "parent_fail_lock"
    case parentQuizFailStreak = "parent_quiz_fail_streak"
    case parentQuizPassed = "parent_quiz_passed"
    case parentDailySummary = "parent_daily_summary"
    case parentDeviceOffline = "parent_device_offline"
    case parentLauncherLost = "parent_launcher_lost"

    case unknown

    public var isControlPlane: Bool {
        switch self {
        case .policySync, .deviceRevoked, .pinSync, .familyDeleted:
            return true
        default:
            return false
        }
    }

    public var isAwarenessPlane: Bool {
        !isControlPlane && self != .unknown
    }
}

public struct PushMessage: Sendable, Equatable {
    public let type: PushMessageType
    public let familyId: String?
    public let childId: String?
    public let route: String?
    public let rawPayload: [String: String]

    public init(
        type: PushMessageType,
        familyId: String? = nil,
        childId: String? = nil,
        route: String? = nil,
        rawPayload: [String: String] = [:]
    ) {
        self.type = type
        self.familyId = familyId
        self.childId = childId
        self.route = route
        self.rawPayload = rawPayload
    }

    public static func parse(from userInfo: [AnyHashable: Any]) -> PushMessage {
        var strMap: [String: String] = [:]
        for (k, v) in userInfo {
            if let keyStr = k as? String {
                if let strVal = v as? String {
                    strMap[keyStr] = strVal
                } else if let numVal = v as? NSNumber {
                    strMap[keyStr] = numVal.stringValue
                }
            }
        }

        let typeRaw = strMap["type"] ?? ""
        let type = PushMessageType(rawValue: typeRaw) ?? .unknown
        let familyId = strMap["familyId"]
        let childId = strMap["childId"]
        let route = strMap["route"]

        return PushMessage(
            type: type,
            familyId: familyId,
            childId: childId,
            route: route,
            rawPayload: strMap
        )
    }
}
