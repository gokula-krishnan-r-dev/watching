import AVFoundation
import SwiftUI
import UIKit

/// Parsed QR code payload containing pairing code and secret.
public struct ParsedPairingQr: Sendable, Equatable {
    public let code: String
    public let secret: String?

    public init(code: String, secret: String? = nil) {
        self.code = code
        self.secret = secret
    }

    /// Parses URL format `meritscreen://pair?c={code}&s={secret}` or plain 6-digit code.
    public static func parse(_ raw: String) -> ParsedPairingQr? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if let url = URL(string: trimmed),
           let components = URLComponents(url: url, resolvingAgainstBaseURL: false) {
            let code = components.queryItems?.first(where: { $0.name == "c" })?.value
            let secret = components.queryItems?.first(where: { $0.name == "s" })?.value
            if let code = code, code.count == 6 {
                return ParsedPairingQr(code: code, secret: secret)
            }
        }
        let digits = trimmed.filter { $0.isNumber }
        if digits.count == 6 {
            return ParsedPairingQr(code: digits, secret: nil)
        }
        return nil
    }
}

/// SwiftUI wrapper around AVFoundation camera feed for scanning pairing QR codes.
public struct CameraQrScannerView: View {
    public let onScanned: (ParsedPairingQr) -> Void

    @State private var permissionStatus: AVAuthorizationStatus = AVCaptureDevice.authorizationStatus(for: .video)
    @State private var hasFoundCode = false

    public init(onScanned: @escaping (ParsedPairingQr) -> Void) {
        self.onScanned = onScanned
    }

    public var body: some View {
        ZStack {
            #if targetEnvironment(simulator)
            VStack(spacing: MeritSpacing.medium) {
                Image(systemName: "camera.viewfinder")
                    .font(.system(size: 48))
                    .foregroundColor(MeritColor.secondaryLabel)
                Text("Camera scanner is not available in Simulator.")
                    .font(MeritTypography.body)
                    .foregroundColor(MeritColor.secondaryLabel)
                    .multilineTextAlignment(.center)
                Text("Use the manual 6-digit code input below.")
                    .font(MeritTypography.subheadline)
                    .foregroundColor(MeritColor.accent)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(MeritColor.secondaryBackground)
            #else
            switch permissionStatus {
            case .authorized:
                AVCameraPreview(hasFoundCode: $hasFoundCode) { scannedString in
                    guard !hasFoundCode else { return }
                    if let parsed = ParsedPairingQr.parse(scannedString) {
                        hasFoundCode = true
                        let feedback = UINotificationFeedbackGenerator()
                        feedback.notificationOccurred(.success)
                        onScanned(parsed)
                    }
                }
                .overlay(scannerReticle)

            case .notDetermined:
                VStack(spacing: MeritSpacing.medium) {
                    ProgressView()
                    Text("Requesting camera access...")
                        .font(MeritTypography.body)
                        .foregroundColor(MeritColor.secondaryLabel)
                }
                .onAppear {
                    AVCaptureDevice.requestAccess(for: .video) { granted in
                        DispatchQueue.main.async {
                            permissionStatus = granted ? .authorized : .denied
                        }
                    }
                }

            case .denied, .restricted:
                VStack(spacing: MeritSpacing.medium) {
                    Image(systemName: "camera.badge.ellipsis")
                        .font(.system(size: 48))
                        .foregroundColor(MeritColor.destructive)

                    Text("Camera Access Required")
                        .font(MeritTypography.headline)
                        .foregroundColor(MeritColor.label)

                    Text("Please enable camera access in Settings to scan the QR code from the parent phone, or enter the 6-digit code manually below.")
                        .font(MeritTypography.footnote)
                        .foregroundColor(MeritColor.secondaryLabel)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, MeritSpacing.large)

                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        Button("Open Settings") {
                            UIApplication.shared.open(url)
                        }
                        .font(MeritTypography.subheadline)
                        .foregroundColor(MeritColor.accent)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(MeritColor.secondaryBackground)

            @unknown default:
                EmptyView()
            }
            #endif
        }
        .clipShape(RoundedRectangle(cornerRadius: MeritSpacing.radiusLarge, style: .continuous))
    }

    private var scannerReticle: some View {
        ZStack {
            Rectangle()
                .fill(Color.black.opacity(0.35))
                .mask(
                    ZStack {
                        Rectangle()
                        RoundedRectangle(cornerRadius: 16)
                            .frame(width: 220, height: 220)
                            .blendMode(.destinationOut)
                    }
                    .compositingGroup()
                )

            RoundedRectangle(cornerRadius: 16)
                .stroke(MeritColor.accent, lineWidth: 3)
                .frame(width: 220, height: 220)
        }
    }
}

#if !targetEnvironment(simulator)
private struct AVCameraPreview: UIViewControllerRepresentable {
    @Binding var hasFoundCode: Bool
    let onScanned: (String) -> Void

    func makeUIViewController(context: Context) -> CameraViewController {
        let vc = CameraViewController()
        vc.delegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ uiViewController: CameraViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onScanned: onScanned)
    }

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        let onScanned: (String) -> Void

        init(onScanned: @escaping (String) -> Void) {
            self.onScanned = onScanned
        }

        func metadataOutput(
            _ output: AVCaptureMetadataOutput,
            didOutput metadataObjects: [AVMetadataObject],
            from connection: AVCaptureConnection
        ) {
            guard let metadata = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
                  let stringValue = metadata.stringValue else {
                return
            }
            onScanned(stringValue)
        }
    }
}

private final class CameraViewController: UIViewController {
    weak var delegate: AVCaptureMetadataOutputObjectsDelegate?
    private let captureSession = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupCamera()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if !captureSession.isRunning {
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.captureSession.startRunning()
            }
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if captureSession.isRunning {
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.captureSession.stopRunning()
            }
        }
    }

    private func setupCamera() {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: device) else {
            return
        }

        captureSession.beginConfiguration()
        if captureSession.canAddInput(input) {
            captureSession.addInput(input)
        }

        let output = AVCaptureMetadataOutput()
        if captureSession.canAddOutput(output) {
            captureSession.addOutput(output)
            output.setMetadataObjectsDelegate(delegate, queue: .main)
            output.metadataObjectTypes = [.qr]
        }

        captureSession.commitConfiguration()

        let preview = AVCaptureVideoPreviewLayer(session: captureSession)
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)
        previewLayer = preview

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.captureSession.startRunning()
        }
    }
}
#endif
