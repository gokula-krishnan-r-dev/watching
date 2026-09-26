package com.meritscreen.feature.child.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.meritscreen.core.common.domain.LearningResourceVideo
import com.meritscreen.core.ui.theme.MeritColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dedicated fullscreen YouTube player for educational deep dive resources.
 * Automatically locks the mobile device to landscape orientation.
 * Dismissing restores original portrait orientation.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FullscreenResourceVideoPlayer(
    video: LearningResourceVideo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var isLoading by remember(video.videoId) { mutableStateOf(true) }
    var isPlaying by remember(video.videoId) { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Intercept back button to dismiss video player
    BackHandler(onBack = onDismiss)

    // Automatically rotate screen to Landscape mode
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

    // Failsafe loading timeout: hide spinner after 4.5s
    LaunchedEffect(video.videoId) {
        delay(4500L)
        if (isLoading) {
            isLoading = false
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
                            mediaPlaybackRequiresUserGesture = false
                            domStorageEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            allowContentAccess = true
                            val currentUa = userAgentString
                            if (currentUa.contains("; wv")) {
                                userAgentString = currentUa.replace("; wv", "")
                            }
                        }
                        setBackgroundColor(android.graphics.Color.BLACK)
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                Log.d("FullscreenVideoJS", "${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()})")
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
                                if (request?.isForMainFrame == true) {
                                    Log.w("FullscreenVideo", "Main frame load failed: ${error?.description}")
                                    scope.launch { isLoading = false }
                                }
                            }
                        }

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onVideoReady() {
                                    Log.d("FullscreenVideo", "onVideoReady: ${video.videoId}")
                                    scope.launch {
                                        isLoading = false
                                        isPlaying = true
                                    }
                                }

                                @JavascriptInterface
                                fun onVideoPlaying() {
                                    Log.d("FullscreenVideo", "onVideoPlaying: ${video.videoId}")
                                    scope.launch {
                                        isLoading = false
                                        isPlaying = true
                                    }
                                }

                                @JavascriptInterface
                                fun onVideoEnded() {
                                    Log.d("FullscreenVideo", "onVideoEnded: ${video.videoId}")
                                    scope.launch { onDismiss() }
                                }

                                @JavascriptInterface
                                fun onVideoError(code: Int) {
                                    Log.w("FullscreenVideo", "YouTube Player reported error: $code")
                                    scope.launch { isLoading = false }
                                }
                            },
                            "AndroidBridge",
                        )

                        val html = buildFullscreenIframeHtml(video.videoId)
                        loadDataWithBaseURL("https://www.meritscreen.com", html, "text/html", "UTF-8", null)
                    }
                },
            )
        }

        // Tap-to-play overlay if autoplay was blocked by browser
        if (!isLoading && !isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        webViewRef?.evaluateJavascript(
                            "if (player && player.playVideo) { player.playVideo(); }",
                            null,
                        )
                        isPlaying = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.7f),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(72.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Play Video",
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }
        }

        // Top Floating Control Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.65f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MeritColors.Primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = MeritColors.OnPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${video.channelName} • ${video.durationLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Close Button
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp),
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Video",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Loading spinner while player loads
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.85f),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MeritColors.Primary,
                        strokeWidth = 3.dp,
                    )
                    Text(
                        text = "Loading Video Lesson...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private fun buildFullscreenIframeHtml(videoId: String): String = """
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
          'controls': 1,
          'rel': 0,
          'playsinline': 1,
          'modestbranding': 1,
          'fs': 0,
          'iv_load_policy': 3,
          'disablekb': 0,
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
