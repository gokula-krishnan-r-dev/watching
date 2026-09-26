package com.meritscreen.core.database.child

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One local-absolute usage snapshot for (childId, day). `minutesByAppJson` is a flat
 * `{packageName: minutes}` JSON object. `dirty` marks rows the upload worker still needs to
 * push to `usageDays/{day}`; the row itself is the child's local source of truth, so a
 * repeated upload of the same snapshot is always safe (see [com.meritscreen.core.common.domain.UsageDayUpload]).
 */
@Entity(tableName = "usage_day", primaryKeys = ["childId", "day"])
data class UsageDayEntity(
    val childId: String,
    val day: String,
    val minutesUsed: Int,
    val minutesByAppJson: String,
    val updatedAtEpochMs: Long,
    val dirty: Boolean = true,
    val syncedAtEpochMs: Long? = null,
)

/**
 * One completed quiz session (append-only, both locally and in Firestore). `topicsCsv` is a
 * comma-separated list of topics covered in that session, kept simple because parent-facing
 * quiz analytics land in Phase 8 — this table exists to make the *sync plumbing* correct now.
 */
@Entity(tableName = "quiz_attempt")
data class QuizAttemptEntity(
    @PrimaryKey val attemptId: String,
    val childId: String,
    val createdAtEpochMs: Long,
    val topicsCsv: String,
    val score: Int,
    val total: Int,
    val passed: Boolean,
    val extraMinutesGranted: Int,
    val dirty: Boolean = true,
    val syncedAtEpochMs: Long? = null,
)

/**
 * Bookkeeping for the policy pull pipeline: when we last tried, whether it worked, and how
 * many times in a row it has failed (feeds backoff / "sync failed" UI hints without adding
 * Firestore listeners — see ARCHITECTURE.md "Real-time synchronization (Phase 7)").
 */
@Entity(tableName = "policy_sync_state")
data class PolicySyncStateEntity(
    @PrimaryKey val childId: String,
    val lastAttemptAtEpochMs: Long,
    val lastSuccessAtEpochMs: Long?,
    val consecutiveFailures: Int,
)
