package com.meritscreen.feature.applications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.collection.LruCache
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.dispatchers.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes and caches app icons locally (PackageManager) for the child launcher grid.
 *
 * Full-res icons stay on-device only. Parent phones receive tiny WEBP thumbnails via
 * [AppIconThumbnailEncoder] on inventory sync — never this LRU.
 */
@Singleton
class AppIconLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val cache = object : LruCache<String, Bitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(packageName: String): Bitmap? {
        cache.get(packageName)?.let { return it }
        return withContext(dispatchers.default) {
            val bitmap = runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                drawableToBitmap(drawable)
            }.getOrNull()
            if (bitmap != null) cache.put(packageName, bitmap)
            bitmap
        }
    }

    /** Sync-path decode at thumbnail size (avoids retaining 128px bitmaps only for upload). */
    suspend fun loadForSync(packageName: String): Bitmap? = withContext(dispatchers.default) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable, AppConfig.APP_ICON_SYNC_SIZE_PX)
        }.getOrNull()
    }

    fun invalidate(packageName: String) {
        cache.remove(packageName)
    }

    fun clear() {
        cache.evictAll()
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int = ICON_SIZE_PX): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun cacheSizeBytes(): Int {
        return (Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtLeast(1)
    }

    private companion object {
        const val ICON_SIZE_PX = 128
    }
}
