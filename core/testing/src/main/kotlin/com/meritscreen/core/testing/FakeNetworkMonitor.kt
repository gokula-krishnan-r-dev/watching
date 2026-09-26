package com.meritscreen.core.testing

import com.meritscreen.core.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkMonitor(online: Boolean = true) : NetworkMonitor {
    private val state = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = state
    override fun isCurrentlyOnline(): Boolean = state.value

    fun setOnline(online: Boolean) {
        state.value = online
    }
}
