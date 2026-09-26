package com.meritscreen.feature.child.data

import com.meritscreen.core.common.domain.ExplorerProgressUpload
import com.meritscreen.core.common.domain.QuizAttemptUpload
import com.meritscreen.core.common.domain.SkillStateUpload
import com.meritscreen.core.common.domain.StickerUnlockUpload
import com.meritscreen.core.common.domain.UsageDayUpload
import com.meritscreen.core.database.child.UsageDayEntity
import com.meritscreen.core.firebase.child.ChildUsageRemoteClient
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.security.pairing.ChildPairingCredential
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.core.testing.FakeNetworkMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSyncCoordinatorTest {

    private val credential = ChildPairingCredential(
        familyId = "fam1",
        childId = "child1",
        deviceId = "dev1",
        parentPinHash = "hash",
    )

    private fun coordinator(
        pairingStore: ChildPairingStore = FakeUsagePairingStore(credential),
        networkMonitor: FakeNetworkMonitor = FakeNetworkMonitor(online = true),
        usageDao: FakeUsageDao = FakeUsageDao(),
        remote: FakeChildUsageRemoteClient = FakeChildUsageRemoteClient(),
        quizAttemptRepository: QuizAttemptRepository = mockk(relaxed = true),
        quizRepository: QuizRepository = mockk(relaxed = true),
        stickerRepository: StickerRepository = mockk(relaxed = true),
    ): UsageSyncCoordinator {
        val dispatchers = AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined, main = Dispatchers.Unconfined)
        return UsageSyncCoordinator(
            pairingStore = pairingStore,
            networkMonitor = networkMonitor,
            usageDao = usageDao,
            usageRecorder = UsageRecorder(usageDao, dispatchers),
            quizAttemptRepository = quizAttemptRepository,
            quizRepository = quizRepository,
            stickerRepository = stickerRepository,
            remote = remote,
            dispatchers = dispatchers,
        )
    }

    private fun dirtyDay() = UsageDayEntity(
        childId = "child1",
        day = "2026-01-01",
        minutesUsed = 5,
        minutesByAppJson = """{"com.a":5}""",
        updatedAtEpochMs = 1L,
        dirty = true,
    )

    @Test
    fun `syncNow succeeds with nothing to do when unpaired`() = runTest {
        assertTrue(coordinator(pairingStore = FakeUsagePairingStore(null)).syncNow())
    }

    @Test
    fun `syncNow returns false while offline and uploads nothing`() = runTest {
        val usageDao = FakeUsageDao().apply { put(dirtyDay()) }
        val remote = FakeChildUsageRemoteClient()
        val result = coordinator(
            networkMonitor = FakeNetworkMonitor(online = false),
            usageDao = usageDao,
            remote = remote,
        ).syncNow()
        assertFalse(result)
        assertTrue(remote.uploadedUsageDays.isEmpty())
    }

    @Test
    fun `uploads dirty usage days and marks them synced`() = runTest {
        val usageDao = FakeUsageDao().apply { put(dirtyDay()) }
        val remote = FakeChildUsageRemoteClient()
        val result = coordinator(usageDao = usageDao, remote = remote).syncNow()
        assertTrue(result)
        assertEquals(1, remote.uploadedUsageDays.size)
        assertEquals(UsageDayUpload("2026-01-01", 5, mapOf("com.a" to 5)), remote.uploadedUsageDays.single())
        assertFalse(usageDao.getDay("child1", "2026-01-01")!!.dirty)
    }

    @Test
    fun `a failed usage upload leaves the row dirty and reports overall failure`() = runTest {
        val usageDao = FakeUsageDao().apply { put(dirtyDay()) }
        val remote = FakeChildUsageRemoteClient(shouldThrow = true)
        val result = coordinator(usageDao = usageDao, remote = remote).syncNow()
        assertFalse(result)
        assertTrue(usageDao.getDay("child1", "2026-01-01")!!.dirty)
    }

    @Test
    fun `uploads pending quiz attempts and marks them synced`() = runTest {
        val attempt = QuizAttemptUpload("attempt1", 1L, listOf("math"), 3, 3, true, 5)
        val quizAttemptRepository: QuizAttemptRepository = mockk {
            coEvery { dirtyForUpload("child1") } returns listOf(attempt)
            coEvery { markSynced("attempt1") } returns Unit
        }
        val remote = FakeChildUsageRemoteClient()
        val result = coordinator(quizAttemptRepository = quizAttemptRepository, remote = remote).syncNow()
        assertTrue(result)
        assertEquals(listOf(attempt), remote.uploadedAttempts)
        coVerify { quizAttemptRepository.markSynced("attempt1") }
    }

    @Test
    fun `uploads dirty skill state as one batch and marks it all synced`() = runTest {
        val skills = listOf(SkillStateUpload("math", level = 3, streakCorrect = 2, weak = false))
        val quizRepository: QuizRepository = mockk {
            coEvery { dirtySkillsForUpload("child1") } returns skills
            coEvery { markSkillsSynced("child1") } returns Unit
        }
        val remote = FakeChildUsageRemoteClient()
        val result = coordinator(quizRepository = quizRepository, remote = remote).syncNow()
        assertTrue(result)
        assertEquals(skills, remote.uploadedSkills)
        coVerify { quizRepository.markSkillsSynced("child1") }
    }

    @Test
    fun `skips the skill upload entirely when nothing is dirty`() = runTest {
        val quizRepository: QuizRepository = mockk {
            coEvery { dirtySkillsForUpload("child1") } returns emptyList()
        }
        val remote = FakeChildUsageRemoteClient()
        coordinator(quizRepository = quizRepository, remote = remote).syncNow()
        assertTrue(remote.uploadedSkills.isEmpty())
        coVerify(exactly = 0) { quizRepository.markSkillsSynced(any()) }
    }
}

