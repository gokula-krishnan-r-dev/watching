package com.meritscreen.core.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Lightweight on-device QR renderer (ZXing encode only — no network).
 * Used for parent↔child pairing payloads such as `meritscreen://pair?c=…&s=…`.
 */
@Composable
fun MeritQrCodeImage(
    payload: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    contentPadding: Dp = 12.dp,
    darkColor: Int = 0xFF1C1C19.toInt(),
    lightColor: Int = 0xFFFFFFFF.toInt(),
) {
    val bitmap = remember(payload, size, darkColor, lightColor) {
        if (payload.isBlank()) null
        else encodeQrBitmap(
            payload = payload,
            pixelSize = (size.value * 3).toInt().coerceIn(256, 1024),
            darkColor = darkColor,
            lightColor = lightColor,
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(lightColor)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Pairing QR code",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        }
    }
}

fun encodeQrBitmap(
    payload: String,
    pixelSize: Int = 512,
    darkColor: Int = 0xFF1C1C19.toInt(),
    lightColor: Int = 0xFFFFFFFF.toInt(),
): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, pixelSize, pixelSize, hints)
    val bitmap = Bitmap.createBitmap(pixelSize, pixelSize, Bitmap.Config.ARGB_8888)
    for (x in 0 until pixelSize) {
        for (y in 0 until pixelSize) {
            bitmap.setPixel(x, y, if (matrix[x, y]) darkColor else lightColor)
        }
    }
    return bitmap
}

/**
 * Parses `meritscreen://pair?c=CODE&s=SECRET` (and tolerant variants) from a scanned QR.
 */
object PairingQrPayload {
    private val deepLink = Regex(
        pattern = """(?i)meritscreen://pair\?(?:.*(?:&|\?))?c=(\d{6})(?:.*(?:&|\?)s=([A-Za-z0-9]+))?""",
    )
    private val queryCode = Regex("""(?i)[?&]c=(\d{6})""")
    private val querySecret = Regex("""(?i)[?&]s=([A-Za-z0-9]+)""")
    private val looseCode = Regex("""(?<!\d)(\d{6})(?!\d)""")

    data class Parsed(val code: String, val secret: String?)

    fun parse(raw: String?): Parsed? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        if (trimmed.contains("meritscreen://pair", ignoreCase = true) ||
            trimmed.contains("c=", ignoreCase = true)
        ) {
            val code = queryCode.find(trimmed)?.groupValues?.getOrNull(1)
                ?: deepLink.find(trimmed)?.groupValues?.getOrNull(1)
            if (code != null) {
                val secret = querySecret.find(trimmed)?.groupValues?.getOrNull(1)
                    ?: deepLink.find(trimmed)?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
                return Parsed(code, secret)
            }
        }

        val codeOnly = looseCode.find(trimmed)?.groupValues?.getOrNull(1) ?: return null
        return Parsed(codeOnly, secret = null)
    }
}
