package com.meritscreen.feature.child.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.meritscreen.core.common.domain.NurseryVideo
import com.meritscreen.core.ui.theme.MeritColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fullscreen auto-playing YouTube player for Early Learners (Ages 3–6).
 * Automatically locks orientation to landscape and autoplays unmuted with sound.
 * When the video ends (or reaches maxSeconds), it automatically finishes and dismisses.
 * No child-facing Start or End buttons. Seamlessly falls back to local teaching cards if offline.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NurseryVideoPlayer(
    video: NurseryVideo,
    childName: String,
    onVideoFinished: () -> Unit,
    onVideoError: (Int) -> Unit,
    onParentDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var isPlaying by remember(video.videoId) { mutableStateOf(false) }
    var isLoading by remember(video.videoId) { mutableStateOf(true) }
    var secondsPlayed by remember(video.videoId) { mutableStateOf(0) }
    var showParentPrompt by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Automatically orient screen to Landscape so child sees comfortable widescreen video
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
            webViewRef?.let { wv ->
                wv.loadUrl("about:blank")
                wv.stopLoading()
                wv.destroy()
            }
        }
    }

    // Safety timeout: auto-complete when maxSeconds reached
    LaunchedEffect(video.videoId, isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (secondsPlayed < video.maxSeconds) {
            delay(1000L)
            secondsPlayed++
        }
        onVideoFinished()
    }

    // Failsafe loading timeout: dismiss spinner after 5 seconds if iframe ready callback was delayed
    LaunchedEffect(video.videoId) {
        delay(5000L)
        if (isLoading) {
            isLoading = false
            isPlaying = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        key(video.videoId) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            mediaPlaybackRequiresUserGesture = false // Key for unmuted autoplay
                            domStorageEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            allowContentAccess = true
                            // Remove '; wv' from User-Agent so YouTube treats it as standard mobile browser
                            val currentUa = userAgentString
                            if (currentUa.contains("; wv")) {
                                userAgentString = currentUa.replace("; wv", "")
                            }
                        }
                        setBackgroundColor(android.graphics.Color.BLACK)
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                Log.d("NurseryVideoJS", "${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()})")
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                super.onReceivedError(view, request, error)
                                // Only fail if the main frame failed to load; ignore minor subresource/ad/metric errors
                                if (request?.isForMainFrame == true) {
                                    Log.w("NurseryVideo", "Main frame load failed: ${error?.description}")
                                    scope.launch { onVideoError(-1) }
                                }
                            }
                        }

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onVideoReady() {
                                    Log.d("NurseryVideo", "onVideoReady: ${video.videoId}")
                                    scope.launch {
                                        isLoading = false
                                        isPlaying = true
                                    }
                                }

                                @JavascriptInterface
                                fun onVideoPlaying() {
                                    Log.d("NurseryVideo", "onVideoPlaying: ${video.videoId}")
                                    scope.launch {
                                        isLoading = false
                                        isPlaying = true
                                    }
                                }

                                @JavascriptInterface
                                fun onVideoEnded() {
                                    Log.d("NurseryVideo", "onVideoEnded: ${video.videoId}")
                                    scope.launch {
                                        onVideoFinished()
                                    }
                                }

                                @JavascriptInterface
                                fun onVideoError(code: Int) {
                                    Log.w("NurseryVideo", "YouTube Player reported error: $code")
                                    scope.launch {
                                        onVideoError(code)
                                    }
                                }
                            },
                            "AndroidBridge",
                        )

                        val html = buildYouTubeIFrameHtml(video.videoId)
                        loadDataWithBaseURL("https://www.meritscreen.com", html, "text/html", "UTF-8", null)
                    }
                },
            )
        }

        // Loading spinner while YouTube loads
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MeritColors.Primary,
                        strokeWidth = 3.dp,
                    )
                    Text(
                        text = "Loading ${video.title}...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }

        // Top Subtle Header Pill: Song Title + Autoplay Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MeritColors.Primary,
                    )
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MeritColors.Tertiary,
                    )
                }
            }

            // Discreet Parent Dismiss Affordance (Press and hold for 2s to exit)
            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                onParentDismiss()
                            },
                            onTap = {
                                showParentPrompt = true
                            },
                        )
                    },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White.copy(alpha = 0.7f),
                    )
                    Text(
                        text = if (showParentPrompt) "Hold to exit" else "Parent",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

private fun buildYouTubeIFrameHtml(videoId: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; background-color: #000000; }
  html, body { width: 100%; height: 100%; overflow: hidden; background: #000000; }
  #player { width: 100vw; height: 100vh; position: absolute; top: 0; left: 0; border: none; }
</style>
</head>
<body>
  <div id="player"></div>
  <script>
    var tag = document.createElement('script');
    tag.src = "https://www.youtube.com/iframe_api";
    var firstScriptTag = document.getElementsByTagName('script')[0];
    firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

    var player;
    function onYouTubeIframeAPIReady() {
      player = new YT.Player('player', {
        height: '100%',
        width: '100%',
        videoId: '$videoId',
        playerVars: {
          'autoplay': 1,
          'controls': 0,
          'rel': 0,
          'playsinline': 1,
          'modestbranding': 1,
          'fs': 0,
          'iv_load_policy': 3,
          'disablekb': 1,
          'enablejsapi': 1,
          'origin': 'https://www.meritscreen.com'
        },
        events: {
          'onReady': onPlayerReady,
          'onStateChange': onPlayerStateChange,
          'onError': onPlayerError
        }
      });
    }

    function onPlayerReady(event) {
      try {
        event.target.playVideo();
        event.target.unMute();
        event.target.setVolume(100);
      } catch (e) {
        console.error("playVideo error:", e);
      }
      if (window.AndroidBridge) {
        window.AndroidBridge.onVideoReady();
      }
    }

    function onPlayerStateChange(event) {
      if (event.data === 0) { // ENDED
        if (window.AndroidBridge) {
          window.AndroidBridge.onVideoEnded();
        }
      } else if (event.data === 1) { // PLAYING
        if (window.AndroidBridge) {
          window.AndroidBridge.onVideoPlaying();
        }
      }
    }

    function onPlayerError(event) {
      var code = (event && typeof event.data !== 'undefined') ? event.data : -1;
      console.error("YouTube Player Error code:", code);
      if (window.AndroidBridge) {
        window.AndroidBridge.onVideoError(code);
      }
    }
  </script>
</body>
</html>
""".trimIndent()