private class FakeUsagePairingStore(private var credential: ChildPairingCredential?) : ChildPairingStore {
    override suspend fun get(): ChildPairingCredential? = credential
    override suspend fun set(credential: ChildPairingCredential) {
        this.credential = credential
    }
    override suspend fun getOrCreateDeviceId(): String = credential?.deviceId ?: "generated"
    override suspend fun clear() {
        credential = null
    }
}

internal class FakeChildUsageRemoteClient(private val shouldThrow: Boolean = false) : ChildUsageRemoteClient {
    val uploadedUsageDays = mutableListOf<UsageDayUpload>()
    val uploadedAttempts = mutableListOf<QuizAttemptUpload>()
    val uploadedSkills = mutableListOf<SkillStateUpload>()
    val uploadedStickers = mutableListOf<StickerUnlockUpload>()
    val uploadedProgress = mutableListOf<ExplorerProgressUpload>()

    override suspend fun uploadUsageDay(familyId: String, childId: String, upload: UsageDayUpload) {
        if (shouldThrow) throw IllegalStateException("boom")
        uploadedUsageDays += upload
    }

    override suspend fun uploadQuizAttempt(familyId: String, childId: String, upload: QuizAttemptUpload) {
        if (shouldThrow) throw IllegalStateException("boom")
        uploadedAttempts += upload
    }

    override suspend fun uploadSkillState(familyId: String, childId: String, skills: List<SkillStateUpload>) {
        if (shouldThrow) throw IllegalStateException("boom")
        uploadedSkills += skills
    }

    override suspend fun uploadStickerUnlock(familyId: String, childId: String, upload: StickerUnlockUpload) {
        if (shouldThrow) throw IllegalStateException("boom")
        uploadedStickers += upload
    }

    override suspend fun uploadExplorerProgress(
        familyId: String,
        childId: String,
        upload: ExplorerProgressUpload,
    ) {
        if (shouldThrow) throw IllegalStateException("boom")
        uploadedProgress += upload
    }
}
