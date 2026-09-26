package com.meritscreen.core.firebase.pairing

import com.google.firebase.functions.FirebaseFunctions
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.firebase.error.FirebaseErrorMapper
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebasePairingClient @Inject constructor(
    private val functions: FirebaseFunctions,
    private val dispatchers: AppDispatchers,
) : PairingClient {

    override suspend fun createToken(childId: String): PairingOffer = runPairing {
        val data = functions
            .getHttpsCallable("createPairingToken")
            .call(mapOf("childId" to childId))
            .await()
            .data
        val map = asMap(data)
        PairingOffer(
            code = requiredString(map, "code"),
            secret = requiredString(map, "secret"),
            expiresAtEpochMs = requiredLong(map, "expiresAtEpochMs"),
            qrPayload = requiredString(map, "qrPayload"),
            childId = requiredString(map, "childId"),
            familyId = requiredString(map, "familyId"),
        )
    }

    override suspend fun consumeToken(
        code: String,
        secret: String?,
        deviceId: String,
    ): PairingResult = runPairing {
        val payload = buildMap {
            put("code", code)
            put("deviceId", deviceId)
            if (!secret.isNullOrBlank()) put("secret", secret)
        }
        val data = functions
            .getHttpsCallable("consumePairingToken")
            .call(payload)
            .await()
            .data
        val map = asMap(data)
        PairingResult(
            customToken = requiredString(map, "customToken"),
            familyId = requiredString(map, "familyId"),
            childId = requiredString(map, "childId"),
            deviceId = requiredString(map, "deviceId"),
            parentPinHash = requiredString(map, "parentPinHash"),
            displayName = map["displayName"] as? String ?: "",
            ageBand = map["ageBand"] as? String ?: "",
            avatarId = map["avatarId"] as? String ?: "",
        )
    }

    private suspend fun <T> runPairing(block: suspend () -> T): T = try {
        withContext(dispatchers.io) { block() }
    } catch (error: AppErrorException) {
        throw error
    } catch (error: Throwable) {
        throw AppErrorException(FirebaseErrorMapper.from(error), error)
    }

    private fun asMap(data: Any?): Map<*, *> =
        data as? Map<*, *> ?: emptyMap<Any, Any>()

    private fun requiredString(map: Map<*, *>, key: String): String =
        map[key] as? String ?: throw AppErrorException(
            com.meritscreen.core.common.error.AppError.Unknown(),
        )

    private fun requiredLong(map: Map<*, *>, key: String): Long {
        return when (val value = map[key]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }
}
