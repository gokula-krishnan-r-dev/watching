package com.meritscreen.core.common.logging

object LogSanitizer {
    private val sensitiveKeys = setOf(
        "email", "password", "token", "idtoken", "pin", "secret", "authorization",
        "pairing", "refresh", "apikey", "customtoken", "pairingcode", "pinhash",
        "fcmtoken", "parentpin",
    )

    fun sanitize(value: Any?): String {
        val raw = value?.toString() ?: "null"
        if (looksSensitive(raw)) return "<redacted>"
        return raw.take(120)
    }

    fun sanitizeKey(key: String, value: Any?): String {
        if (sensitiveKeys.any { key.contains(it, ignoreCase = true) }) return "<redacted>"
        return sanitize(value)
    }

    private fun looksSensitive(raw: String): Boolean {
        return raw.contains('@') && raw.contains('.') ||
            raw.startsWith("eyJ") ||
            raw.startsWith("meritscreen://pair") ||
            // 6-digit pairing codes alone are short; redact when logged as bare digits
            // of that length only if they look like codes in longer strings above.
            raw.length > 80
    }
}
