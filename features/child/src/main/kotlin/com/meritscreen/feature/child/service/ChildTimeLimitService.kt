package com.meritscreen.feature.child.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.domain.SessionPhase
import com.meritscreen.core.common.logging.AppLogger
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.feature.child.data.ChildPolicyRepository
import com.meritscreen.feature.child.domain.ChildSessionController
import com.meritscreen.feature.child.domain.SessionEngine
import com.meritscreen.feature.child.ui.ChildTimeLimitOverlayActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 24/7 Foreground Service and Background Monitor that continuously tracks active app usage,
 * ticks screen time quotas, and immediately triggers the system-overlay challenge pop-up
 * the moment an app limit or daily quota expires, regardless of which third-party app is in focus.
 */
@AndroidEntryPoint
class ChildTimeLimitService : Service() {

    @Inject lateinit var sessionController: ChildSessionController
    @Inject lateinit var pairingStore: ChildPairingStore
    @Inject lateinit var policyRepository: ChildPolicyRepository
    @Inject lateinit var dispatchers: AppDispatchers
    @Inject lateinit var logger: AppLogger

    private val serviceScope by lazy { CoroutineScope(SupervisorJob() + dispatchers.default) }
    private var monitorJob: Job? = null
    private var lastOverlayTriggerElapsedMs = 0L
    private val foregroundTracker = ForegroundAppDetector.Tracker()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startInForeground("Watching Protection Active")
        startMonitoring()
        logger.i("ChildTimeLimitService created and started in foreground")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        logger.i("ChildTimeLimitService destroyed")
        super.onDestroy()
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    val credential = pairingStore.get()
                    if (credential == null) {
                        // Device not paired as child yet, idle and retry
                        delay(5_000L)
                        continue
                    }

                    // Check if screen is interactive (screen is turned on)
                    val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val isScreenInteractive = powerManager?.isInteractive ?: true
                    if (!isScreenInteractive) {
                        delay(2_000L)
                        continue
                    }

                    // Foreground app detection
                    val hasUsagePermission = ForegroundAppDetector.hasUsageStatsPermission(this@ChildTimeLimitService)
                    val currentForeground = if (hasUsagePermission) {
                        foregroundTracker.currentPackage(this@ChildTimeLimitService)
                    } else null

                    val policy = sessionController.currentPolicy()
                    val snapshot = sessionController.snapshot.value
                    val now = SystemClock.elapsedRealtime()

                    // Handle app transitions
                    if (currentForeground != null) {
                        if (currentForeground == packageName) {
                            // User is inside Watching / MeritScreen launcher or UI
                            // The app block remains paused while MeritScreen is visible.
                            // Returning Home must not erase the block or its accrued time.
                        } else {
                            // User is in a third-party app
                            val isEmergency = SessionEngine.isEmergencyPackage(currentForeground, policy)
                            val rules = policyRepository.listAppRules(credential.childId)
                            val rule = rules.firstOrNull { it.packageOrBundleId == currentForeground }
                            val isAllowed = rule?.allowed ?: true

                            if (snapshot.phase == SessionPhase.Shielded && !isEmergency) {
                                enforceReturnHome()
                            } else if (policy.paused && !isEmergency) {
                                enforceReturnHome()
                            } else if (!isAllowed && !isEmergency) {
                                enforceReturnHome()
                            } else if (isAllowed && snapshot.phase != SessionPhase.QuizDue &&
                                (snapshot.phase != SessionPhase.InBlock || snapshot.activePackage != currentForeground)
                            ) {
                                val appRule = rule ?: com.meritscreen.core.common.domain.AppRule(
                                    appId = currentForeground.replace('.', '_'),
                                    packageOrBundleId = currentForeground,
                                    allowed = true,
                                    blockMinutes = policy.defaultBlockMinutes,
                                )
                                sessionController.openApp(appRule)
                            }
                        }
                    }

                    // Continuous local engine tick
                    val trackedAppIsForeground = snapshot.phase == SessionPhase.InBlock &&
                        currentForeground.equals(snapshot.activePackage, ignoreCase = true)
                    sessionController.tick(isAppActive = trackedAppIsForeground)
                    val updatedSnapshot = sessionController.snapshot.value

                    when (updatedSnapshot.phase) {
                        SessionPhase.InBlock -> {
                            val remainingMinutes = updatedSnapshot.remainingBlockMinutes(now)
                            val appName = updatedSnapshot.activePackage?.substringAfterLast('.') ?: "App"
                            updateNotification("Active: $appName • ${remainingMinutes}m left")
                            delay(1_000L)
                        }

                        SessionPhase.QuizDue -> {
                            updateNotification("Time’s up! Complete quiz challenge to continue")
                            // Only trigger overlay pop-up if not already in Watching app
                            if (currentForeground != packageName) {
                                triggerOverlayPopUp(updatedSnapshot.activePackage)
                            }
                            delay(2_000L)
                        }

                        SessionPhase.Shielded -> {
                            val remainingSeconds = updatedSnapshot.remainingCooldownSeconds(now)
                            updateNotification("Cooldown active • ${remainingSeconds}s remaining")
                            delay(1_500L)
                        }

                        SessionPhase.Idle -> {
                            val dailyRemaining = updatedSnapshot.dailyRemainingMinutes(policy)
                            updateNotification("Watching Active • ${dailyRemaining}m left today")
                            delay(1_000L)
                        }
                    }
                } catch (e: Exception) {
                    logger.w("Error in ChildTimeLimitService monitor loop", e)
                    delay(3_000L)
                }
            }
        }
    }

    private fun triggerOverlayPopUp(activePackage: String?) {
        val now = SystemClock.elapsedRealtime()
        // Debounce overlay triggers (minimum 3 seconds between launches)
        if (now - lastOverlayTriggerElapsedMs < 3_000L) return
        lastOverlayTriggerElapsedMs = now

        val overlayIntent = ChildTimeLimitOverlayActivity.createIntent(this)
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OVERLAY,
            overlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Show high-priority notification with full-screen intent so Android displays it immediately
        val appName = activePackage?.substringAfterLast('.') ?: "App"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time’s up for $appName! ⏳")
            .setContentText("Answer 3 quick fun questions to unlock more minutes.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)

        // Directly launch the overlay activity over the running third-party app
        try {
            startActivity(overlayIntent)
        } catch (e: Exception) {
            logger.e("Failed to launch overlay activity directly", e)
        }
    }

    private fun enforceReturnHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Time & Safety Monitor",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows live status of active app screen time and safety boundaries"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun startInForeground(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(statusText: String) {
        val notification = buildNotification(statusText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else null

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Watching Protection")
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "meritscreen_time_limit_channel"
        const val NOTIFICATION_ID = 2001
        private const val REQUEST_CODE_OVERLAY = 2002

        fun start(context: Context) {
            val intent = Intent(context, ChildTimeLimitService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Background start restriction fallback: start service normally or wait for next resume
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ChildTimeLimitService::class.java)
            context.stopService(intent)
        }
    }
}
