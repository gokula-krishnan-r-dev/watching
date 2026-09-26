package com.meritscreen.core.firebase.auth

import com.google.firebase.functions.FirebaseFunctions
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.firebase.error.FirebaseErrorMapper
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseEmailOtpClient @Inject constructor(
    private val functions: FirebaseFunctions,
    private val dispatchers: AppDispatchers,
) : EmailOtpClient {

    override suspend fun sendOtp(email: String): Unit = runOtp {
        functions
            .getHttpsCallable("sendEmailOtp")
            .call(mapOf("email" to email.trim()))
            .await()
    }

    override suspend fun verifyOtp(email: String, code: String): String = runOtp {
        val result = functions
            .getHttpsCallable("verifyEmailOtp")
            .call(
                mapOf(
                    "email" to email.trim(),
                    "code" to code.trim(),
                ),
            )
            .await()
            .data

        val map = result as? Map<*, *> ?: emptyMap<Any, Any>()
        map["customToken"] as? String
            ?: throw AppErrorException(AppError.Auth("Verification response was invalid. Please try again."))
    }

    private suspend fun <T> runOtp(block: suspend () -> T): T = try {
        withContext(dispatchers.io) { block() }
    } catch (error: AppErrorException) {
        throw error
    } catch (error: Throwable) {
        throw AppErrorException(FirebaseErrorMapper.from(error), error)
    }
}
