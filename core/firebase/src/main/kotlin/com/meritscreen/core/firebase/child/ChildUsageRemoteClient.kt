package com.meritscreen.core.firebase.child

import com.meritscreen.core.common.domain.ExplorerProgressUpload
import com.meritscreen.core.common.domain.QuizAttemptUpload
import com.meritscreen.core.common.domain.SkillStateUpload
import com.meritscreen.core.common.domain.StickerUnlockUpload
import com.meritscreen.core.common.domain.UsageDayUpload

/**
 * Child-device write path for usage/quiz/skill/sticker sync (Phase 7). Mirrors
 * `DeviceRegistryClient`'s pattern: writes are scoped to this child's own subtree and
 * enforced server-side by `firestore.rules`, not just by client code.
 *
 * Deliberately one-shot, called only from a bounded WorkManager worker — never from the
 * Home/quiz render path.
 */
interface ChildUsageRemoteClient {
    /** Idempotent: always overwrites with this device's local-absolute totals for the day. */
    suspend fun uploadUsageDay(familyId: String, childId: String, upload: UsageDayUpload)

    /**
     * Idempotent via a client-generated, deterministic [QuizAttemptUpload.attemptId]: if the
     * doc already exists (e.g. a retried upload after a dropped ack), this is a no-op rather
     * than a rules-rejected update, since `quizAttempts` is append-only.
     */
    suspend fun uploadQuizAttempt(familyId: String, childId: String, upload: QuizAttemptUpload)

    /** Full replace of this child's skill snapshot — latest local state wins, per docs/03. */
    suspend fun uploadSkillState(familyId: String, childId: String, skills: List<SkillStateUpload>)

    /**
     * Append-only sticker unlock. Idempotent by [StickerUnlockUpload.unlockId]: if the doc
     * already exists, treat as already synced (same pattern as quizAttempts).
     */
    suspend fun uploadStickerUnlock(familyId: String, childId: String, upload: StickerUnlockUpload)

    /** Merge explorer XP/level progress — latest local state wins. */
    suspend fun uploadExplorerProgress(familyId: String, childId: String, upload: ExplorerProgressUpload)
}
