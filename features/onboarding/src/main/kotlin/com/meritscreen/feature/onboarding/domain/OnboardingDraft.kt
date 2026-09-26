package com.meritscreen.feature.onboarding.domain

import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import kotlinx.serialization.Serializable

/**
 * A single child profile collected during first-run, before a Firestore `childId` exists.
 * [localId] is a client-generated key used only to edit/remove the row inside the wizard.
 */
@Serializable
data class ChildDraft(
    val localId: String,
    val name: String,
    val ageBand: AgeBand,
    val avatar: AvatarPreset,
    /** BCP-47 style tag (e.g. `en`, `es`) persisted on the child profile. */
    val language: String = "en",
    /** Stable [CurriculumFocusTopic.id] values valid for [ageBand]. */
    val curriculumFocusIds: List<String> = emptyList(),
)

/**
 * The parent's in-progress first-run state (family name, child profiles, Parent PIN hash).
 *
 * The product spec explicitly allows collecting this before account creation
 * (`docs/02-onboarding-authentication.md`), so it is persisted locally via a typed DataStore
 * rather than Firestore. Once Phase 3 (authentication) provides a signed-in `uid`, this draft
 * is committed to Firestore (family + child docs) and cleared. [parentPinHash] is a PBKDF2
 * hash produced by `core:security`'s `PinHasher` — the raw PIN is never persisted or logged.
 */
@Serializable
data class OnboardingDraft(
    val consentGiven: Boolean = false,
    val familyName: String = "",
    val children: List<ChildDraft> = emptyList(),
    val parentPinHash: String? = null,
) {
    /** Ready once a child profile and Parent PIN exist (consent screen removed from first-run). */
    val isReadyToCommit: Boolean
        get() = !parentPinHash.isNullOrBlank() && children.isNotEmpty()
}
