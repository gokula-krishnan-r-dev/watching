package com.meritscreen.core.firebase.error

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorMapper

object FirebaseErrorMapper {
    fun from(throwable: Throwable): AppError {
        val auth = throwable as? FirebaseAuthException
            ?: throwable.cause as? FirebaseAuthException
        if (auth != null) return mapAuthCode(auth.errorCode)

        val functions = throwable as? FirebaseFunctionsException
            ?: throwable.cause as? FirebaseFunctionsException
        if (functions != null) return mapFunctions(functions)

        val firestore = throwable as? FirebaseFirestoreException
            ?: throwable.cause as? FirebaseFirestoreException
        if (firestore != null) return mapFirestore(firestore)

        return when (throwable) {
            is FirebaseNetworkException -> AppError.Network()
            else -> AppErrorMapper.from(throwable)
        }
    }

    private fun mapFirestore(error: FirebaseFirestoreException): AppError = when (error.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            AppError.Permission(
                "Couldn't reach your family data. Sign out, check your connection, and try again.",
            )
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        -> AppError.Network()
        FirebaseFirestoreException.Code.NOT_FOUND ->
            AppError.NotFound("We couldn't find that family data.")
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            AppError.Unknown(
                "Family data is still setting up. Wait a moment and try again.",
            )
        else -> AppErrorMapper.from(error)
    }

    private fun mapAuthCode(code: String): AppError = when (code) {
        "ERROR_INVALID_EMAIL" -> AppError.Validation("Enter a valid email address.")
        "ERROR_WEAK_PASSWORD" -> AppError.Validation("Use at least 8 characters for your password.")
        "ERROR_EMAIL_ALREADY_IN_USE" ->
            AppError.Validation("An account already exists for that email. Try signing in.")
        "ERROR_USER_NOT_FOUND",
        "ERROR_WRONG_PASSWORD",
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_LOGIN_CREDENTIALS",
        -> AppError.Auth("That email and password don't match.")
        "ERROR_USER_DISABLED" -> AppError.Auth("This account has been disabled.")
        "ERROR_TOO_MANY_REQUESTS" ->
            AppError.Auth("Too many attempts. Please wait a minute and try again.")
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
            AppError.Auth("This email is already used with a different sign-in method.")
        "ERROR_NETWORK_REQUEST_FAILED" -> AppError.Network()
        else -> AppError.Auth()
    }

    private fun mapFunctions(error: FirebaseFunctionsException): AppError {
        val rawMessage = error.message?.trim()
        val isTechnicalMessage = rawMessage.isNullOrBlank() ||
            rawMessage.equals("NOT_FOUND", ignoreCase = true) ||
            rawMessage.equals("INTERNAL", ignoreCase = true) ||
            rawMessage.equals("UNAVAILABLE", ignoreCase = true) ||
            rawMessage.equals("UNKNOWN", ignoreCase = true) ||
            rawMessage.contains("UNAUTHENTICATED", ignoreCase = true)

        val cleanMessage = if (isTechnicalMessage) null else rawMessage

        return when (error.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                AppError.Auth(cleanMessage ?: "Authentication expired. Please sign in again.")
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            -> AppError.Network(cleanMessage ?: "Connection timed out. Check your internet connection and try again.")
            FirebaseFunctionsException.Code.NOT_FOUND ->
                AppError.NotFound(
                    cleanMessage
                        ?: "That action isn’t available right now. Update the app or try again in a moment.",
                )
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.ALREADY_EXISTS,
            FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            -> AppError.Validation(cleanMessage ?: "Something doesn’t look right. Check your details and try again.")
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                AppError.Auth(cleanMessage ?: "Too many attempts. Please wait a few minutes before trying again.")
            FirebaseFunctionsException.Code.INTERNAL ->
                AppError.Unknown(cleanMessage ?: "Something went wrong on our side. Please try again.")
            else -> AppError.Unknown(cleanMessage ?: "Something went wrong. Please try again.")
        }
    }
}
