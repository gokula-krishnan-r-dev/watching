package com.meritscreen.feature.child.ui

import kotlinx.serialization.Serializable

@Serializable
data object ChildRoute

@Serializable
data object ChildHomeRoute

@Serializable
data object ChildQuizRoute

@Serializable
data object ChildStickerBookRoute

/**
 * @param endFailLockOnUnlock when true (fail-lock / resting-window entry), a successful
 * PIN clears the device-wide cooldown and returns to Home instead of the parent menu.
 */
@Serializable
data class ChildPinRoute(
    val endFailLockOnUnlock: Boolean = false,
)

@Serializable
data object ChildParentMenuRoute

@Serializable
data object ChildNotAllowedRoute

@Serializable
data object ChildDailyCeilingRoute
