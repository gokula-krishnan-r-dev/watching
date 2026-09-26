package com.meritscreen.core.firebase.auth

interface EmailOtpClient {
    /**
     * Requests a 6-digit verification code to be sent to [email].
     */
    suspend fun sendOtp(email: String)

    /**
     * Verifies the 6-digit [code] for [email].
     * On success, returns the Firebase custom token to sign in the parent.
     */
    suspend fun verifyOtp(email: String, code: String): String
}
