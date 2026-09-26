package com.meritscreen.feature.onboarding.domain

import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.common.session.ParentSession
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.firebase.auth.AuthClient
import com.meritscreen.core.firebase.family.FamilyDraft
import com.meritscreen.core.firebase.family.FamilyDraftChild
import com.meritscreen.core.firebase.family.FamilyStore
import com.meritscreen.core.network.NetworkMonitor
import com.meritscreen.core.security.pin.PinHasher
import com.meritscreen.feature.onboarding.data.OnboardingDraftRepository
import java.security.SecureRandom
import java.util.Locale
import javax.inject.Inject

data class PairingChildContext(
    val familyId: String,
    val childId: String,
    val childName: String,
)

/**
 * Ensures a Firestore family + child exist before [createPairingToken] can succeed.
 * Onboarding often reaches Device Handshake before Parent PIN / full draft commit, so this
 * bootstraps the family from the local draft when needed (PIN can be updated later).
 *
 * @param preferredChildId when set (post-signup Add Child), pair that child instead of
 * the session's first child id.
 */
class EnsureFamilyReadyForPairingUseCase @Inject constructor(
    private val authClient: AuthClient,
    private val familyStore: FamilyStore,
    private val parentSessionRepository: ParentSessionRepository,
    private val draftRepository: OnboardingDraftRepository,
    private val pinHasher: PinHasher,
    private val networkMonitor: NetworkMonitor,
) {
    suspend operator fun invoke(preferredChildId: String? = null): PairingChildContext {
        if (!networkMonitor.isCurrentlyOnline()) {
            throw AppErrorException(AppError.Network())
        }
        val user = authClient.currentUser
            ?: throw AppErrorException(AppError.Auth("Sign in as a parent before pairing a device."))
        if (user.isChildDevice) {
            throw AppErrorException(AppError.Auth("This device is paired as a child device."))
        }

        val preferred = preferredChildId?.takeIf { it.isNotBlank() }

        parentSessionRepository.current()?.let { session ->
            resolveFromFamily(
                familyId = session.familyId,
                preferredChildId = preferred ?: session.childIds.firstOrNull(),
            )?.let { return it }
        }

        familyStore.getUserFamilyId(user.uid)?.let { familyId ->
            val children = familyStore.listChildren(familyId)
            if (children.isEmpty()) {
                throw AppErrorException(
                    AppError.Validation("Add a child profile before pairing a device."),
                )
            }
            val child = preferred?.let { id -> children.firstOrNull { it.childId == id } }
                ?: children.first()
            if (preferred != null && child.childId != preferred) {
                throw AppErrorException(
                    AppError.Validation("That child profile was not found. Go back and try again."),
                )
            }
            parentSessionRepository.set(
                ParentSession(
                    uid = user.uid,
                    familyId = familyId,
                    childIds = children.map { it.childId },
                ),
            )
            return PairingChildContext(familyId, child.childId, child.displayName)
        }

        val draft = draftRepository.current()
        if (draft.children.isEmpty()) {
            throw AppErrorException(
                AppError.Validation("Add a child profile before pairing a device."),
            )
        }

        val pinHash = draft.parentPinHash ?: pinHasher.hash(generateBootstrapPin())
        if (draft.parentPinHash == null) {
            draftRepository.setParentPinHash(pinHash)
        }

        val created = familyStore.createFamilyFromDraft(
            uid = user.uid,
            email = user.email,
            draft = FamilyDraft(
                familyName = draft.familyName.ifBlank { "My Family" },
                parentPinHash = pinHash,
                children = draft.children.map { child ->
                    FamilyDraftChild(
                        localId = child.localId,
                        name = child.name,
                        ageBand = child.ageBand,
                        avatar = child.avatar,
                        language = child.language.ifBlank {
                            Locale.getDefault().language.ifBlank { "en" }
                        },
                        curriculumFocusIds = child.curriculumFocusIds,
                    )
                },
            ),
        )
        parentSessionRepository.set(
            ParentSession(
                uid = user.uid,
                familyId = created.familyId,
                childIds = created.children.map { it.childId },
            ),
        )
        val primary = preferred?.let { id -> created.children.firstOrNull { it.childId == id } }
            ?: created.children.first()
        return PairingChildContext(created.familyId, primary.childId, primary.displayName)
    }

    private suspend fun resolveFromFamily(
        familyId: String,
        preferredChildId: String?,
    ): PairingChildContext? {
        if (familyId.isBlank()) return null
        val children = runCatching { familyStore.listChildren(familyId) }.getOrDefault(emptyList())
        if (children.isEmpty()) return null
        val child = preferredChildId?.let { id -> children.firstOrNull { it.childId == id } }
        if (preferredChildId != null && child == null) {
            throw AppErrorException(
                AppError.Validation("That child profile was not found. Go back and try again."),
            )
        }
        val resolved = child ?: children.first()
        return PairingChildContext(familyId, resolved.childId, resolved.displayName)
    }

    private fun generateBootstrapPin(): String {
        val random = SecureRandom()
        return buildString(6) {
            repeat(6) { append(random.nextInt(10)) }
        }
    }
}
