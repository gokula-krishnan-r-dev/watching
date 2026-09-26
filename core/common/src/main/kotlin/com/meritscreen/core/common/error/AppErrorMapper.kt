package com.meritscreen.core.common.error

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object AppErrorMapper {
    fun from(throwable: Throwable): AppError {
        val cause = throwable.cause ?: throwable
        return when {
            throwable is AppErrorException -> throwable.error
            cause is UnknownHostException || cause is SocketTimeoutException -> AppError.Network()
            cause is IOException -> AppError.Network()
            isAuthFailure(throwable) || isAuthFailure(cause) -> AppError.Auth()
            else -> AppError.Unknown()
        }
    }

    private fun isAuthFailure(error: Throwable): Boolean {
        val name = error::class.java.name
        return name.contains("FirebaseAuth", ignoreCase = true) ||
            name.contains("GoogleAuth", ignoreCase = true)
    }
}

class AppErrorException(val error: AppError, cause: Throwable? = null) : Exception(error.userMessage, cause)
