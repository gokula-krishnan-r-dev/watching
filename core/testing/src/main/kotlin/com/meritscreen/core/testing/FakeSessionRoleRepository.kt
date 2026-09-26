package com.meritscreen.core.testing

import com.meritscreen.core.common.session.DeviceRole
import com.meritscreen.core.common.session.SessionRoleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSessionRoleRepository(
    initial: DeviceRole = DeviceRole.Unassigned,
) : SessionRoleRepository {
    private val state = MutableStateFlow(initial)
    override val role: Flow<DeviceRole> = state

    override suspend fun setRole(role: DeviceRole) {
        state.value = role
    }

    override suspend fun clear() {
        state.value = DeviceRole.Unassigned
    }
}
