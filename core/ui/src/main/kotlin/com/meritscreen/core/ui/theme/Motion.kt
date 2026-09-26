package com.meritscreen.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

object MeritMotion {
    val emphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun <T> short() = tween<T>(durationMillis = 180, easing = emphasizedEasing)
    fun <T> medium() = tween<T>(durationMillis = 280, easing = emphasizedEasing)
}
