package com.meritscreen.feature.child.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
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
    private var lastUsageAccessState: Boolean? = null
    private var lastOverlayPermissionState: Boolean? = null
    private var lastLoggedSessionState: String? = null
    private val foregroundTracker = ForegroundAppDetector.Tracker()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var countdownView: TextView? = null
    private var countdownWindowManager: WindowManager? = null
    private var countdownAttached = false

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
        mainHandler.post { removeDebugCountdown() }
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
                    if (lastUsageAccessState != hasUsagePermission) {
                        lastUsageAccessState = hasUsagePermission
                        if (hasUsagePermission) {
                            logger.i("Usage Access granted; foreground app monitoring is active")
                        } else {
                            logger.w("Usage Access is not granted; app usage and quiz triggers are paused")
                        }
                    }
                    val currentForeground = if (hasUsagePermission) {
                        foregroundTracker.currentPackage(this@ChildTimeLimitService)
                    } else null

                    val policy = sessionController.currentPolicy()
                    var snapshot = sessionController.snapshot.value
                    val now = SystemClock.elapsedRealtime()

                    val previouslyTrackedPackage = snapshot.activePackage
                    if (previouslyTrackedPackage != null && snapshot.phase != SessionPhase.Idle &&
                        ForegroundAppDetector.isHomeOrSystemUi(this@ChildTimeLimitService, previouslyTrackedPackage)
                    ) {
                        val stalePhase = snapshot.phase
                        snapshot = sessionController.clearUntrackedSurfaceSession()
                        logger.w(
                            "Cleared stale $stalePhase session for Home/system UI " +
                                "($previouslyTrackedPackage) left by an earlier build",
                        )
                    }

                    // Handle app transitions
                    if (currentForeground != null) {
                        if (currentForeground == packageName ||
                            ForegroundAppDetector.isHomeOrSystemUi(this@ChildTimeLimitService, currentForeground)
                        ) {
                            // Our own UI, the device launcher, and Android control surfaces
                            // pause the monitored app. They must never start a fresh app block.
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
                    syncDebugCountdown(updatedSnapshot, currentForeground)
                    val sessionStateKey = "${updatedSnapshot.phase}:${updatedSnapshot.activePackage}"
                    if (sessionStateKey != lastLoggedSessionState) {
                        lastLoggedSessionState = sessionStateKey
                        logger.i(
                            "Child session changed to ${updatedSnapshot.phase}; " +
                                "tracked=${updatedSnapshot.activePackage ?: "none"}, " +
                                "foreground=${currentForeground ?: "unknown"}",
                        )
                    }

                    when (updatedSnapshot.phase) {
                        SessionPhase.InBlock -> {
                            val remainingMinutes = updatedSnapshot.remainingBlockMinutes(now)
                            val appName = updatedSnapshot.activePackage?.substringAfterLast('.') ?: "App"
                            updateNotification(
                                if (hasUsagePermission) "Active: $appName • ${remainingMinutes}m left"
                                else "Setup required • allow Usage Access",
                            )
                            delay(1_000L)
                        }

                        SessionPhase.QuizDue -> {
                            updateNotification(
                                if (Settings.canDrawOverlays(this@ChildTimeLimitService)) {
                                    "Time’s up! Complete quiz challenge to continue"
                                } else {
                                    "Quiz due • open MeritScreen to continue"
                                },
                            )
                            // The service owns lock quizzes whenever overlay access is available,
                            // including while the child launcher is foregrounded. Letting Home
                            // also navigate to ChildQuizRoute races this activity and leaves a
                            // second, stale quiz underneath the overlay after a successful pass.
                            val foregroundCanBeInterrupted = currentForeground == packageName ||
                                (currentForeground != null &&
                                    !ForegroundAppDetector.isHomeOrSystemUi(
                                        this@ChildTimeLimitService,
                                        currentForeground,
                                    ))
                            if (foregroundCanBeInterrupted) {
                                val canOverlay = Settings.canDrawOverlays(this@ChildTimeLimitService)
                                if (lastOverlayPermissionState != canOverlay) {
                                    lastOverlayPermissionState = canOverlay
                                    if (canOverlay) {
                                        logger.i("Display over other apps permission granted; quiz overlay can launch")
                                    } else {
                                        logger.w("Quiz is due, but Display over other apps permission is not granted")
                                    }
                                }
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
                            updateNotification(
                                if (hasUsagePermission) "Watching Active • ${dailyRemaining}m left today"
                                else "Setup required • allow Usage Access",
                            )
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

    /**
     * Debug aid for on-device validation: this is a projection of the authoritative session
     * snapshot, not another timer. It is only visible over the app whose block is being tracked.
     */
    private fun syncDebugCountdown(snapshot: com.meritscreen.feature.child.domain.SessionSnapshot, foreground: String?) {
        val shouldShow = Settings.canDrawOverlays(this) &&
            snapshot.phase == SessionPhase.InBlock &&
            !foreground.isNullOrBlank() &&
            foreground.equals(snapshot.activePackage, ignoreCase = true) &&
            foreground != packageName

        mainHandler.post {
            if (!shouldShow) {
                removeDebugCountdown()
                return@post
            }

            val view = countdownView ?: createDebugCountdown().also { countdownView = it }
            val seconds = snapshot.remainingBlockSeconds()
            val minutesPart = seconds / 60
            val secondsPart = seconds % 60
            view.text = "DEBUG  •  Quiz in %d:%02d".format(minutesPart, secondsPart)

            if (!countdownAttached) {
                val manager = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return@post
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = dp(10)
                    y = dp(56)
                }
                try {
                    manager.addView(view, params)
                    countdownWindowManager = manager
                    countdownAttached = true
                } catch (e: Exception) {
                    logger.w("Could not attach debug countdown overlay", e)
                    countdownView = null
                }
            } else {
                view.invalidate()
            }
        }
    }

    private fun createDebugCountdown(): TextView = TextView(this).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply {
            setColor(Color.rgb(0, 91, 84))
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Color.rgb(127, 224, 197))
        }
        elevation = dp(6).toFloat()
        contentDescription = "Debug countdown until the next quiz"
    }

    private fun removeDebugCountdown() {
        val view = countdownView ?: return
        if (countdownAttached) {
            try {
                countdownWindowManager?.removeView(view)
            } catch (e: Exception) {
                logger.w("Could not remove debug countdown overlay", e)
            }
        }
        countdownAttached = false
        countdownWindowManager = null
        countdownView = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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

        // Android blocks background activity launches unless this special access is granted.
        // Keep the notification available so the child can still enter the quiz manually.
        if (!Settings.canDrawOverlays(this)) return

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
                // Keep the failure visible in local logs; silently swallowing it made a dead
                // monitor indistinguishable from a healthy child device.
                android.util.Log.e("ChildTimeLimitService", "Unable to start monitoring service", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ChildTimeLimitService::class.java)
            context.stopService(intent)
        }
    }
}
