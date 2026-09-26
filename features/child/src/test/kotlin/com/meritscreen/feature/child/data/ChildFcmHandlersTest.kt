package com.meritscreen.feature.child.data

import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildFcmHandlersTest {

    @Test
    fun `handles a policy_sync push and triggers in-process plus WorkManager sync`() = runTest {
        val scheduler: PolicySyncScheduler = mockk(relaxed = true)
        val coordinator: ChildPolicySyncCoordinator = mockk(relaxed = true)
        val handled = PolicySyncFcmHandler(scheduler, coordinator).handle(mapOf("type" to "policy_sync"))
        assertTrue(handled)
        verify { scheduler.runSoon() }
        coVerify(timeout = 1_000) { coordinator.refreshNow() }
    }

    @Test
    fun `handles a pin_sync push by reusing the policy sync worker`() = runTest {
        val scheduler: PolicySyncScheduler = mockk(relaxed = true)
        val coordinator: ChildPolicySyncCoordinator = mockk(relaxed = true)
        val handled = PinSyncFcmHandler(scheduler, coordinator).handle(mapOf("type" to "pin_sync"))
        assertTrue(handled)
        verify { scheduler.runSoon() }
        coVerify(timeout = 1_000) { coordinator.refreshNow() }
    }

    @Test
    fun `handles family_deleted by starting an unpair`() {
        val unpair: com.meritscreen.feature.child.domain.UnpairChildDeviceUseCase = mockk(relaxed = true)
        val lifecycle: ChildDeviceLifecycleCoordinator = mockk(relaxed = true)
        val handled = FamilyDeletedFcmHandler(unpair, lifecycle).handle(mapOf("type" to "family_deleted"))
        assertTrue(handled)
        coVerify(timeout = 1_000) { unpair() }
        verify(timeout = 1_000) { lifecycle.markForceSignedOut() }
    }

    @Test
    fun `ignores message types it does not own`() {
        val scheduler: PolicySyncScheduler = mockk(relaxed = true)
        val coordinator: ChildPolicySyncCoordinator = mockk(relaxed = true)
        val handled = PolicySyncFcmHandler(scheduler, coordinator).handle(mapOf("type" to "device_revoked"))
        assertFalse(handled)
        verify(exactly = 0) { scheduler.runSoon() }
    }

    @Test
    fun `ignores a message with no type field`() {
        val scheduler: PolicySyncScheduler = mockk(relaxed = true)
        val coordinator: ChildPolicySyncCoordinator = mockk(relaxed = true)
        assertFalse(PolicySyncFcmHandler(scheduler, coordinator).handle(emptyMap()))
    }
}
