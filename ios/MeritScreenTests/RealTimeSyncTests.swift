import XCTest
@testable import MeritScreen

final class RealTimeSyncTests: XCTestCase {
    private var testDefaults: UserDefaults!
    private var fakeSecureStorage: FakeSecureStorage!
    private var pairingStore: ChildPairingStore!
    private var localStore: ChildLocalStore!
    private var mockRegistry: MockDeviceRegistryClient!
    private var mockParentRegistry: MockParentDeviceRegistryClient!
    private var mockUploadClient: MockUsageUploadClient!
    private var mockTokenProvider: MockPushTokenProvider!
    private var mockFamilyStore: MockFamilyStore!

    override func setUp() {
        super.setUp()
        ScreenTimeSharedStore.shared.setDeviceRevoked(false)
        let suite = "test_sync_\(UUID().uuidString)"
        testDefaults = UserDefaults(suiteName: suite)!
        fakeSecureStorage = FakeSecureStorage()
        pairingStore = ChildPairingStore(secureStorage: fakeSecureStorage)
        localStore = ChildLocalStore(defaults: testDefaults)
        mockRegistry = MockDeviceRegistryClient()
        mockParentRegistry = MockParentDeviceRegistryClient()
        mockUploadClient = MockUsageUploadClient()
        mockTokenProvider = MockPushTokenProvider()
        mockFamilyStore = MockFamilyStore()
    }

    override func tearDown() {
        pairingStore.clear()
        localStore.clearAll()
        ScreenTimeSharedStore.shared.setDeviceRevoked(false)
        super.tearDown()
    }

    // MARK: - PushMessage Parsing Tests

    func testPushMessageParsingControlPlane() {
        let payload: [AnyHashable: Any] = [
            "type": "policy_sync",
            "familyId": "fam_123",
            "childId": "child_456"
        ]
        let message = PushMessage.parse(from: payload)

        XCTAssertEqual(message.type, .policySync)
        XCTAssertTrue(message.type.isControlPlane)
        XCTAssertFalse(message.type.isAwarenessPlane)
        XCTAssertEqual(message.familyId, "fam_123")
        XCTAssertEqual(message.childId, "child_456")
        XCTAssertNil(message.route)
    }

    func testPushMessageParsingAwarenessPlane() {
        let payload: [AnyHashable: Any] = [
            "type": "parent_fail_lock",
            "familyId": "fam_123",
            "childId": "child_456",
            "route": "child_detail"
        ]
        let message = PushMessage.parse(from: payload)

        XCTAssertEqual(message.type, .parentFailLock)
        XCTAssertFalse(message.type.isControlPlane)
        XCTAssertTrue(message.type.isAwarenessPlane)
        XCTAssertEqual(message.familyId, "fam_123")
        XCTAssertEqual(message.childId, "child_456")
        XCTAssertEqual(message.route, "child_detail")
    }

    func testPushMessageParsingUnknownType() {
        let payload: [AnyHashable: Any] = [
            "type": "nonexistent_custom_type"
        ]
        let message = PushMessage.parse(from: payload)

        XCTAssertEqual(message.type, .unknown)
        XCTAssertFalse(message.type.isControlPlane)
        XCTAssertFalse(message.type.isAwarenessPlane)
    }

    // MARK: - DevicePushTokenRegistrar Tests

    func testDevicePushTokenRegistrarUnpaired() async {
        let registrar = DevicePushTokenRegistrar(
            pairingStore: pairingStore,
            tokenProvider: mockTokenProvider,
            registryClient: mockRegistry,
            defaults: testDefaults
        )

        let success = await registrar.registerPushTokenIfNeeded()
        XCTAssertFalse(success)
        XCTAssertTrue(mockRegistry.registeredTokens.isEmpty)
    }

