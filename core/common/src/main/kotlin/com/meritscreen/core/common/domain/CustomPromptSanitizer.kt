package com.meritscreen.core.common.domain

import com.meritscreen.core.common.config.AppConfig

/**
 * Pure Kotlin prompt sanitizer guarding against prompt-injection and adversarial
 * instruction hijacking inside parent-provided focus guidelines (docs/08 §2).
 */
object CustomPromptSanitizer {

    private val BLOCKED_PATTERNS = listOf(
        "ignore previous",
        "ignore all previous",
        "system:",
        "you are now",
        "developer message",
        "new instructions",
        "<|",
        "```system",
        "as an ai",
        "jailbreak",
        "bypass rules",
    )

    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim().take(AppConfig.CUSTOM_PROMPT_GUIDELINES_MAX_CHARS)
        val lower = trimmed.lowercase()
        return if (BLOCKED_PATTERNS.any { it in lower }) "" else trimmed
    }
}
