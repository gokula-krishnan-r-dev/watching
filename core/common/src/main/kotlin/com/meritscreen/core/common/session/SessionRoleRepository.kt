package com.meritscreen.core.common.session

import kotlinx.coroutines.flow.Flow

interface SessionRoleRepository {
    val role: Flow<DeviceRole>
    suspend fun setRole(role: DeviceRole)
    suspend fun clear()
}
