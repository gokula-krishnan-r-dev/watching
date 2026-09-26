package com.meritscreen.feature.devices

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Re-arms the heartbeat schedule after a device reboot (Phase 6 §12). WorkManager already
 * persists periodic work across reboots on its own, so [DeviceHeartbeatScheduler.ensureScheduled]
 * (policy `KEEP`) is a cheap, idempotent safety net rather than the primary mechanism.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: DeviceHeartbeatScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        scheduler.ensureScheduled()
    }
}
