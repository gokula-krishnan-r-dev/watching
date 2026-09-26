package com.meritscreen.feature.authentication.domain

import com.meritscreen.core.analytics.AnalyticsEvent
import com.meritscreen.core.analytics.AnalyticsTracker
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.core.common.session.ParentSession
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.firebase.auth.AuthClient
import com.meritscreen.core.firebase.family.FamilyDraft
import com.meritscreen.core.firebase.family.FamilyDraftChild
import com.meritscreen.core.firebase.family.FamilyStore
import com.meritscreen.core.network.NetworkMonitor
import com.meritscreen.feature.onboarding.data.OnboardingDraftRepository
import java.util.Locale
import javax.inject.Inject

class CompleteParentAuthUseCase @Inject constructor(
    private val authClient: AuthClient,
    private val emailOtpClient: com.meritscreen.core.firebase.auth.EmailOtpClient,
    private val familyStore: FamilyStore,
    private val draftRepository: OnboardingDraftRepository,
    private val parentSessionRepository: ParentSessionRepository,
    private val networkMonitor: NetworkMonitor,
    private val analyticsTracker: AnalyticsTracker,
) {
    suspend fun sendEmailOtp(email: String): Outcome<Unit> = wrapVoid {
        requireOnline()
        val error = CredentialsValidator.emailError(email)
        if (error != null) {
            throw AppErrorException(AppError.Validation(error))
        }
        emailOtpClient.sendOtp(email.trim())
    }

    suspend fun verifyEmailOtp(email: String, code: String): Outcome<ParentAuthResult> =
        wrap {
            requireOnline()
            val error = CredentialsValidator.otpError(code)
            if (error != null) {
                throw AppErrorException(AppError.Validation(error))
            }
            val customToken = emailOtpClient.verifyOtp(email.trim(), code.trim())
            authClient.signInWithCustomToken(customToken)
            finishAuthenticatedSession()
        }

    suspend fun signInWithGoogleIdToken(idToken: String): Outcome<ParentAuthResult> =
        wrap {
            requireOnline()
            authClient.signInWithGoogleIdToken(idToken)
            finishAuthenticatedSession()
        }


    /**
     * If Firebase already has a parent session (e.g. user finished onboarding after a partial
     * sign-up), commit the local draft / restore [ParentSession] without asking for credentials again.
     */
    suspend fun resumeIfAlreadySignedIn(): Outcome<ParentAuthResult>? {
        val user = authClient.refreshCurrentUser() ?: return null
        if (user.isChildDevice) return null
        return wrap { finishAuthenticatedSession() }
    }

    suspend fun finishAuthenticatedSession(): ParentAuthResult {
        authClient.ensureIdToken(forceRefresh = true)
        val user = authClient.currentUser ?: throw AppErrorException(AppError.Auth())
        if (user.isChildDevice) {
            throw AppErrorException(AppError.Auth("This device is paired as a child device."))
        }
        val existingFamilyId = familyStore.getUserFamilyId(user.uid)
        if (existingFamilyId != null) {
            val children = familyStore.listChildren(existingFamilyId)
            val draft = draftRepository.current()
            draft.parentPinHash?.let { familyStore.updateParentPinHash(existingFamilyId, it) }
            parentSessionRepository.set(
                ParentSession(
                    uid = user.uid,
                    familyId = existingFamilyId,
                    childIds = children.map { it.childId },
                ),
            )
            draftRepository.clear()
            analyticsTracker.track(AnalyticsEvent.ParentSignedIn)
            return ParentAuthResult(
                familyId = existingFamilyId,
                childIds = children.map { it.childId },
                isNewFamily = false,
                needsOnboarding = false,
            )
        }
        val draft = draftRepository.current()
        if (!draft.isReadyToCommit) {
            return ParentAuthResult(
                familyId = null,
                childIds = emptyList(),
                isNewFamily = false,
                needsOnboarding = true,
            )
        }
        val pinHash = draft.parentPinHash
            ?: throw AppErrorException(AppError.Validation("Set a Parent PIN before creating the family."))
        val created = familyStore.createFamilyFromDraft(
            uid = user.uid,
            email = user.email,
            draft = FamilyDraft(
                // Value-tour → Add Child path may skip Create Family; use a safe default.
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
        draftRepository.clear()
        analyticsTracker.track(AnalyticsEvent.ParentSignedIn)
        return ParentAuthResult(
            familyId = created.familyId,
            childIds = created.children.map { it.childId },
            isNewFamily = true,
            needsOnboarding = false,
        )
    }

    private fun requireOnline() {
        if (!networkMonitor.isCurrentlyOnline()) {
            throw AppErrorException(AppError.Network())
        }
    }

    private suspend fun wrap(block: suspend () -> ParentAuthResult): Outcome<ParentAuthResult> = try {
        Outcome.Success(block())
    } catch (error: AppErrorException) {
        Outcome.Failure(error.error)
    } catch (error: Throwable) {
        Outcome.Failure(com.meritscreen.core.firebase.error.FirebaseErrorMapper.from(error))
    }

    private suspend fun wrapVoid(block: suspend () -> Unit): Outcome<Unit> = try {
        block()
        Outcome.Success(Unit)
    } catch (error: AppErrorException) {
        Outcome.Failure(error.error)
    } catch (error: Throwable) {
        Outcome.Failure(com.meritscreen.core.firebase.error.FirebaseErrorMapper.from(error))
    }
}

