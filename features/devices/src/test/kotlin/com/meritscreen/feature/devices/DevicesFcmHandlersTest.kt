package com.meritscreen.feature.devices

import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicesFcmHandlersTest {

    @Test
    fun `unpairs immediately on device_revoked and still schedules expedited heartbeat`() {
        val scheduler: DeviceHeartbeatScheduler = mockk(relaxed = true)
        val revocation: DeviceRevocationHandler = mockk(relaxed = true)
        val handled = DeviceRevokedFcmHandler(revocation, scheduler)
            .handle(mapOf("type" to "device_revoked"))
        assertTrue(handled)
        verify { scheduler.runOnceExpedited() }
        coVerify(timeout = 2_000) { revocation.onDeviceRevoked() }
    }

    @Test
    fun `ignores unrelated message types`() {
        val scheduler: DeviceHeartbeatScheduler = mockk(relaxed = true)
        val revocation: DeviceRevocationHandler = mockk(relaxed = true)
        val handled = DeviceRevokedFcmHandler(revocation, scheduler)
            .handle(mapOf("type" to "policy_sync"))
        assertFalse(handled)
        verify(exactly = 0) { scheduler.runOnceExpedited() }
        coVerify(exactly = 0) { revocation.onDeviceRevoked() }
    }

    @Test
    fun `a refreshed token schedules registration`() {
        val scheduler: PushTokenRegistrationScheduler = mockk(relaxed = true)
        DevicePushTokenRegistrar(scheduler).onTokenRefreshed("new-token")
        verify { scheduler.registerNow() }
    }
}
