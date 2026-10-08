package com.meritscreen.feature.child.data

import com.meritscreen.core.common.domain.SessionPhase
import com.meritscreen.core.database.child.SessionStateDao
import com.meritscreen.core.database.child.SessionStateEntity
import com.meritscreen.feature.child.domain.SessionSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChildSessionRepository @Inject constructor(
    private val dao: SessionStateDao,
) {
    fun observe(childId: String): Flow<SessionSnapshot> =
        dao.observe(childId).map { it?.toDomain() ?: SessionSnapshot() }

    suspend fun get(childId: String): SessionSnapshot =
        if (childId.isBlank()) SessionSnapshot() else dao.get(childId)?.toDomain() ?: SessionSnapshot()

    suspend fun save(childId: String, snapshot: SessionSnapshot) {
        if (childId.isBlank()) return
        dao.upsert(snapshot.toEntity(childId))
    }

    suspend fun clearForChild(childId: String) {
        if (childId.isBlank()) return
        dao.clearForChild(childId)
    }
}

private fun SessionStateEntity.toDomain(): SessionSnapshot = SessionSnapshot(
    phase = runCatching { SessionPhase.valueOf(phase) }.getOrDefault(SessionPhase.Idle),
    activePackage = activePackage,
    activeAppId = activeAppId,
    blockStartedElapsedMs = blockStartedElapsedMs,
    blockDurationMinutes = blockDurationMinutes,
    minutesAccruedInBlock = minutesAccruedInBlock,
    deviceShieldedUntilElapsedMs = deviceShieldedUntilElapsedMs,
    cooldownMinutes = cooldownMinutes,
    dayKey = dayKey,
    minutesUsedToday = minutesUsedToday,
    lastTickElapsedMs = lastTickElapsedMs,
    quizGraceUntilElapsedMs = quizGraceUntilElapsedMs,
)

private fun SessionSnapshot.toEntity(childId: String): SessionStateEntity = SessionStateEntity(
    childId = childId,
    phase = phase.name,
    activePackage = activePackage,
    activeAppId = activeAppId,
    blockStartedElapsedMs = blockStartedElapsedMs,
    blockDurationMinutes = blockDurationMinutes,
    minutesAccruedInBlock = minutesAccruedInBlock,
    deviceShieldedUntilElapsedMs = deviceShieldedUntilElapsedMs,
    cooldownMinutes = cooldownMinutes,
    dayKey = dayKey,
    minutesUsedToday = minutesUsedToday,
    lastTickElapsedMs = lastTickElapsedMs,
    quizGraceUntilElapsedMs = quizGraceUntilElapsedMs,
)
