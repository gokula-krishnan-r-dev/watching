package com.meritscreen.feature.child.data

import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.database.child.UsageDao
import com.meritscreen.core.database.child.UsageDayEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageRecorderTest {
    private val dao = FakeUsageDao()
    private val recorder = UsageRecorder(
        dao,
        AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined, main = Dispatchers.Unconfined),
    )

    @Test
    fun `records first minute for a new app-day`() = runTest {
        recorder.record("child1", "2026-01-01", "com.a", 5)
        val day = dao.getDay("child1", "2026-01-01")
        assertEquals(5, day?.minutesUsed)
        assertEquals(mapOf("com.a" to 5), recorder.decode(day?.minutesByAppJson))
    }

    @Test
    fun `accumulates minutes across multiple calls for the same app`() = runTest {
        recorder.record("child1", "2026-01-01", "com.a", 5)
        recorder.record("child1", "2026-01-01", "com.a", 3)
        assertEquals(8, dao.getDay("child1", "2026-01-01")?.minutesUsed)
    }

    @Test
    fun `tracks separate apps independently and sums the total`() = runTest {
        recorder.record("child1", "2026-01-01", "com.a", 5)
        recorder.record("child1", "2026-01-01", "com.b", 2)
        val day = dao.getDay("child1", "2026-01-01")
        assertEquals(7, day?.minutesUsed)
        assertEquals(mapOf("com.a" to 5, "com.b" to 2), recorder.decode(day?.minutesByAppJson))
    }

    @Test
    fun `keeps different days for the same child separate`() = runTest {
        recorder.record("child1", "2026-01-01", "com.a", 5)
        recorder.record("child1", "2026-01-02", "com.a", 2)
        assertEquals(5, dao.getDay("child1", "2026-01-01")?.minutesUsed)
        assertEquals(2, dao.getDay("child1", "2026-01-02")?.minutesUsed)
    }

    @Test
    fun `ignores non-positive deltas`() = runTest {
        recorder.record("child1", "2026-01-01", "com.a", 0)
        assertNull(dao.getDay("child1", "2026-01-01"))
    }

    @Test
    fun `marks the day dirty so the sync worker will pick it up`() = runTest {
        recorder.record("child1", "2026-01-01", "com.a", 5)
        assertTrue(dao.getDay("child1", "2026-01-01")!!.dirty)
    }

    @Test
    fun `decode returns an empty map for null or blank json`() {
        assertEquals(emptyMap<String, Int>(), recorder.decode(null))
        assertEquals(emptyMap<String, Int>(), recorder.decode(""))
        assertEquals(emptyMap<String, Int>(), recorder.decode("not json"))
    }
}

internal class FakeUsageDao : UsageDao {
    private val days = mutableMapOf<String, UsageDayEntity>()

    private fun key(childId: String, day: String) = "$childId:$day"

    fun put(entity: UsageDayEntity) {
        days[key(entity.childId, entity.day)] = entity
    }

    override suspend fun getDay(childId: String, day: String): UsageDayEntity? = days[key(childId, day)]

    override suspend fun upsertDay(entity: UsageDayEntity) {
        days[key(entity.childId, entity.day)] = entity
    }

    override suspend fun listDirty(childId: String): List<UsageDayEntity> =
        days.values.filter { it.childId == childId && it.dirty }

    override suspend fun markSynced(childId: String, day: String, syncedAtEpochMs: Long) {
        days[key(childId, day)]?.let {
            days[key(childId, day)] = it.copy(dirty = false, syncedAtEpochMs = syncedAtEpochMs)
        }
    }

    override suspend fun clearForChild(childId: String) {
        days.keys.filter { it.startsWith("$childId:") }.forEach { days.remove(it) }
    }
}
