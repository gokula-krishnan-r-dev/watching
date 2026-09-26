package com.meritscreen.feature.authentication.domain

data class ParentAuthResult(
    val familyId: String?,
    val childIds: List<String>,
    val isNewFamily: Boolean,
    val needsOnboarding: Boolean,
) {
    val pairingChildId: String?
        get() = childIds.firstOrNull()
}
