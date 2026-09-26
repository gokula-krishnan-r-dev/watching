package com.meritscreen.core.common.domain

/**
 * Lightweight heuristics for parent App Rules filters. Categories are not uploaded from
 * the child device (inventory is packageName + label only), so we classify locally.
 */
object AppInventoryCategorizer {

    enum class Category {
        EDUCATIONAL,
        ENTERTAINMENT,
        SYSTEM,
        OTHER,
    }

    data class Classification(
        val category: Category,
        val subtitle: String,
        val verifiedSafe: Boolean,
    )

    fun classify(packageName: String, label: String): Classification {
        val pkg = packageName.lowercase()
        val name = label.lowercase()

        if (isSystem(pkg, name)) {
            return Classification(
                category = Category.SYSTEM,
                subtitle = "System / device tool",
                verifiedSafe = true,
            )
        }
        if (isEducational(pkg, name)) {
            return Classification(
                category = Category.EDUCATIONAL,
                subtitle = "Learning & skills",
                verifiedSafe = true,
            )
        }
        if (isEntertainment(pkg, name)) {
            return Classification(
                category = Category.ENTERTAINMENT,
                subtitle = entertainmentSubtitle(pkg, name),
                verifiedSafe = false,
            )
        }
        return Classification(
            category = Category.OTHER,
            subtitle = packageName.substringAfterLast('.').ifBlank { "Installed app" },
            verifiedSafe = false,
        )
    }

    private fun isSystem(pkg: String, name: String): Boolean {
        if (pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.dialer") ||
            pkg.startsWith("com.samsung.android.") && (name.contains("phone") || name.contains("contact"))
        ) {
            return true
        }
        val systemTokens = listOf("dialer", "phone", "contacts", "settings", "camera", "gallery", "photos")
        return systemTokens.any { token -> pkg.contains(token) || name.contains(token) } &&
            !pkg.contains("youtube") && !pkg.contains("game")
    }

    private fun isEducational(pkg: String, name: String): Boolean {
        val tokens = listOf(
            "khan", "duolingo", "duo", "scratch", "abcmouse", "epic", "reading", "math",
            "learn", "school", "education", "classroom", "quizlet", "photomath", "brainly",
            "brilliant", "coursera", "udemy", "wikipedia", "dictionary", "spell",
        )
        return tokens.any { token -> pkg.contains(token) || name.contains(token) }
    }

    private fun isEntertainment(pkg: String, name: String): Boolean {
        val tokens = listOf(
            "youtube", "netflix", "disney", "hulu", "twitch", "tiktok", "roblox", "minecraft",
            "game", "play", "steam", "spotify", "music", "cinema", "movie", "video",
        )
        return tokens.any { token -> pkg.contains(token) || name.contains(token) }
    }

    private fun entertainmentSubtitle(pkg: String, name: String): String = when {
        pkg.contains("youtube") || name.contains("youtube") -> "Videos • consider a time cap"
        pkg.contains("roblox") || name.contains("roblox") -> "Online sandbox • restricted by default"
        else -> "Entertainment"
    }
}
