package com.meritscreen.feature.child.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.meritscreen.core.common.domain.SessionPhase
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
 */
@AndroidEntryPoint
class ChildTimeLimitOverlayActivity : ComponentActivity() {

    @Inject lateinit var sessionController: ChildSessionController

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

        setContent {
            MeritScreenTheme {
                val session by sessionController.snapshot.collectAsState()
                var showPin by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    if (showPin) {
                        ChildPinScreen(
                            endFailLockOnUnlock = true,
                            onUnlockedToMenu = { returnToHome() },
                            onUnlockedEndFailLock = { returnToHome() },
                            onBack = { showPin = false },
                        )
                    } else {
                        ChildQuizScreen(
                            onFinished = {
                                if (session.phase == SessionPhase.InBlock) {
                                    finish()
                                } else {
                                    returnToHome()
                                }
                            },
                            onOpenPin = { showPin = true },
                        )
                    }
                }
            }
        }
    }

    private fun returnToHome() {
        lifecycleScope.launch {
            runCatching { sessionController.returnHome() }
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(homeIntent)
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
