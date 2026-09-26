package com.meritscreen.core.database.child

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "child_sticker_unlock")
data class ChildStickerUnlockEntity(
    @PrimaryKey val unlockId: String,
    val childId: String,
    val stickerId: String,
    val stage: String,
    val title: String,
    val emoji: String,
    val source: String,
    val attemptId: String?,
    val unlockedAtEpochMs: Long,
    val xpAtUnlock: Int,
    val dirty: Boolean = true,
    val syncedAtEpochMs: Long? = null,
)

@Entity(tableName = "child_explorer_progress")
data class ChildExplorerProgressEntity(
    @PrimaryKey val childId: String,
    val xp: Int = 0,
    val explorerLevel: Int = 1,
    val passStreakDays: Int = 0,
    val lastPassDayKey: String? = null,
    val dirty: Boolean = true,
    val updatedAtEpochMs: Long = 0L,
)

@Dao
interface ChildStickerDao {
    @Query("SELECT * FROM child_sticker_unlock WHERE childId = :childId ORDER BY unlockedAtEpochMs DESC")
    suspend fun listUnlocks(childId: String): List<ChildStickerUnlockEntity>

    @Query("SELECT stickerId FROM child_sticker_unlock WHERE childId = :childId")
    suspend fun listOwnedStickerIds(childId: String): List<String>

    @Query("SELECT COUNT(*) FROM child_sticker_unlock WHERE childId = :childId")
    suspend fun countUnlocks(childId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM child_sticker_unlock
        WHERE childId = :childId AND unlockedAtEpochMs >= :sinceEpochMs
        """,
    )
    suspend fun countUnlocksSince(childId: String, sinceEpochMs: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUnlock(entity: ChildStickerUnlockEntity): Long

    @Query("SELECT * FROM child_sticker_unlock WHERE childId = :childId AND dirty = 1")
    suspend fun listDirtyUnlocks(childId: String): List<ChildStickerUnlockEntity>

    @Query(
        """
        UPDATE child_sticker_unlock
        SET dirty = 0, syncedAtEpochMs = :syncedAtEpochMs
        WHERE unlockId = :unlockId
        """,
    )
    suspend fun markUnlockSynced(unlockId: String, syncedAtEpochMs: Long)

    @Query("SELECT * FROM child_explorer_progress WHERE childId = :childId LIMIT 1")
    suspend fun getProgress(childId: String): ChildExplorerProgressEntity?

    @Query("SELECT * FROM child_explorer_progress WHERE childId = :childId LIMIT 1")
    fun observeProgress(childId: String): Flow<ChildExplorerProgressEntity?>

    @Query("SELECT COUNT(*) FROM child_sticker_unlock WHERE childId = :childId")
    fun observeUnlockCount(childId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(entity: ChildExplorerProgressEntity)

    @Query("SELECT * FROM child_explorer_progress WHERE dirty = 1")
    suspend fun listDirtyProgress(): List<ChildExplorerProgressEntity>

    @Query(
        """
        UPDATE child_explorer_progress
        SET dirty = 0, updatedAtEpochMs = :updatedAtEpochMs
        WHERE childId = :childId
        """,
    )
    suspend fun markProgressSynced(childId: String, updatedAtEpochMs: Long)
}
