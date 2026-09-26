package com.meritscreen.feature.child.data

import android.content.Context
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.database.child.PolicySyncStateDao
import com.meritscreen.core.database.child.PolicySyncStateEntity
import com.meritscreen.core.firebase.child.ChildRemotePolicyClient
import com.meritscreen.core.firebase.child.ChildRemoteProfile
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.security.pairing.ChildPairingCredential
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.core.testing.FakeNetworkMonitor
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7: no continuous Firestore listener — [ChildPolicySyncCoordinator] only ever does
 * bounded, one-shot pulls, so these tests exercise `refreshNow` / sync-state bookkeeping
 * directly rather than a long-lived subscription.
 */
class ChildPolicySyncCoordinatorTest {

    private val credential = ChildPairingCredential(
        familyId = "fam1",
        childId = "child1",
        deviceId = "dev1",
        parentPinHash = "hash",
    )

    private fun coordinator(
        remote: ChildRemotePolicyClient = FakeChildRemotePolicyClient(),
        pairingStore: ChildPairingStore = FakePolicyPairingStore(credential),
        networkMonitor: FakeNetworkMonitor = FakeNetworkMonitor(online = true),
        policySyncStateDao: PolicySyncStateDao = FakePolicySyncStateDao(),
        repository: ChildPolicyRepository = mockk(relaxed = true),
    ): ChildPolicySyncCoordinator = ChildPolicySyncCoordinator(
        context = mockk<Context>(relaxed = true),
        pairingStore = pairingStore,
        remote = remote,
        repository = repository,
        quizBankSeeder = mockk(relaxed = true),
        networkMonitor = networkMonitor,
        analyticsTracker = mockk(relaxed = true),
        policySyncStateDao = policySyncStateDao,
        policySyncScheduler = mockk(relaxed = true),
        quizPackGenerationScheduler = mockk(relaxed = true),
        dispatchers = AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined, main = Dispatchers.Unconfined),
    )

    @Test
    fun `refreshNow returns false when the device is not paired`() = runTest {
        assertFalse(coordinator(pairingStore = FakePolicyPairingStore(null)).refreshNow())
    }

    @Test
    fun `refreshNow returns false while offline and never touches Firestore`() = runTest {
        val remote = FakeChildRemotePolicyClient()
        val result = coordinator(remote = remote, networkMonitor = FakeNetworkMonitor(online = false)).refreshNow()
        assertFalse(result)
        assertEquals(0, remote.fetchCount)
    }

    @Test
    fun `refreshNow pulls policy and appRules into the repository on success`() = runTest {
        val repository: ChildPolicyRepository = mockk(relaxed = true)
        val remote = FakeChildRemotePolicyClient()
        val result = coordinator(remote = remote, repository = repository).refreshNow()
        assertTrue(result)
        coVerify { repository.savePolicy("fam1", "child1", remote.policy) }
        coVerify { repository.saveAppRules("child1", remote.rules) }
    }

    @Test
    fun `a failing remote is recorded as a failure without throwing`() = runTest {
        val dao = FakePolicySyncStateDao()
        val result = coordinator(remote = FakeChildRemotePolicyClient(shouldThrow = true), policySyncStateDao = dao)
            .refreshNow()
        assertFalse(result)
        assertEquals(1, dao.get("child1")?.consecutiveFailures)
    }

    @Test
    fun `consecutive failures accumulate and reset after a success`() = runTest {
        val dao = FakePolicySyncStateDao()
        val failing = coordinator(remote = FakeChildRemotePolicyClient(shouldThrow = true), policySyncStateDao = dao)
        failing.refreshNow()
        failing.refreshNow()
        assertEquals(2, dao.get("child1")?.consecutiveFailures)

        val healthy = coordinator(remote = FakeChildRemotePolicyClient(), policySyncStateDao = dao)
        healthy.refreshNow()
        assertEquals(0, dao.get("child1")?.consecutiveFailures)
    }

    @Test
    fun `lastSyncState reflects the most recent successful pull`() = runTest {
        val dao = FakePolicySyncStateDao()
        val target = coordinator(policySyncStateDao = dao)
        target.refreshNow()
        val state = target.lastSyncState()
        assertNotNull(state?.lastSuccessAtEpochMs)
        assertEquals(0, state?.consecutiveFailures)
    }
}

private class FakePolicyPairingStore(private var credential: ChildPairingCredential?) : ChildPairingStore {
    override suspend fun get(): ChildPairingCredential? = credential
    override suspend fun set(credential: ChildPairingCredential) {
        this.credential = credential
    }
    override suspend fun getOrCreateDeviceId(): String = credential?.deviceId ?: "generated"
    override suspend fun clear() {
        credential = null
    }
}

private class FakeChildRemotePolicyClient(private val shouldThrow: Boolean = false) : ChildRemotePolicyClient {
    val policy = ChildPolicy(defaultBlockMinutes = 45)
    val rules = listOf(AppRule(appId = "a", packageOrBundleId = "com.a"))
    var fetchCount = 0

    override suspend fun fetchPolicy(familyId: String, childId: String): ChildPolicy {
        fetchCount += 1
        if (shouldThrow) throw IllegalStateException("boom")
        return policy
    }

    override suspend fun fetchAppRules(familyId: String, childId: String): List<AppRule> {
        if (shouldThrow) throw IllegalStateException("boom")
        return rules
    }

    override suspend fun fetchProfile(familyId: String, childId: String): ChildRemoteProfile? = null
    override suspend fun fetchParentPinHash(familyId: String): String? = null
}

private class FakePolicySyncStateDao : PolicySyncStateDao {
    private val states = mutableMapOf<String, PolicySyncStateEntity>()
    override suspend fun get(childId: String): PolicySyncStateEntity? = states[childId]
    override suspend fun upsert(entity: PolicySyncStateEntity) {
        states[entity.childId] = entity
    }
    override suspend fun clear(childId: String) {
        states.remove(childId)
    }
}
