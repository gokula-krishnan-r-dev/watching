package com.meritscreen.feature.devices

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meritscreen.core.common.device.DefaultHomeChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Bounded, WorkManager-scheduled liveness + revocation check (Phase 6 §3/§4). Requires
 * network (see [DeviceHeartbeatScheduler]) and always succeeds-with-retry rather than
 * crash-looping so a temporary outage never wedges the device.
 */
@HiltWorker
class DeviceHeartbeatWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val heartbeatUseCase: DeviceHeartbeatUseCase,
    private val revocationHandler: DeviceRevocationHandler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isDefaultHome = DefaultHomeChecker.isDefaultHome(applicationContext)
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val battery = readBatteryPercent(applicationContext)
        val osVersion = "Android ${Build.VERSION.RELEASE}"
        val appVersion = runCatching {
            applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0)
                .versionName
        }.getOrNull()
        return when (
            val outcome = heartbeatUseCase(isDefaultHome, model, battery, osVersion, appVersion)
        ) {
            HeartbeatOutcome.Skipped -> Result.success()
            HeartbeatOutcome.Ok -> Result.success()
            HeartbeatOutcome.Revoked -> {
                revocationHandler.onDeviceRevoked()
                Result.success()
            }
            is HeartbeatOutcome.Failed -> {
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5

        fun readBatteryPercent(context: Context): Int? {
            val manager = context.getSystemService(BatteryManager::class.java) ?: return null
            return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .takeIf { it in 0..100 }
        }
    }
}
