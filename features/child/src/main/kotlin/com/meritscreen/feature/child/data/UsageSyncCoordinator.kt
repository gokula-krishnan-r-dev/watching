package com.meritscreen.feature.child.data

import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.domain.UsageDayUpload
import com.meritscreen.core.database.child.UsageDao
import com.meritscreen.core.firebase.child.ChildUsageRemoteClient
import com.meritscreen.core.network.NetworkMonitor
import com.meritscreen.core.security.pairing.ChildPairingStore
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads this device's pending usage days, quiz attempts, skill-state, and sticker
 * deltas to Firestore (Phase 7 "child Room → WorkManager → Firestore" direction from
 * docs/03). Runs only from [UsageSyncWorker] — never from the Home/quiz render path —
 * and every write is idempotent by construction (see [ChildUsageRemoteClient]), so a
 * WorkManager retry after a dropped ack never double-counts.
 */
@Singleton
class UsageSyncCoordinator @Inject constructor(
    private val pairingStore: ChildPairingStore,
    private val networkMonitor: NetworkMonitor,
    private val usageDao: UsageDao,
    private val usageRecorder: UsageRecorder,
    private val quizAttemptRepository: QuizAttemptRepository,
    private val quizRepository: QuizRepository,
    private val stickerRepository: StickerRepository,
    private val remote: ChildUsageRemoteClient,
    private val dispatchers: AppDispatchers,
) {
    /** @return true if every pending item uploaded successfully (or there was nothing to do). */
    suspend fun syncNow(): Boolean = withContext(dispatchers.io) {
        val credential = pairingStore.get() ?: return@withContext true
        if (!networkMonitor.isCurrentlyOnline()) return@withContext false

        var allSucceeded = true
        allSucceeded = uploadUsageDays(credential.familyId, credential.childId) && allSucceeded
        allSucceeded = uploadQuizAttempts(credential.familyId, credential.childId) && allSucceeded
        allSucceeded = uploadSkillState(credential.familyId, credential.childId) && allSucceeded
        allSucceeded = uploadStickerUnlocks(credential.familyId, credential.childId) && allSucceeded
        allSucceeded = uploadExplorerProgress(credential.familyId) && allSucceeded
        allSucceeded
    }

    private suspend fun uploadUsageDays(familyId: String, childId: String): Boolean {
        var ok = true
        for (day in usageDao.listDirty(childId)) {
            val succeeded = runCatching {
                remote.uploadUsageDay(
                    familyId,
                    childId,
                    UsageDayUpload(
                        day = day.day,
                        minutesUsed = day.minutesUsed,
                        minutesByApp = usageRecorder.decode(day.minutesByAppJson),
                    ),
                )
            }.isSuccess
            if (succeeded) {
                usageDao.markSynced(childId, day.day, System.currentTimeMillis())
            } else {
                ok = false
            }
        }
        return ok
    }

    private suspend fun uploadQuizAttempts(familyId: String, childId: String): Boolean {
        var ok = true
        for (attempt in quizAttemptRepository.dirtyForUpload(childId)) {
            val succeeded = runCatching { remote.uploadQuizAttempt(familyId, childId, attempt) }.isSuccess
            if (succeeded) quizAttemptRepository.markSynced(attempt.attemptId) else ok = false
        }
        return ok
    }

    private suspend fun uploadSkillState(familyId: String, childId: String): Boolean {
        val dirty = quizRepository.dirtySkillsForUpload(childId)
        if (dirty.isEmpty()) return true
        val succeeded = runCatching { remote.uploadSkillState(familyId, childId, dirty) }.isSuccess
        if (succeeded) quizRepository.markSkillsSynced(childId)
        return succeeded
    }

    private suspend fun uploadStickerUnlocks(familyId: String, childId: String): Boolean {
        var ok = true
        for (unlock in stickerRepository.dirtyUnlocksForUpload(childId)) {
            val succeeded = runCatching {
                remote.uploadStickerUnlock(familyId, childId, unlock)
            }.isSuccess
            if (succeeded) stickerRepository.markUnlockSynced(unlock.unlockId) else ok = false
        }
        return ok
    }

    private suspend fun uploadExplorerProgress(familyId: String): Boolean {
        var ok = true
        for ((childId, progress) in stickerRepository.dirtyProgressForUpload()) {
            val succeeded = runCatching {
                remote.uploadExplorerProgress(familyId, childId, progress)
            }.isSuccess
            if (succeeded) stickerRepository.markProgressSynced(childId) else ok = false
        }
        return ok
    }
}
