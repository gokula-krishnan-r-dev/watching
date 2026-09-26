package com.meritscreen.feature.devices

/**
 * Invoked when a heartbeat discovers this device has been revoked by a parent. Implemented
 * by `:features:child` (which owns unpair/sign-out logic) and bound via Hilt — kept as an
 * interface here so `:features:devices` never depends on the child feature module.
 */
interface DeviceRevocationHandler {
    suspend fun onDeviceRevoked()
}
