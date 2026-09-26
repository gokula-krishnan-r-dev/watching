package com.meritscreen.feature.applications

import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import com.meritscreen.core.common.config.AppConfig
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compresses launcher icons into tiny WEBP thumbnails for Firestore inventory sync.
 * Returns null when compression fails or the result exceeds [AppConfig.APP_ICON_MAX_BYTES].
 */
@Singleton
class AppIconThumbnailEncoder @Inject constructor() {

    data class EncodedIcon(
        val base64: String,
        val hash: String,
        val byteSize: Int,
    )

    fun encode(bitmap: Bitmap): EncodedIcon? {
        val size = AppConfig.APP_ICON_SYNC_SIZE_PX
        val scaled = if (bitmap.width == size && bitmap.height == size) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        return try {
            val stream = ByteArrayOutputStream(AppConfig.APP_ICON_MAX_BYTES)
            val compressed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                scaled.compress(
                    Bitmap.CompressFormat.WEBP_LOSSY,
                    AppConfig.APP_ICON_WEBP_QUALITY,
                    stream,
                )
            } else {
                @Suppress("DEPRECATION")
                scaled.compress(
                    Bitmap.CompressFormat.WEBP,
                    AppConfig.APP_ICON_WEBP_QUALITY,
                    stream,
                )
            }
            if (!compressed) return null
            val bytes = stream.toByteArray()
            if (bytes.isEmpty() || bytes.size > AppConfig.APP_ICON_MAX_BYTES) return null
            EncodedIcon(
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                hash = shortHash(bytes),
                byteSize = bytes.size,
            )
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun shortHash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
