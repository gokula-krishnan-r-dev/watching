import Foundation

/// Lightweight heuristics for classifying apps into categories for reporting and allowlist rules.
/// Matches Android `AppInventoryCategorizer` logic with iOS bundle identifier enhancements.
public enum AppInventoryCategorizer {

    public enum Category: String, Sendable, CaseIterable {
        case educational
        case entertainment
        case system
        case other
    }

    public struct Classification: Sendable, Equatable {
        public let category: Category
        public let subtitle: String
        public let verifiedSafe: Bool

        public init(category: Category, subtitle: String, verifiedSafe: Bool) {
            self.category = category
            self.subtitle = subtitle
            self.verifiedSafe = verifiedSafe
        }
    }

    public static func classify(packageName: String, label: String) -> Classification {
        let pkg = packageName.lowercased()
        let name = label.lowercased()

        if isSystem(pkg: pkg, name: name) {
            return Classification(
                category: .system,
                subtitle: "System / device tool",
                verifiedSafe: true
            )
        }
        if isEducational(pkg: pkg, name: name) {
            return Classification(
                category: .educational,
                subtitle: "Learning & skills",
                verifiedSafe: true
            )
        }
        if isEntertainment(pkg: pkg, name: name) {
            return Classification(
                category: .entertainment,
                subtitle: entertainmentSubtitle(pkg: pkg, name: name),
                verifiedSafe: false
            )
        }
        let leaf = packageName.components(separatedBy: ".").last ?? "Installed app"
        return Classification(
            category: .other,
            subtitle: leaf.isEmpty ? "Installed app" : leaf,
            verifiedSafe: false
        )
    }

    private static func isSystem(pkg: String, name: String) -> Bool {
        // Apple system app bundles
        if pkg == "com.apple.mobilephone" || pkg == "com.apple.preferences" ||
           pkg == "com.apple.facetime" || pkg == "com.apple.camera" ||
           pkg == "com.apple.mobileslideshow" || pkg == "com.apple.calculator" ||
           pkg == "com.apple.clock" || pkg == "com.apple.health" ||
           pkg == "com.apple.mobiletimer" {
            return true
        }
        // Android system packages (mixed-platform families)
        if pkg.hasPrefix("com.android.") || pkg.hasPrefix("com.google.android.dialer") ||
           (pkg.hasPrefix("com.samsung.android.") && (name.contains("phone") || name.contains("contact"))) {
            return true
        }

        let systemTokens = ["dialer", "phone", "contacts", "settings", "camera", "gallery", "photos"]
        let hasSystemToken = systemTokens.contains { pkg.contains($0) || name.contains($0) }
        return hasSystemToken && !pkg.contains("youtube") && !pkg.contains("game")
    }

    private static func isEducational(pkg: String, name: String) -> Bool {
        let tokens = [
            "khan", "duolingo", "duo", "scratch", "abcmouse", "epic", "reading", "math",
            "learn", "school", "education", "classroom", "quizlet", "photomath", "brainly",
            "brilliant", "coursera", "udemy", "wikipedia", "dictionary", "spell"
        ]
        return tokens.contains { pkg.contains($0) || name.contains($0) }
    }

    private static func isEntertainment(pkg: String, name: String) -> Bool {
        let tokens = [
            "youtube", "netflix", "disney", "hulu", "twitch", "tiktok", "roblox", "minecraft",
            "game", "play", "steam", "spotify", "music", "cinema", "movie", "video"
        ]
        return tokens.contains { pkg.contains($0) || name.contains($0) }
    }

    private static func entertainmentSubtitle(pkg: String, name: String) -> String {
        if pkg.contains("youtube") || name.contains("youtube") {
            return "Videos • consider a time cap"
        }
        if pkg.contains("roblox") || name.contains("roblox") {
            return "Online sandbox • restricted by default"
        }
        return "Entertainment"
    }
}
