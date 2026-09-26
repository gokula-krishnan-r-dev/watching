package com.meritscreen.core.common.auth

import com.meritscreen.core.common.config.AppConfig

/**
 * Extracts a parent email OTP from autofill payloads, clipboard text, or email body
 * snippets. Never logs the returned code.
 */
object OtpCodeExtractor {

    private val keywordPattern = Regex(
        pattern = """(?i)(?:(?:merit\s*screen|watching)[^0-9]{0,48})?(?:verification\s+)?code(?:\s*(?:is|:))?\s*[#:]?\s*(\d{${AppConfig.EMAIL_OTP_LENGTH}})\b""",
    )

    private val leadingCodePattern = Regex(
        pattern = """(?i)^\s*(\d{${AppConfig.EMAIL_OTP_LENGTH}})\s+is your\b""",
    )

    private val standalonePattern = Regex(
        pattern = """(?<!\d)(\d{${AppConfig.EMAIL_OTP_LENGTH}})(?!\d)""",
    )

    /**
     * @return a digit string of [AppConfig.EMAIL_OTP_LENGTH], or null when no confident match.
     */
    fun extract(raw: CharSequence?, length: Int = AppConfig.EMAIL_OTP_LENGTH): String? {
        if (raw.isNullOrBlank() || length !in 4..8) return null
        val text = raw.toString().trim()

        leadingCodePattern.find(text)?.groupValues?.getOrNull(1)
            ?.takeIf { it.length == length }
            ?.let { return it }

        keywordPattern.find(text)?.groupValues?.getOrNull(1)
            ?.takeIf { it.length == length }
            ?.let { return it }

        val standalone = standalonePattern.findAll(text)
            .map { it.groupValues[1] }
            .filter { it.length == length }
            .distinct()
            .toList()

        // Only autofill when exactly one candidate — avoids grabbing phone fragments.
        return standalone.singleOrNull()
    }
}
