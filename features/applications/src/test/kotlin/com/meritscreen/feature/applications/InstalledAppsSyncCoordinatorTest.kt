package com.meritscreen.feature.applications

import com.meritscreen.core.common.domain.DeviceRemoteStatus
import com.meritscreen.core.common.domain.InstalledAppSummary
import com.meritscreen.core.database.device.DeviceRuntimeStateDao
import com.meritscreen.core.database.device.DeviceRuntimeStateEntity
import com.meritscreen.core.firebase.device.DeviceRegistryClient
import com.meritscreen.core.security.pairing.ChildPairingCredential
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.core.common.logging.AppLogger
import com.meritscreen.core.testing.FakeNetworkMonitor
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppsSyncCoordinatorTest {

    private val credential = ChildPairingCredential(
        familyId = "fam1",
        childId = "child1",
        deviceId = "device1",
        parentPinHash = "hash",
    )

    private fun coordinator(
        client: FakeDeviceRegistryClient = FakeDeviceRegistryClient(),
        pairingStore: FakePairingStore = FakePairingStore(credential),
        networkMonitor: FakeNetworkMonitor = FakeNetworkMonitor(online = true),
        runtimeDao: FakeDeviceRuntimeStateDao = FakeDeviceRuntimeStateDao(),
    ) = InstalledAppsSyncCoordinator(
        repository = mockk(relaxed = true),
        deviceRegistryClient = client,
        pairingStore = pairingStore,
        networkMonitor = networkMonitor,
        runtimeStateDao = runtimeDao,
        iconLoader = mockk(relaxed = true),
        iconEncoder = mockk(relaxed = true),
        logger = NoOpLogger,
    )

    private val apps = listOf(
        InstalledAppInfo("com.a", "A", isSystemApp = false, versionCode = 1),
        InstalledAppInfo("com.b", "B", isSystemApp = false, versionCode = 1),
    )

    @Test
    fun `uploads when never uploaded before`() = runTest {
        val client = FakeDeviceRegistryClient()
        val result = coordinator(client = client).syncIfNeeded(apps)
        assertTrue(result)
        assertEquals(2, client.lastUploaded?.size)
    }

    @Test
    fun `skips upload when inventory hash unchanged`() = runTest {
        val client = FakeDeviceRegistryClient()
        val runtimeDao = FakeDeviceRuntimeStateDao(
            initial = DeviceRuntimeStateEntity(lastUploadedAppsHash = apps.stableInventoryHash()),
        )
        val result = coordinator(client = client, runtimeDao = runtimeDao).syncIfNeeded(apps)
        assertTrue(result)
        assertEquals(null, client.lastUploaded)
    }

    @Test
    fun `does not upload while offline`() = runTest {
        val client = FakeDeviceRegistryClient()
        val result = coordinator(
            client = client,
            networkMonitor = FakeNetworkMonitor(online = false),
        ).syncIfNeeded(apps)
        assertFalse(result)
        assertEquals(null, client.lastUploaded)
    }

    @Test
    fun `does not upload when device is not paired`() = runTest {
        val client = FakeDeviceRegistryClient()
        val result = coordinator(client = client, pairingStore = FakePairingStore(null)).syncIfNeeded(apps)
        assertFalse(result)
        assertEquals(null, client.lastUploaded)
    }

    @Test
    fun `re-uploads after inventory changes`() = runTest {
        val client = FakeDeviceRegistryClient()
        val runtimeDao = FakeDeviceRuntimeStateDao(
            initial = DeviceRuntimeStateEntity(lastUploadedAppsHash = listOf(apps[0]).stableInventoryHash()),
        )
        val result = coordinator(client = client, runtimeDao = runtimeDao).syncIfNeeded(apps)
        assertTrue(result)
        assertEquals(2, client.lastUploaded?.size)
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

private class FakeDeviceRegistryClient : DeviceRegistryClient {
    var lastUploaded: List<InstalledAppSummary>? = null

    override suspend fun heartbeat(
        familyId: String,
        childId: String,
        deviceId: String,
        launcherDefault: Boolean,
        model: String,
        batteryPercent: Int?,
        osVersion: String?,
        appVersion: String?,
    ) = Unit

    override suspend fun uploadInstalledApps(
        familyId: String,
        childId: String,
        deviceId: String,
        apps: List<InstalledAppSummary>,
    ) {
        lastUploaded = apps
    }

    override suspend fun fetchStatus(familyId: String, childId: String, deviceId: String): DeviceRemoteStatus =
        DeviceRemoteStatus(revoked = false)

    override fun observeStatus(
        familyId: String,
        childId: String,
        deviceId: String,
    ): kotlinx.coroutines.flow.Flow<DeviceRemoteStatus> =
        kotlinx.coroutines.flow.flowOf(DeviceRemoteStatus(revoked = false))

    override suspend fun registerPushToken(familyId: String, childId: String, deviceId: String, token: String) =
        Unit
}

private class FakeDeviceRuntimeStateDao(initial: DeviceRuntimeStateEntity? = null) : DeviceRuntimeStateDao {
    private var state = initial
    override fun observe(): Flow<DeviceRuntimeStateEntity?> = flowOf(state)
    override suspend fun get(): DeviceRuntimeStateEntity? = state
    override suspend fun upsert(entity: DeviceRuntimeStateEntity) {
        state = entity
    }
    override suspend fun clear() {
        state = null
    }
}

private object NoOpLogger : AppLogger {
    override fun d(message: String, vararg extras: Pair<String, Any?>) = Unit
    override fun i(message: String, vararg extras: Pair<String, Any?>) = Unit
    override fun w(message: String, throwable: Throwable?, vararg extras: Pair<String, Any?>) = Unit
    override fun e(message: String, throwable: Throwable?, vararg extras: Pair<String, Any?>) = Unit
}
