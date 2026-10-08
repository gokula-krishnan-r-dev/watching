package com.meritscreen.feature.authentication.domain

import com.meritscreen.core.analytics.AnalyticsEvent
import com.meritscreen.core.analytics.AnalyticsTracker
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.core.firebase.auth.AuthClient
import com.meritscreen.core.firebase.pairing.PairingClient
import com.meritscreen.core.network.NetworkMonitor
import com.meritscreen.core.security.pairing.ChildPairingCredential
import com.meritscreen.core.security.pairing.ChildPairingStore
import javax.inject.Inject

class PairChildDeviceUseCase @Inject constructor(
    private val pairingClient: PairingClient,
    private val authClient: AuthClient,
    private val pairingStore: ChildPairingStore,
    private val networkMonitor: NetworkMonitor,
    private val analyticsTracker: AnalyticsTracker,
) {
    suspend operator fun invoke(code: String, secret: String?): Outcome<Unit> {
        val digits = code.filter(Char::isDigit)
        if (digits.length != AppConfig.PAIRING_CODE_LENGTH) {
            return Outcome.Failure(
                AppError.Validation("Enter the ${AppConfig.PAIRING_CODE_LENGTH}-digit code from the parent phone."),
            )
        }
        if (!networkMonitor.isCurrentlyOnline()) {
            return Outcome.Failure(AppError.Network())
        }
        return try {
            val deviceId = pairingStore.getOrCreateDeviceId()
            val existingProfiles = pairingStore.listProfiles()
            val result = pairingClient.consumeToken(digits, secret, deviceId)
            val isNewProfile = existingProfiles
                .none { it.childId.equals(result.childId, ignoreCase = true) }
            if (isNewProfile && existingProfiles.size >= AppConfig.MAX_CHILDREN_PER_PARENT) {
                return Outcome.Failure(
                    AppError.Validation(
                        "This device already has ${AppConfig.MAX_CHILDREN_PER_PARENT} child profiles. " +
                            "Remove one from Parent menu before pairing another.",
                    ),
                )
            }
            authClient.signInWithCustomToken(result.customToken)
            pairingStore.set(
                ChildPairingCredential(
                    familyId = result.familyId,
                    childId = result.childId,
                    deviceId = result.deviceId,
                    parentPinHash = result.parentPinHash,
                    displayName = result.displayName,
                    avatarId = result.avatarId,
                ),
            )
            analyticsTracker.track(AnalyticsEvent.ChildDevicePaired)
            Outcome.Success(Unit)
        } catch (error: IllegalStateException) {
            Outcome.Failure(AppError.Validation(error.message ?: "Could not add another child on this device."))
        } catch (error: AppErrorException) {
            Outcome.Failure(error.error)
        } catch (error: Throwable) {
            Outcome.Failure(com.meritscreen.core.firebase.error.FirebaseErrorMapper.from(error))
        }
    }
}
