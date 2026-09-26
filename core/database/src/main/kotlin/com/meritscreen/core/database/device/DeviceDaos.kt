package com.meritscreen.core.database.device

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledAppDao {
    @Query("SELECT * FROM installed_app ORDER BY label ASC")
    fun observeAll(): Flow<List<InstalledAppEntity>>

    @Query("SELECT * FROM installed_app ORDER BY label ASC")
    suspend fun getAll(): List<InstalledAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<InstalledAppEntity>)

    @Query("DELETE FROM installed_app WHERE packageName NOT IN (:keepPackageNames)")
    suspend fun deleteMissing(keepPackageNames: List<String>)

    @Query("DELETE FROM installed_app")
    suspend fun clear()

    /** Replaces the whole cache in one transaction so observers never see a half-empty list. */
    @Transaction
    suspend fun replaceAll(apps: List<InstalledAppEntity>) {
        if (apps.isEmpty()) {
            clear()
            return
        }
        upsertAll(apps)
        deleteMissing(apps.map { it.packageName })
    }
}

@Dao
interface DeviceRuntimeStateDao {
    @Query("SELECT * FROM device_runtime_state WHERE id = 1 LIMIT 1")
    fun observe(): Flow<DeviceRuntimeStateEntity?>

    @Query("SELECT * FROM device_runtime_state WHERE id = 1 LIMIT 1")
    suspend fun get(): DeviceRuntimeStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeviceRuntimeStateEntity)

    @Query("DELETE FROM device_runtime_state")
    suspend fun clear()
}
