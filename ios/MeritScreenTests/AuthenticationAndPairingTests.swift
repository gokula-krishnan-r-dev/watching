import XCTest
@testable import MeritScreen

final class AuthenticationAndPairingTests: XCTestCase {
    private var testUserDefaults: UserDefaults!
    private var draftRepo: OnboardingDraftRepository!
    private var sessionRepo: ParentSessionRepository!
    private var mockAuth: MockAuthClient!
    private var mockFamilyStore: MockFamilyStore!
    private var mockPairingClient: MockPairingClient!

    override func setUp() {
        super.setUp()
        let suiteName = "test_auth_\(UUID().uuidString)"
        testUserDefaults = UserDefaults(suiteName: suiteName)!
        draftRepo = OnboardingDraftRepository(defaults: testUserDefaults)
        sessionRepo = ParentSessionRepository(userDefaults: testUserDefaults)
        mockAuth = MockAuthClient()
        mockFamilyStore = MockFamilyStore()
        mockPairingClient = MockPairingClient()
    }

    override func tearDown() {
        draftRepo.clear()
        sessionRepo.clear()
        super.tearDown()
    }

    // MARK: - CredentialsValidator Tests

    func testCredentialsValidatorEmail() {
        XCTAssertNil(CredentialsValidator.emailError("parent@example.com"))
        XCTAssertNil(CredentialsValidator.emailError("parent.smith+test@domain.co.uk"))
        XCTAssertNotNil(CredentialsValidator.emailError(""))
        XCTAssertNotNil(CredentialsValidator.emailError("invalid-email"))
        XCTAssertNotNil(CredentialsValidator.emailError("@example.com"))
        XCTAssertNotNil(CredentialsValidator.emailError("parent@"))
    }

    func testCredentialsValidatorOtp() {
        XCTAssertNil(CredentialsValidator.otpError("123456"))
        XCTAssertNotNil(CredentialsValidator.otpError("12345"))
        XCTAssertNotNil(CredentialsValidator.otpError("1234567"))
        XCTAssertNotNil(CredentialsValidator.otpError("abcdef"))
        XCTAssertNotNil(CredentialsValidator.otpError(""))
    }

    func testEmailNormalization() {
        XCTAssertEqual(CredentialsValidator.normalizeEmail("  Parent@Example.COM  "), "parent@example.com")
    }

    // MARK: - CompleteParentAuthUseCase Tests

    func testSendEmailOtpValidation() async {
        let useCase = CompleteParentAuthUseCase(
            authClient: mockAuth,
            familyStore: mockFamilyStore,
            draftRepository: draftRepo,
            sessionRepository: sessionRepo
        )

        let badEmailOutcome = await useCase.sendEmailOtp(email: "bad-email")
        guard case .failure(let error) = badEmailOutcome else {
            XCTFail("Expected failure for bad email")
            return
        }
        XCTAssertTrue(error.userMessage.contains("email"))

        let goodEmailOutcome = await useCase.sendEmailOtp(email: "parent@meritscreen.com")
        guard case .success = goodEmailOutcome else {
            XCTFail("Expected success for valid email")
            return
        }
    }

    func testVerifyEmailOtpCommitsOnboardingDraft() async {
        // Setup ready onboarding draft
        draftRepo.setConsentGiven(true)
        draftRepo.setFamilyName("The Incredibles")
        try? draftRepo.addChild(ChildDraft(name: "Dash", ageBand: .band_7_9, avatar: .astro))
        try? draftRepo.setParentPin(pin: "2468")

        let useCase = CompleteParentAuthUseCase(
            authClient: mockAuth,
            familyStore: mockFamilyStore,
            draftRepository: draftRepo,
            sessionRepository: sessionRepo
        )

        let outcome = await useCase.verifyEmailOtp(email: "parent@example.com", code: "123456")
        guard case .success(let result) = outcome else {
            XCTFail("Expected successful verification and family commit")
            return
        }

        XCTAssertTrue(result.isNewFamily)
        XCTAssertFalse(result.needsOnboarding)
        XCTAssertNotNil(result.familyId)
        XCTAssertEqual(result.childIds.count, 1)

        // Verify draft was cleared after commit
        XCTAssertFalse(draftRepo.current().consentGiven)
        XCTAssertTrue(draftRepo.current().children.isEmpty)

        // Verify session was persisted
        let session = sessionRepo.current()
        XCTAssertNotNil(session)
        XCTAssertEqual(session?.familyId, result.familyId)
        XCTAssertEqual(session?.childIds, result.childIds)
    }

