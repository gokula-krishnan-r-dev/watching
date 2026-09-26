package com.meritscreen.core.ui.components

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.meritscreen.core.ui.theme.MeritColors

/**
 * Renders a synced child-device launcher icon (WEBP Base64) via Coil with memory + disk cache.
 * Falls back to a Material glyph when bytes are missing or decode fails.
 */
@Composable
fun SyncedAppIcon(
    packageName: String,
    iconBase64: String?,
    iconHash: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 12.dp,
    fallbackIcon: ImageVector = Icons.Default.Apps,
) {
    val context = LocalContext.current
    val bytes = remember(packageName, iconHash, iconBase64) {
        decodeIconBytes(iconBase64)
    }
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MeritColors.SurfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bytes != null) {
            val cacheKey = remember(packageName, iconHash) {
                "app-icon:${packageName}:${iconHash.orEmpty()}"
            }
            val request = remember(cacheKey, bytes) {
                ImageRequest.Builder(context)
                    .data(bytes)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)
                    .build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                loading = { IconFallback(fallbackIcon, size) },
                error = { IconFallback(fallbackIcon, size) },
            )
        } else {
            IconFallback(fallbackIcon, size * 0.55f)
        }
    }
}

@Composable
private fun IconFallback(icon: ImageVector, size: Dp) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MeritColors.Primary,
        modifier = Modifier.size(size),
    )
}

private fun decodeIconBytes(iconBase64: String?): ByteArray? {
    if (iconBase64.isNullOrBlank()) return null
    return runCatching {
        Base64.decode(iconBase64, Base64.DEFAULT)
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}