    func testDevicePushTokenRegistrarSuccess() async {
        let credential = ChildPairingCredential(
            familyId: "fam_1",
            childId: "child_1",
            deviceId: "device_abc",
            parentPinHash: "pin_hash",
            displayName: "Leo",
            ageBand: "band_7_9"
        )
        pairingStore.set(credential)

        let registrar = DevicePushTokenRegistrar(
            pairingStore: pairingStore,
            tokenProvider: mockTokenProvider,
            registryClient: mockRegistry,
            defaults: testDefaults
        )

        let success = await registrar.registerPushTokenIfNeeded()
        XCTAssertTrue(success)
        XCTAssertEqual(mockRegistry.registeredTokens["device_abc"], "mock_fcm_token_12345")

        // Redundant call should not re-register unless forced
        mockRegistry.registeredTokens.removeAll()
        let secondCall = await registrar.registerPushTokenIfNeeded(force: false)
        XCTAssertTrue(secondCall)
        XCTAssertTrue(mockRegistry.registeredTokens.isEmpty)

        // Force call re-registers
        let forceCall = await registrar.registerPushTokenIfNeeded(force: true)
        XCTAssertTrue(forceCall)
        XCTAssertEqual(mockRegistry.registeredTokens["device_abc"], "mock_fcm_token_12345")
    }

    // MARK: - ParentPushTokenRegistrar Tests

    func testParentPushTokenRegistrarUnauthenticated() async {
        let sessionRepo = ParentSessionRepository(userDefaults: testDefaults)
        sessionRepo.clear()

        let registrar = ParentPushTokenRegistrar(
            sessionRepo: sessionRepo,
            tokenProvider: mockTokenProvider,
            registryClient: mockParentRegistry,
            defaults: testDefaults
        )

        let success = await registrar.registerParentTokenIfNeeded()
        XCTAssertFalse(success)
        XCTAssertTrue(mockParentRegistry.registeredDevices.isEmpty)
    }

    func testParentPushTokenRegistrarAuthenticated() async {
        let sessionRepo = ParentSessionRepository(userDefaults: testDefaults)
        sessionRepo.set(ParentSession(uid: "parent_uid_1", familyId: "fam_parent_1", childIds: ["c1"]))

        let registrar = ParentPushTokenRegistrar(
            sessionRepo: sessionRepo,
            tokenProvider: mockTokenProvider,
            registryClient: mockParentRegistry,
            defaults: testDefaults
        )

        let success = await registrar.registerParentTokenIfNeeded()
        XCTAssertTrue(success)
        XCTAssertFalse(mockParentRegistry.registeredDevices.isEmpty)

        let installationId = registrar.getOrCreateInstallationId()
        let registeredInfo = mockParentRegistry.registeredDevices[installationId]
        XCTAssertNotNil(registeredInfo)
        XCTAssertEqual(registeredInfo?.uid, "parent_uid_1")
        XCTAssertEqual(registeredInfo?.fcmToken, "mock_fcm_token_12345")
        XCTAssertEqual(registeredInfo?.platform, "ios")
    }

    // MARK: - DeviceHeartbeatCoordinator Tests

    func testHeartbeatUnpaired() async {
        let coordinator = DeviceHeartbeatCoordinator(
            pairingStore: pairingStore,
            registryClient: mockRegistry
        )

        let success = await coordinator.performHeartbeat()
        XCTAssertFalse(success)
        XCTAssertTrue(mockRegistry.heartbeats.isEmpty)
    }

    func testHeartbeatSuccess() async {
        let credential = ChildPairingCredential(
            familyId: "fam_1",
            childId: "child_1",
            deviceId: "device_heartbeat_1",
            parentPinHash: "hash",
            displayName: "Mia",
            ageBand: "band_10_12"
        )
        pairingStore.set(credential)

        let coordinator = DeviceHeartbeatCoordinator(
            pairingStore: pairingStore,
            registryClient: mockRegistry
        )

        let success = await coordinator.performHeartbeat()
        XCTAssertTrue(success)
        XCTAssertEqual(mockRegistry.heartbeats.count, 1)
        XCTAssertEqual(mockRegistry.heartbeats.first?.deviceId, "device_heartbeat_1")
    }

    func testHeartbeatDetectsRevocationAndKillsSession() async {
        let credential = ChildPairingCredential(
            familyId: "fam_1",
            childId: "child_1",
            deviceId: "device_to_revoke",
            parentPinHash: "hash",
            displayName: "Mia",
            ageBand: "band_10_12"
        )
        pairingStore.set(credential)

        // Server reports device is revoked!
        mockRegistry.remoteStatus = RemoteDeviceStatus(exists: true, revoked: true, launcherDefault: false)

        let coordinator = DeviceHeartbeatCoordinator(
            pairingStore: pairingStore,
            registryClient: mockRegistry
        )

        let success = await coordinator.performHeartbeat()
        XCTAssertFalse(success)

        // Killswitch verification: Pairing must be cleared immediately
        XCTAssertNil(pairingStore.current())
    }