    func testVerifyEmailOtpReconcilesExistingFamily() async {
        // User already has family in cloud
        mockFamilyStore.userFamilyId = "existing_family_99"
        mockFamilyStore.children = [
            FamilyChildProfile(childId: "child_1", displayName: "Maya", ageBand: .band_10_12, avatar: .fox)
        ]

        let useCase = CompleteParentAuthUseCase(
            authClient: mockAuth,
            familyStore: mockFamilyStore,
            draftRepository: draftRepo,
            sessionRepository: sessionRepo
        )

        let outcome = await useCase.verifyEmailOtp(email: "existing@example.com", code: "654321")
        guard case .success(let result) = outcome else {
            XCTFail("Expected successful sign in for existing user")
            return
        }

        XCTAssertFalse(result.isNewFamily)
        XCTAssertFalse(result.needsOnboarding)
        XCTAssertEqual(result.familyId, "existing_family_99")
        XCTAssertEqual(result.childIds, ["child_1"])

        let session = sessionRepo.current()
        XCTAssertEqual(session?.familyId, "existing_family_99")
    }

    // MARK: - PairChildDeviceUseCase Tests

    func testPairChildDeviceValidation() async {
        let store = ChildPairingStore.shared
        let useCase = PairChildDeviceUseCase(
            pairingClient: mockPairingClient,
            authClient: mockAuth,
            pairingStore: store
        )

        let shortCodeOutcome = await useCase.execute(code: "12345")
        guard case .failure(let error) = shortCodeOutcome else {
            XCTFail("Expected failure for short pairing code")
            return
        }
        XCTAssertTrue(error.userMessage.contains("6-digit"))

        let successOutcome = await useCase.execute(code: "123456", secret: "test_secret")
        guard case .success(let credential) = successOutcome else {
            XCTFail("Expected pairing to succeed")
            return
        }

        XCTAssertEqual(credential.familyId, "mock_family_1")
        XCTAssertEqual(credential.childId, "mock_child_1")
        XCTAssertEqual(credential.displayName, "Leo")
        XCTAssertFalse(credential.deviceId.isEmpty)

        // Verify stored in ChildPairingStore
        XCTAssertEqual(store.current()?.childId, "mock_child_1")
    }

    // MARK: - QR Code Parser & Generator Tests

    func testParsedPairingQrURL() {
        let urlPayload = "meritscreen://pair?c=890123&s=supersecretkey77"
        let parsed = ParsedPairingQr.parse(urlPayload)

        XCTAssertNotNil(parsed)
        XCTAssertEqual(parsed?.code, "890123")
        XCTAssertEqual(parsed?.secret, "supersecretkey77")
    }

    func testParsedPairingQrPlainDigits() {
        let plain = "554433"
        let parsed = ParsedPairingQr.parse(plain)

        XCTAssertNotNil(parsed)
        XCTAssertEqual(parsed?.code, "554433")
        XCTAssertNil(parsed?.secret)
    }

    func testParsedPairingQrMalformed() {
        XCTAssertNil(ParsedPairingQr.parse("hello-world"))
        XCTAssertNil(ParsedPairingQr.parse("meritscreen://pair?c=123"))
    }

    func testQRCodeGeneratorOutput() {
        let image = QRCodeGenerator.generateImage(from: "meritscreen://pair?c=123456&s=abc", targetSize: 200)
        XCTAssertNotNil(image)
        XCTAssertGreaterThan(image?.size.width ?? 0, 0)
    }

    // MARK: - SignOutParentUseCase Tests

    func testSignOutClearsParentSession() {
        sessionRepo.set(ParentSession(uid: "parent_1", familyId: "fam_1", childIds: ["c1"]))
        XCTAssertNotNil(sessionRepo.current())

        let signOut = SignOutParentUseCase(
            authClient: mockAuth,
            sessionRepository: sessionRepo,
            draftRepository: draftRepo
        )
        signOut.execute()

        XCTAssertNil(sessionRepo.current())
    }
}
