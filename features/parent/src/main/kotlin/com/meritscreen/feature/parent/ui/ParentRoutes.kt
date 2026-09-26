package com.meritscreen.feature.parent.ui

import kotlinx.serialization.Serializable

@Serializable
data object ParentRoute

@Serializable
data object ParentDashboardRoute

@Serializable
data object ChildrenRoute

@Serializable
data class ChildDetailRoute(val childId: String)

@Serializable
data class AllowlistRoute(val childId: String)

@Serializable
data class TimeLimitsRoute(val childId: String)

@Serializable
data class QuizSettingsRoute(val childId: String)

@Serializable
data class RewardsRoute(val childId: String)

@Serializable
data class ReportsRoute(val childId: String = "")

@Serializable
data class DevicesRoute(val childId: String)

@Serializable
data object NotificationsRoute

@Serializable
data object AccountRoute

@Serializable
data object AddChildRoute

/** Post-signup add-child step 2 — same Timeline UI as onboarding. */
@Serializable
data class AddChildTimelineRoute(
    val childId: String,
    val childName: String,
    val ageBand: String,
    val avatar: String = "RABBIT",
)

/** Post-signup add-child step 3 — QR / code device handshake (same UI as onboarding). */
@Serializable
data class AddChildPairingRoute(
    val childId: String,
    val childName: String,
    val ageBand: String,
    val avatar: String = "RABBIT",
)

/** Post-signup add-child step 4 — app allowlist (same UI as onboarding). */
@Serializable
data class AddChildAllowlistRoute(
    val childId: String,
    val childName: String,
    val ageBand: String,
    val avatar: String = "RABBIT",
)

/** Post-signup add-child step 5 — AI Learning Context. */
@Serializable
data class AddChildAiRoute(
    val childId: String,
    val childName: String,
    val ageBand: String,
    val avatar: String = "RABBIT",
)

@Serializable
data object ResetPinRoute

@Serializable
data object DeleteFamilyRoute
