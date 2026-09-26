package com.meritscreen.feature.child.data

import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.ExplorerLevelCalculator
import com.meritscreen.core.common.domain.ExplorerProgress
import com.meritscreen.core.common.domain.StickerCatalog
import com.meritscreen.core.common.domain.StickerDefinition
import com.meritscreen.core.common.domain.StickerStage
import com.meritscreen.core.common.domain.StickerUnlock
import com.meritscreen.core.common.domain.StickerUnlockSource
import com.meritscreen.core.common.domain.StickerUnlockUpload
import com.meritscreen.core.common.domain.ExplorerProgressUpload
import com.meritscreen.core.database.child.ChildExplorerProgressEntity
import com.meritscreen.core.database.child.ChildStickerDao
import com.meritscreen.core.database.child.ChildStickerUnlockEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class StickerAwardResult(
    val progress: ExplorerProgress,
    val unlocked: StickerUnlock?,
    val xpGained: Int,
    val leveledUp: Boolean,
    val weekendBonusApplied: Boolean,
)

@Singleton
class StickerRepository @Inject constructor(
    private val dao: ChildStickerDao,
    private val dispatchers: AppDispatchers,
) {
    suspend fun getProgress(childId: String): ExplorerProgress = withContext(dispatchers.io) {
        dao.getProgress(childId)?.toDomain() ?: ExplorerProgress()
    }

    fun observeProgress(childId: String): Flow<ExplorerProgress> =
        dao.observeProgress(childId).map { it?.toDomain() ?: ExplorerProgress() }

    fun observeUnlockCount(childId: String): Flow<Int> = dao.observeUnlockCount(childId)

    suspend fun listUnlocks(childId: String): List<StickerUnlock> = withContext(dispatchers.io) {
        dao.listUnlocks(childId).map { it.toDomain() }
    }

    suspend fun countUnlocks(childId: String): Int = withContext(dispatchers.io) {
        dao.countUnlocks(childId)
    }

    suspend fun countUnlocksSince(childId: String, sinceEpochMs: Long): Int =
        withContext(dispatchers.io) {
            dao.countUnlocksSince(childId, sinceEpochMs)
        }

    /**
     * Awards XP + at most one new sticker after a passed quiz.
     * Room-only; upload happens via [UsageSyncCoordinator].
     */
    suspend fun awardQuizPass(
        childId: String,
        policy: ChildPolicy,
        attemptId: String?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): StickerAwardResult = withContext(dispatchers.io) {
        if (!policy.rewardsEnabled) {
            return@withContext StickerAwardResult(
                progress = getProgress(childId),
                unlocked = null,
                xpGained = 0,
                leveledUp = false,
                weekendBonusApplied = false,
            )
        }

        val today = LocalDate.now(zoneId)
        val dayKey = today.toString()
        val isWeekend = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
        val weekendBonus = policy.weekendBonusEnabled && isWeekend
        var xpGain = AppConfig.STICKER_QUIZ_PASS_XP
        if (weekendBonus) xpGain += AppConfig.STICKER_WEEKEND_BONUS_XP

        val previous = dao.getProgress(childId)?.toDomain() ?: ExplorerProgress()
        val streak = when {
            previous.lastPassDayKey == dayKey -> previous.passStreakDays
            previous.lastPassDayKey == today.minusDays(1).toString() -> previous.passStreakDays + 1
            else -> 1
        }
        val newXp = previous.xp + xpGain
        val newLevel = ExplorerLevelCalculator.levelForXp(newXp)
        val leveledUp = newLevel > previous.explorerLevel

        dao.upsertProgress(
            ChildExplorerProgressEntity(
                childId = childId,
                xp = newXp,
                explorerLevel = newLevel,
                passStreakDays = streak,
                lastPassDayKey = dayKey,
                dirty = true,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )

        val owned = dao.listOwnedStickerIds(childId).toSet()
        val next = StickerCatalog.nextUnlockable(newLevel, owned)
        val unlocked = if (next != null) {
            val source = if (weekendBonus) StickerUnlockSource.WEEKEND_BONUS else StickerUnlockSource.QUIZ_PASS
            val unlockId = "stk_${childId}_${next.stickerId}"
            val entity = ChildStickerUnlockEntity(
                unlockId = unlockId,
                childId = childId,
                stickerId = next.stickerId,
                stage = next.stage.name,
                title = next.title,
                emoji = next.emoji,
                source = source.name,
                attemptId = attemptId,
                unlockedAtEpochMs = System.currentTimeMillis(),
                xpAtUnlock = newXp,
                dirty = true,
            )
            val inserted = dao.insertUnlock(entity)
            if (inserted != -1L) entity.toDomain() else null
        } else {
            null
        }

        StickerAwardResult(
            progress = ExplorerProgress(
                xp = newXp,
                explorerLevel = newLevel,
                passStreakDays = streak,
                lastPassDayKey = dayKey,
            ),
            unlocked = unlocked,
            xpGained = xpGain,
            leveledUp = leveledUp,
            weekendBonusApplied = weekendBonus,
        )
    }

    suspend fun dirtyUnlocksForUpload(childId: String): List<StickerUnlockUpload> =
        withContext(dispatchers.io) {
            dao.listDirtyUnlocks(childId).map {
                StickerUnlockUpload(
                    unlockId = it.unlockId,
                    stickerId = it.stickerId,
                    stage = it.stage,
                    title = it.title,
                    emoji = it.emoji,
                    source = it.source,
                    attemptId = it.attemptId,
                    unlockedAtEpochMs = it.unlockedAtEpochMs,
                    xpAtUnlock = it.xpAtUnlock,
                )
            }
        }

    suspend fun markUnlockSynced(unlockId: String) = withContext(dispatchers.io) {
        dao.markUnlockSynced(unlockId, System.currentTimeMillis())
    }

    suspend fun dirtyProgressForUpload(): List<Pair<String, ExplorerProgressUpload>> =
        withContext(dispatchers.io) {
            dao.listDirtyProgress().map { entity ->
                entity.childId to ExplorerProgressUpload(
                    xp = entity.xp,
                    explorerLevel = entity.explorerLevel,
                    passStreakDays = entity.passStreakDays,
                    lastPassDayKey = entity.lastPassDayKey,
                    updatedAtEpochMs = entity.updatedAtEpochMs,
                )
            }
        }

    suspend fun markProgressSynced(childId: String) = withContext(dispatchers.io) {
        dao.markProgressSynced(childId, System.currentTimeMillis())
    }

    fun catalogGrouped(): Map<StickerStage, List<StickerDefinition>> =
        StickerStage.entries.associateWith { StickerCatalog.forStage(it) }
}

private fun ChildExplorerProgressEntity.toDomain() = ExplorerProgress(
    xp = xp,
    explorerLevel = explorerLevel,
    passStreakDays = passStreakDays,
    lastPassDayKey = lastPassDayKey,
)

private fun ChildStickerUnlockEntity.toDomain() = StickerUnlock(
    unlockId = unlockId,
    stickerId = stickerId,
    stage = runCatching { StickerStage.valueOf(stage) }.getOrDefault(StickerStage.SPROUT),
    title = title,
    emoji = emoji,
    source = runCatching { StickerUnlockSource.valueOf(source) }.getOrDefault(StickerUnlockSource.QUIZ_PASS),
    attemptId = attemptId,
    unlockedAtEpochMs = unlockedAtEpochMs,
    xpAtUnlock = xpAtUnlock,
)
