package com.meritscreen.core.firebase.child

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.domain.ExplorerProgressUpload
import com.meritscreen.core.common.domain.QuizAttemptUpload
import com.meritscreen.core.common.domain.SkillStateUpload
import com.meritscreen.core.common.domain.StickerUnlockUpload
import com.meritscreen.core.common.domain.UsageDayUpload
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.firebase.error.FirebaseErrorMapper
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreChildUsageRemoteClient @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val dispatchers: AppDispatchers,
) : ChildUsageRemoteClient {

    override suspend fun uploadUsageDay(familyId: String, childId: String, upload: UsageDayUpload) {
        runRemote {
            childRef(familyId, childId).collection("usageDays").document(upload.day).set(
                mapOf(
                    "minutesUsed" to upload.minutesUsed,
                    "minutesByApp" to upload.minutesByApp,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
        }
    }

    override suspend fun uploadQuizAttempt(familyId: String, childId: String, upload: QuizAttemptUpload) {
        runRemote {
            val ref = childRef(familyId, childId).collection("quizAttempts").document(upload.attemptId)
            // quizAttempts is append-only server-side: a second write to an existing doc is
            // rejected as an "update" by firestore.rules. If it already exists, the previous
            // attempt already landed, so treat this retry as already-synced rather than error.
            if (ref.get().await().exists()) return@runRemote
            ref.set(
                mapOf(
                    "createdAt" to com.google.firebase.Timestamp(upload.createdAtEpochMs / 1000, 0),
                    "topics" to upload.topics,
                    "score" to upload.score,
                    "total" to upload.total,
                    "passed" to upload.passed,
                    "extraMinutesGranted" to upload.extraMinutesGranted,
                ),
            ).await()
        }
    }

    override suspend fun uploadSkillState(familyId: String, childId: String, skills: List<SkillStateUpload>) {
        runRemote {
            val byTopic = skills.associate { skill ->
                skill.topic to mapOf(
                    "level" to skill.level,
                    "streakCorrect" to skill.streakCorrect,
                    "weak" to skill.weak,
                    "weakConcepts" to skill.weakConcepts,
                    "weakConceptTitles" to skill.weakConceptTitles,
                    "masteredConcepts" to skill.masteredConcepts,
                    "tierLabel" to skill.tierLabel,
                    "totalAttempts" to skill.totalAttempts,
                    "totalCorrect" to skill.totalCorrect,
                    "avgResponseTimeMs" to skill.avgResponseTimeMs,
                )
            }
            childRef(familyId, childId).collection("skillState").document("current").set(
                mapOf(
                    "topics" to byTopic,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
        }
    }

    override suspend fun uploadStickerUnlock(
        familyId: String,
        childId: String,
        upload: StickerUnlockUpload,
    ) {
        runRemote {
            val ref = childRef(familyId, childId).collection("stickerUnlocks").document(upload.unlockId)
            if (ref.get().await().exists()) return@runRemote
            ref.set(
                mapOf(
                    "stickerId" to upload.stickerId,
                    "stage" to upload.stage,
                    "title" to upload.title,
                    "emoji" to upload.emoji,
                    "source" to upload.source,
                    "attemptId" to upload.attemptId,
                    "unlockedAt" to com.google.firebase.Timestamp(upload.unlockedAtEpochMs / 1000, 0),
                    "xpAtUnlock" to upload.xpAtUnlock,
                ),
            ).await()
        }
    }

    override suspend fun uploadExplorerProgress(
        familyId: String,
        childId: String,
        upload: ExplorerProgressUpload,
    ) {
        runRemote {
            childRef(familyId, childId).collection("explorerProgress").document("current").set(
                mapOf(
                    "xp" to upload.xp,
                    "explorerLevel" to upload.explorerLevel,
                    "passStreakDays" to upload.passStreakDays,
                    "lastPassDayKey" to upload.lastPassDayKey,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedAtEpochMs" to upload.updatedAtEpochMs,
                ),
                SetOptions.merge(),
            ).await()
        }
    }

    private fun childRef(familyId: String, childId: String) =
        firestore.collection(FAMILIES).document(familyId).collection(CHILDREN).document(childId)

    private suspend fun <T> runRemote(block: suspend () -> T): T = try {
        withContext(dispatchers.io) { block() }
    } catch (error: AppErrorException) {
        throw error
    } catch (error: Throwable) {
        throw AppErrorException(FirebaseErrorMapper.from(error), error)
    }

    private companion object {
        const val FAMILIES = "families"
        const val CHILDREN = "children"
    }
}
