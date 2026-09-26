package com.meritscreen.feature.devices

import com.meritscreen.core.common.domain.DeviceRemoteStatus
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.core.firebase.device.DeviceRegistryClient
import com.meritscreen.core.security.pairing.ChildPairingCredential
import com.meritscreen.core.security.pairing.ChildPairingStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHeartbeatUseCaseTest {

    private val credential = ChildPairingCredential(
        familyId = "fam1",
        childId = "child1",
        deviceId = "device1",
        parentPinHash = "hash",
    )

    @Test
    fun `skips when device is not paired`() = runTest {
        val client = FakeDeviceRegistryClient()
        val useCase = DeviceHeartbeatUseCase(FakePairingStore(null), client)
        val outcome = useCase(isDefaultHome = true, deviceModel = "Pixel")
        assertEquals(HeartbeatOutcome.Skipped, outcome)
        assertEquals(0, client.heartbeatCalls)
    }

    @Test
    fun `writes heartbeat and reports ok when not revoked`() = runTest {
        val client = FakeDeviceRegistryClient(status = DeviceRemoteStatus(revoked = false))
        val useCase = DeviceHeartbeatUseCase(FakePairingStore(credential), client)
        val outcome = useCase(isDefaultHome = true, deviceModel = "Pixel")
        assertEquals(HeartbeatOutcome.Ok, outcome)
        assertEquals(1, client.heartbeatCalls)
        assertTrue(client.lastLauncherDefault == true)
    }

    @Test
    fun `reports revoked when parent has revoked the device`() = runTest {
        val client = FakeDeviceRegistryClient(status = DeviceRemoteStatus(revoked = true))
        val useCase = DeviceHeartbeatUseCase(FakePairingStore(credential), client)
        val outcome = useCase(isDefaultHome = false, deviceModel = "Pixel")
        assertEquals(HeartbeatOutcome.Revoked, outcome)
    }

    @Test
    fun `reports failed when the network call throws`() = runTest {
        val client = FakeDeviceRegistryClient(throwOnHeartbeat = true)
        val useCase = DeviceHeartbeatUseCase(FakePairingStore(credential), client)
        val outcome = useCase(isDefaultHome = true, deviceModel = "Pixel")
        assertTrue(outcome is HeartbeatOutcome.Failed)
    }
}

private class FakePairingStore(private var credential: ChildPairingCredential?) : ChildPairingStore {
    override suspend fun get(): ChildPairingCredential? = credential
    override suspend fun set(credential: ChildPairingCredential) {
        this.credential = credential
    }
    override suspend fun getOrCreateDeviceId(): String = credential?.deviceId ?: "generated"
    override suspend fun clear() {
        credential = null
    }
}

private class FakeDeviceRegistryClient(
    private val status: DeviceRemoteStatus = DeviceRemoteStatus(revoked = false),
    private val throwOnHeartbeat: Boolean = false,
) : DeviceRegistryClient {
    var heartbeatCalls = 0
    var lastLauncherDefault: Boolean? = null

    override suspend fun heartbeat(
        familyId: String,
        childId: String,
        deviceId: String,
        launcherDefault: Boolean,
        model: String,
        batteryPercent: Int?,
        osVersion: String?,
        appVersion: String?,
    ) {
        if (throwOnHeartbeat) throw IllegalStateException("network down")
        heartbeatCalls++
        lastLauncherDefault = launcherDefault
    }

    override suspend fun uploadInstalledApps(
        familyId: String,
        childId: String,
        deviceId: String,
        apps: List<InstalledAppSummary>,
    ) = Unit

    override suspend fun fetchStatus(familyId: String, childId: String, deviceId: String): DeviceRemoteStatus = status

    override fun observeStatus(
        familyId: String,
        childId: String,
        deviceId: String,
    ): kotlinx.coroutines.flow.Flow<DeviceRemoteStatus> = kotlinx.coroutines.flow.flowOf(status)

    override suspend fun registerPushToken(familyId: String, childId: String, deviceId: String, token: String) =
        Unit
}
