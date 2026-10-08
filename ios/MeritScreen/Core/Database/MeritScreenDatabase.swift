import Foundation
import SwiftData

/// Configures and manages the SwiftData ModelContainer for MeritScreen.
/// Shares storage between the main app and extensions via App Groups when available.
public final class MeritScreenDatabase: @unchecked Sendable {
    public static let shared = MeritScreenDatabase()

    public let container: ModelContainer

    public init(inMemory: Bool = false) {
        let schema = Schema([
            ChildPolicyEntity.self,
            AppRuleEntity.self,
            SessionStateEntity.self,
            QuizAttemptEntity.self,
            StickerEntity.self
        ])

        if inMemory {
            let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
            do {
                self.container = try ModelContainer(for: schema, configurations: [configuration])
            } catch {
                fatalError("Failed to create in-memory ModelContainer: \(error)")
            }
            return
        }

        // Shared App Group storage container
        let appGroupID = "group.com.watching.app"
        var storeURL: URL?

        if let groupURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupID) {
            storeURL = groupURL.appendingPathComponent("MeritScreenData.sqlite")
        }

        let configuration: ModelConfiguration
        if let storeURL = storeURL {
            configuration = ModelConfiguration(schema: schema, url: storeURL)
        } else {
            configuration = ModelConfiguration(schema: schema)
        }

        do {
            self.container = try ModelContainer(for: schema, configurations: [configuration])
        } catch {
            // Fallback to default in-app container if App Group setup fails during development
            do {
                self.container = try ModelContainer(for: schema)
            } catch {
                fatalError("Failed to initialize SwiftData ModelContainer: \(error)")
            }
        }
    }
}