    // MARK: - UsageSyncCoordinator Tests

    func testUsageSyncUploadsMinutesAndPendingAttempts() async {
        let credential = ChildPairingCredential(
            familyId: "fam_1",
            childId: "child_usage_1",
            deviceId: "device_usage",
            parentPinHash: "hash",
            displayName: "Noah",
            ageBand: "band_7_9"
        )
        pairingStore.set(credential)

        // Set session snapshot minutes
        var snapshot = localStore.getSessionSnapshot()
        snapshot.minutesUsedToday = 45
        localStore.saveSessionSnapshot(snapshot)

        // Add pending quiz attempt
        let attempt = RemoteQuizAttempt(
            attemptId: "att_1",
            appId: "com.apple.mobilesafari",
            passed: true,
            scorePercent: 100,
            questionsAnswered: 3,
            ageBand: "band_7_9"
        )
        localStore.addPendingQuizAttempt(attempt)
        XCTAssertEqual(localStore.getPendingQuizAttempts().count, 1)

        let testSharedStore = ScreenTimeSharedStore(suiteName: "test_screentime_\(UUID().uuidString)")
        testSharedStore.setDeviceRevoked(false)
        let coordinator = UsageSyncCoordinator(
            pairingStore: pairingStore,
            localStore: localStore,
            uploadClient: mockUploadClient,
            revocationGuard: DeviceRevocationGuard(sharedStore: testSharedStore)
        )

        let success = await coordinator.syncUsageAndAttempts()
        XCTAssertTrue(success)

        // Verify uploads
        XCTAssertEqual(mockUploadClient.uploadedUsages.count, 1)
        XCTAssertEqual(mockUploadClient.uploadedUsages.first?.minutes, 45)
        XCTAssertEqual(mockUploadClient.uploadedAttempts.count, 1)
        XCTAssertEqual(mockUploadClient.uploadedAttempts.first?.attempt.attemptId, "att_1")

        // Queue must now be cleared
        XCTAssertTrue(localStore.getPendingQuizAttempts().isEmpty)
    }

    // MARK: - PushNotificationRouter Control Plane Tests

    func testRouterDeviceRevokedImmediateUnpair() async {
        let credential = ChildPairingCredential(
            familyId: "fam_1",
            childId: "child_revoke",
            deviceId: "device_fcm_revoke",
            parentPinHash: "hash",
            displayName: "Emma",
            ageBand: "band_4_6"
        )
        pairingStore.set(credential)

        let router = PushNotificationRouter(
            pairingStore: pairingStore,
            sharedStore: ScreenTimeSharedStore(suiteName: "test_screentime_\(UUID().uuidString)"),
            familyStore: mockFamilyStore
        )

        let payload: [AnyHashable: Any] = [
            "type": "device_revoked",
            "familyId": "fam_1",
            "childId": "child_revoke"
        ]

        let result = await router.handleRemoteNotification(userInfo: payload)
        XCTAssertEqual(result, .newData)

        // Invariant: Unpair immediately upon device_revoked push
        XCTAssertNil(pairingStore.current())
    }

    func testRouterFamilyDeletedWipesSession() async {
        let credential = ChildPairingCredential(
            familyId: "fam_del",
            childId: "child_del",
            deviceId: "device_del",
            parentPinHash: "hash",
            displayName: "Liam",
            ageBand: "band_7_9"
        )
        pairingStore.set(credential)

        let router = PushNotificationRouter(
            pairingStore: pairingStore,
            sharedStore: ScreenTimeSharedStore(suiteName: "test_screentime_\(UUID().uuidString)"),
            familyStore: mockFamilyStore
        )

        let payload: [AnyHashable: Any] = [
            "type": "family_deleted",
            "familyId": "fam_del"
        ]

        let result = await router.handleRemoteNotification(userInfo: payload)
        XCTAssertEqual(result, .newData)

        // Invariant: Family deleted clears pairing
        XCTAssertNil(pairingStore.current())
    }
}
