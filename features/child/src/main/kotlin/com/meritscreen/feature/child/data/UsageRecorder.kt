package com.meritscreen.feature.child.data

import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.database.child.UsageDao
import com.meritscreen.core.database.child.UsageDayEntity
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records per-app-per-day usage locally for later upload (Phase 7). Deliberately decoupled
 * from [com.meritscreen.feature.child.domain.SessionEngine] / `SessionSnapshot` — the
 * session engine's own `minutesUsedToday` continues to drive block/quiz timing; this table
 * exists only to give the sync layer a per-app breakdown to upload to `usageDays/{day}`.
 *
 * Called from [ChildSessionController.tick] only when a whole minute has just elapsed (that
 * call site already throttles to roughly once a minute), so this never turns into a
 * per-second Room write.
 */
@Singleton
class UsageRecorder @Inject constructor(
    private val usageDao: UsageDao,
    private val dispatchers: AppDispatchers,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun record(childId: String, day: String, packageName: String, deltaMinutes: Int) {
        if (deltaMinutes <= 0 || packageName.isBlank()) return
        withContext(dispatchers.io) {
            val existing = usageDao.getDay(childId, day)
            val minutesByApp = decode(existing?.minutesByAppJson).toMutableMap()
            minutesByApp[packageName] = (minutesByApp[packageName] ?: 0) + deltaMinutes
            usageDao.upsertDay(
                UsageDayEntity(
                    childId = childId,
                    day = day,
                    minutesUsed = minutesByApp.values.sum(),
                    minutesByAppJson = encode(minutesByApp),
                    updatedAtEpochMs = System.currentTimeMillis(),
                    dirty = true,
                ),
            )
        }
    }

    fun decode(rawJson: String?): Map<String, Int> {
        if (rawJson.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), rawJson)
        }.getOrDefault(emptyMap())
    }

    private fun encode(minutesByApp: Map<String, Int>): String =
        json.encodeToString(MapSerializer(String.serializer(), Int.serializer()), minutesByApp)
}
