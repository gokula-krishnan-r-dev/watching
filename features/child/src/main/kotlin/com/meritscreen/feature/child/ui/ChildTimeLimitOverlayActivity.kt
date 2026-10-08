package com.meritscreen.feature.child.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.lifecycle.lifecycleScope
import com.meritscreen.core.common.domain.SessionPhase
import com.meritscreen.core.common.logging.AppLogger
import com.meritscreen.core.ui.theme.MeritScreenTheme
import com.meritscreen.feature.child.domain.ChildSessionController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * System-overlay activity that appears immediately over any active third-party application
 * (such as Calendar, YouTube, or Games) when an app time limit or daily quota is reached.
 *
 * Provides a modal challenge screen (Quiz Interrupt) preventing further access to the underlying
 * application until 3 quick questions are answered and passed, or the child chooses to take a break.
 *
 * When "Display over other apps" is granted, the quiz UI is hosted in a
 * [InterruptSurfaceController] TYPE_APPLICATION_OVERLAY surface so YouTube (and other)
 * Picture-in-Picture windows cannot float on top of the questions.
 */
@AndroidEntryPoint
class ChildTimeLimitOverlayActivity : ComponentActivity() {

    @Inject lateinit var sessionController: ChildSessionController
    @Inject lateinit var logger: AppLogger

    private val interruptSurface by lazy { InterruptSurfaceController(logger) }
    private var isReturningHome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure window to immediately display over active applications or keyguard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        // Intercept back button: the child cannot bypass the time limit popup by pressing Back
        onBackPressedDispatcher.addCallback(this) {
            returnToHome()
        }

        val attachedOverlay = interruptSurface.attach(this) {
            MeritScreenTheme {
                QuizInterruptContent(
                    sessionController = sessionController,
                    onFinishedPass = ::finishOverlay,
                    onFinishedHome = ::returnToHome,
                    onContinueToApp = ::continueToMonitoredApp,
                )
            }
        }

        if (attachedOverlay) {
            // Activity is only a lifecycle/ViewModel host. Keep it transparent and
            // non-interactive so the overlay surface (above PiP) owns the UI.
            makeActivityWindowPassive()
            setContent {
                // Empty host — real UI is in the overlay surface.
                Box(modifier = Modifier.fillMaxSize())
            }
        } else {
            // Overlay unavailable (permission or attach failure): Activity UI fallback.
            // Honest limit — another app's PiP may still float above on some OEMs.
            interruptSurface.requestAudioFocus(this)
            setContent {
                MeritScreenTheme {
                    QuizInterruptContent(
                        sessionController = sessionController,
                        onFinishedPass = ::finishOverlay,
                        onFinishedHome = ::returnToHome,
                        onContinueToApp = ::continueToMonitoredApp,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        interruptSurface.detach()
        super.onDestroy()
    }

    private fun makeActivityWindowPassive() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setBackgroundDrawableResource(android.R.color.transparent)
        // Touches must not hit this under-PiP activity; the overlay surface receives them.
        window.decorView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun finishOverlay() {
        if (isReturningHome || isFinishing) return
        isReturningHome = true
        interruptSurface.detach()
        finish()
    }

    private fun continueToMonitoredApp() {
        if (isReturningHome || isFinishing) return
        isReturningHome = true
        interruptSurface.detach()
        val targetPackage = sessionController.snapshot.value.activePackage
        logger.i("Quiz pass continue requested; phase=${sessionController.phase()}, target=${targetPackage ?: "none"}")
        val launchIntent = targetPackage
            ?.takeIf { it != packageName }
            ?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            runCatching { startActivity(launchIntent) }
                .onFailure {
                    logger.w("Could not launch monitored package $targetPackage after quiz pass", it)
                }
        } else {
            logger.w("No launch intent available for monitored package ${targetPackage ?: "none"}")
        }
        // If Android no longer has a launch intent for the target, dismiss safely. The
        // session stays InBlock and the foreground monitor will resume it on next launch.
        finish()
    }

    private fun returnToHome() {
        if (isReturningHome || isFinishing) return
        isReturningHome = true
        interruptSurface.detach()
        lifecycleScope.launch {
            runCatching { sessionController.returnHome() }
            val launcherIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launcherIntent != null) {
                launcherIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(launcherIntent)
            } else {
                startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            finish()
        }
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, ChildTimeLimitOverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
    }
}

@Composable
private fun QuizInterruptContent(
    sessionController: ChildSessionController,
    onFinishedPass: () -> Unit,
    onFinishedHome: () -> Unit,
    onContinueToApp: () -> Unit,
) {
    var showPin by remember { mutableStateOf(false) }
    // Overlay surface owns focus when covering PiP; keep Back consistent with the Activity.
    BackHandler {
        if (showPin) showPin = false else onFinishedHome()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.55f)),
    ) {
        if (showPin) {
            ChildPinScreen(
                endFailLockOnUnlock = true,
                onUnlockedToMenu = onFinishedHome,
                onUnlockedEndFailLock = onFinishedHome,
                onBack = { showPin = false },
            )
        } else {
            ChildQuizScreen(
                onFinished = {
                    // Read the authoritative current state at click time; the
                    // Compose-collected snapshot can lag behind the pass write.
                    if (sessionController.phase() == SessionPhase.InBlock) {
                        onFinishedPass()
                    } else {
                        onFinishedHome()
                    }
                },
                onOpenPin = { showPin = true },
                onContinueToApp = onContinueToApp,
                onGoHome = onFinishedHome,
            )
        }
    }
}
