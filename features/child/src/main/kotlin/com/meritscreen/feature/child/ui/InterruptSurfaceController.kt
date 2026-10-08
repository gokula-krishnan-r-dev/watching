package com.meritscreen.feature.child.ui

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.meritscreen.core.common.logging.AppLogger

/**
 * Hosts the quiz interrupt UI in a [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
 * surface so it paints **above** other apps' Picture-in-Picture windows.
 *
 * Regular Activities (including [ChildTimeLimitOverlayActivity]) sit in the app-task layer.
 * Android z-orders pinned PiP tasks above fullscreen tasks, which is why YouTube's floating
 * panel can cover the quiz when we only use an Activity. Overlay windows are a separate
 * layer above activity windows (public `SYSTEM_ALERT_WINDOW` API) — that is the supported
 * consumer-device way to keep the quiz readable without Accessibility or Device Owner.
 *
 * **Honest limit:** a third-party app cannot call SystemUI to *dismiss* another package's
 * PiP. We cover it and pause its audio. If overlay permission is missing, callers must use
 * an Activity fallback (PiP may remain visible on top).
 */
class InterruptSurfaceController(
    private val logger: AppLogger,
) {
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null

    val isAttached: Boolean get() = overlayView != null

    fun canUseOverlaySurface(context: Context): Boolean =
        InterruptSurfacePolicy.shouldHostQuizInOverlay(Settings.canDrawOverlays(context))

    /**
     * Attaches a fullscreen overlay hosting [content]. No-op if already attached.
     * Returns false when overlay permission is missing or [WindowManager.addView] fails.
     */
    fun attach(activity: ComponentActivity, content: @Composable () -> Unit): Boolean {
        if (overlayView != null) return true
        if (!canUseOverlaySurface(activity)) {
            logger.w("Interrupt overlay skipped: Display over other apps not granted")
            return false
        }

        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent(content)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Keep the quiz above cutouts; PiP often sits in a corner that would otherwise
            // peek past a non-cutout-aware surface.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
            title = "MeritScreenQuizInterrupt"
        }

        return try {
            val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(composeView, params)
            windowManager = wm
            overlayView = composeView
            requestAudioFocus(activity)
            logger.i("Quiz interrupt overlay attached above activity/PiP layer")
            true
        } catch (e: Exception) {
            logger.w("Could not attach quiz interrupt overlay; falling back to activity UI", e)
            overlayView = null
            windowManager = null
            false
        }
    }

    fun detach() {
        val view = overlayView
        if (view != null) {
            try {
                windowManager?.removeViewImmediate(view)
            } catch (e: Exception) {
                logger.w("Could not remove quiz interrupt overlay", e)
                runCatching { windowManager?.removeView(view) }
            }
        }
        overlayView = null
        windowManager = null
        abandonAudioFocus()
    }

    /** Pauses other apps' media (YouTube PiP audio) for the duration of the quiz. */
    fun requestAudioFocus(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = am
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) return
            val listener = AudioManager.OnAudioFocusChangeListener { /* hold until abandon */ }
            audioFocusListener = listener
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(listener)
                .build()
            audioFocusRequest = request
            val result = am.requestAudioFocus(request)
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED &&
                result != AudioManager.AUDIOFOCUS_REQUEST_DELAYED
            ) {
                logger.w("Audio focus not granted for quiz interrupt (result=$result)")
            }
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                { /* hold until abandon */ },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }
        } else {
            @Suppress("DEPRECATION")
            audioFocusListener?.let { runCatching { am.abandonAudioFocus(it) } }
        }
        audioFocusRequest = null
        audioFocusListener = null
        audioManager = null
    }
}
