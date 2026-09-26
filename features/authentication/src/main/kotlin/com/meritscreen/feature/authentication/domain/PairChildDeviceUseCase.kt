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
            val result = pairingClient.consumeToken(digits, secret, deviceId)
            authClient.signInWithCustomToken(result.customToken)
            pairingStore.set(
                ChildPairingCredential(
                    familyId = result.familyId,
                    childId = result.childId,
                    deviceId = result.deviceId,
                    parentPinHash = result.parentPinHash,
                    displayName = result.displayName,
                ),
            )
            analyticsTracker.track(AnalyticsEvent.ChildDevicePaired)
            Outcome.Success(Unit)
        } catch (error: AppErrorException) {
            Outcome.Failure(error.error)
        } catch (error: Throwable) {
            Outcome.Failure(com.meritscreen.core.firebase.error.FirebaseErrorMapper.from(error))
        }
    }
}
