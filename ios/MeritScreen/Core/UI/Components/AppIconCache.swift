import SwiftUI
import UIKit

/// Thread-safe, bounded in-memory icon and image cache to prevent redundant decoding/rendering during scroll.
/// Automatically purges on system memory warnings to conform strictly to iOS memory constraints.
/// Mirrors icon LRU cache from Android Phase 10.
public final class AppIconCache: @unchecked Sendable {
    public static let shared = AppIconCache()

    private let cache = NSCache<NSString, UIImage>()
    private let lock = NSLock()

    public init(countLimit: Int = 100) {
        cache.countLimit = countLimit
        cache.totalCostLimit = 10 * 1024 * 1024 // 10 MB maximum memory footprint

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleMemoryWarning),
            name: UIApplication.didReceiveMemoryWarningNotification,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func handleMemoryWarning() {
        clear()
    }

    public func image(forKey key: String) -> UIImage? {
        lock.lock()
        defer { lock.unlock() }
        return cache.object(forKey: key as NSString)
    }

    public func setImage(_ image: UIImage, forKey key: String) {
        lock.lock()
        defer { lock.unlock() }
        // Estimate byte size: width * height * 4
        let cost = Int(image.size.width * image.size.height * 4)
        cache.setObject(image, forKey: key as NSString, cost: cost)
    }

    public func removeImage(forKey key: String) {
        lock.lock()
        defer { lock.unlock() }
        cache.removeObject(forKey: key as NSString)
    }

    public func clear() {
        lock.lock()
        defer { lock.unlock() }
        cache.removeAllObjects()
    }
}
