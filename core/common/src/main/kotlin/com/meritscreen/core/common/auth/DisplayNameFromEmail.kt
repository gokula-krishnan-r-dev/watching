package com.meritscreen.core.common.auth

import java.util.Locale

/**
 * Derives a parent-facing first name / greeting from auth identity.
 * Email local-parts often include digits (e.g. `gokulakrishnanr812@…`); those are stripped
 * so the dashboard shows letters only.
 */
object DisplayNameFromEmail {

    const val DEFAULT_FALLBACK: String = "there"

    /**
     * Strip the domain, keep only alphabetic letters, title-case the first character.
     * Returns [fallback] when nothing usable remains.
     */
    fun fromEmail(email: String?, fallback: String = DEFAULT_FALLBACK): String {
        val local = email.orEmpty().substringBefore('@').trim()
        val lettersOnly = local.filter { it.isLetter() }
        if (lettersOnly.isBlank()) return fallback
        return lettersOnly.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
        }
    }

    /**
     * Prefer a real auth [displayName] (letters + spaces) when present; otherwise derive from email.
     */
    fun fromAuth(
        displayName: String?,
        email: String?,
        fallback: String = DEFAULT_FALLBACK,
    ): String {
        val cleanedDisplay = displayName
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.map { token -> token.filter { it.isLetter() } }
            ?.filter { it.isNotBlank() }
            ?.joinToString(" ") { token ->
                token.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                }
            }
            .orEmpty()
        if (cleanedDisplay.isNotBlank()) return cleanedDisplay
        return fromEmail(email, fallback)
    }
}
