import CoreImage
import SwiftUI
import UIKit

/// Generates sharp, high-contrast QR codes using native CoreImage.
public enum QRCodeGenerator {
    private static let context = CIContext()

    /// Generates a crisp `UIImage` QR code for the given string payload.
    public static func generateImage(from string: String, targetSize: CGFloat = 220) -> UIImage? {
        guard let data = string.data(using: .utf8) else { return nil }

        guard let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        filter.setValue(data, forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")

        guard let outputImage = filter.outputImage else { return nil }

        // Scale up without blurriness
        let extent = outputImage.extent
        guard extent.width > 0, extent.height > 0 else { return nil }

        let scale = targetSize / extent.width
        let transformedImage = outputImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))

        guard let cgImage = context.createCGImage(transformedImage, from: transformedImage.extent) else {
            return nil
        }

        return UIImage(cgImage: cgImage)
    }
}
