package com.meritscreen.core.common.error

/**
 * User-facing errors. Never carry raw Firebase/API messages into the UI layer.
 */
sealed interface AppError {
    val userMessage: String

    data class Network(
        override val userMessage: String = "Check your connection and try again.",
    ) : AppError

    data class Auth(
        override val userMessage: String = "Please sign in again to continue.",
    ) : AppError

    data class Permission(
        override val userMessage: String = "Watching needs a permission to continue.",
    ) : AppError

    data class NotFound(
        override val userMessage: String = "We couldn't find that information.",
    ) : AppError

    data class Validation(
        override val userMessage: String,
    ) : AppError

    data class OfflinePolicy(
        override val userMessage: String = "Using saved settings until the device is back online.",
    ) : AppError

    data class Unknown(
        override val userMessage: String = "Something went wrong. Please try again.",
    ) : AppError
}
