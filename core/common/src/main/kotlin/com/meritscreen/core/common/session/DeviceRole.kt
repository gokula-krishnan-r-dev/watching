package com.meritscreen.core.common.session

sealed interface DeviceRole {
    data object Unassigned : DeviceRole
    data object Parent : DeviceRole
    data object Child : DeviceRole
}
