package com.meritscreen.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val updatedAtEpochMs: Long,
    val payloadHash: String? = null,
)
