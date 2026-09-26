package com.meritscreen.feature.authentication.domain

import com.meritscreen.core.common.config.AppConfig

object CredentialsValidator {
    private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun emailError(email: String): String? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return "Enter your email."
        if (!emailPattern.matches(trimmed)) return "Enter a valid email address."
        return null
    }

    fun isValidEmail(email: String): Boolean {
        return emailPattern.matches(email.trim())
    }

    fun otpError(code: String): String? {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return "Enter the 6-digit verification code."
        if (trimmed.length != AppConfig.EMAIL_OTP_LENGTH || !trimmed.all { it.isDigit() }) {
            return "Enter all ${AppConfig.EMAIL_OTP_LENGTH} digits of the verification code."
        }
        return null
    }
}

