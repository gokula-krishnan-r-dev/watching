package com.meritscreen.core.database.child

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_day WHERE childId = :childId AND day = :day LIMIT 1")
    suspend fun getDay(childId: String, day: String): UsageDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(entity: UsageDayEntity)

    @Query("SELECT * FROM usage_day WHERE childId = :childId AND dirty = 1")
    suspend fun listDirty(childId: String): List<UsageDayEntity>

    @Query(
        "UPDATE usage_day SET dirty = 0, syncedAtEpochMs = :syncedAtEpochMs " +
            "WHERE childId = :childId AND day = :day",
    )
    suspend fun markSynced(childId: String, day: String, syncedAtEpochMs: Long)

    @Query("DELETE FROM usage_day WHERE childId = :childId")
    suspend fun clearForChild(childId: String)
}

@Dao
interface QuizAttemptDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: QuizAttemptEntity)

    @Query("SELECT * FROM quiz_attempt WHERE childId = :childId AND dirty = 1 ORDER BY createdAtEpochMs ASC")
    suspend fun listDirty(childId: String): List<QuizAttemptEntity>

    @Query(
        "UPDATE quiz_attempt SET dirty = 0, syncedAtEpochMs = :syncedAtEpochMs WHERE attemptId = :attemptId",
    )
    suspend fun markSynced(attemptId: String, syncedAtEpochMs: Long)

    /** Bounded local history; append-only remotely, but the device does not need to keep it forever. */
    @Query("DELETE FROM quiz_attempt WHERE childId = :childId AND dirty = 0 AND createdAtEpochMs < :beforeEpochMs")
    suspend fun deleteSyncedBefore(childId: String, beforeEpochMs: Long)

    @Query("DELETE FROM quiz_attempt WHERE childId = :childId")
    suspend fun clearForChild(childId: String)
}

@Dao
interface PolicySyncStateDao {
    @Query("SELECT * FROM policy_sync_state WHERE childId = :childId LIMIT 1")
    suspend fun get(childId: String): PolicySyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PolicySyncStateEntity)

    @Query("DELETE FROM policy_sync_state WHERE childId = :childId")
    suspend fun clear(childId: String)
}
