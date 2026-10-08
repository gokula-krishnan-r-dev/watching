package com.meritscreen.core.common.config

/**
 * Runtime-tunable product limits. Read these values instead of hard-coding them
 * in features. Later this can be backed by Remote Config without changing call sites.
 */
object AppConfig {
    const val MAX_CHILDREN_PER_PARENT: Int = 5
    const val PARENT_PIN_MIN_LENGTH: Int = 4
    const val PARENT_PIN_MAX_LENGTH: Int = 4
    const val PARENT_PIN_MAX_ATTEMPTS: Int = 5
    const val PARENT_PIN_LOCKOUT_MINUTES: Int = 5
    const val PAIRING_TOKEN_TTL_MINUTES: Int = 10
    const val PAIRING_CODE_LENGTH: Int = 6
    const val PAIRING_CONSUME_MAX_ATTEMPTS: Int = 8
    const val EMAIL_OTP_LENGTH: Int = 6
    const val EMAIL_OTP_TTL_MINUTES: Int = 10
    const val EMAIL_OTP_RESEND_COOLDOWN_SECONDS: Int = 42
    const val EMAIL_OTP_MAX_ATTEMPTS: Int = 5
    /** Default session block (minutes) when a parent newly allows an app or child allowlist is seeded. */
    const val DEFAULT_BLOCK_MINUTES: Int = 15
    const val DEFAULT_COOLDOWN_MINUTES: Int = 15
    /**
     * Session-block length chips on the parent allowlist rule card.
     * First value should match [DEFAULT_BLOCK_MINUTES] so creation defaults highlight 15m.
     */
    val ALLOWLIST_BLOCK_PRESET_MINUTES: List<Int> = listOf(15, 30, 45, 50, 60)
    /** XP granted on each passed quiz when stickers/rewards are enabled. */
    const val STICKER_QUIZ_PASS_XP: Int = 10
    /** Extra XP on Sat/Sun when [ChildPolicy.weekendBonusEnabled] is true. */
    const val STICKER_WEEKEND_BONUS_XP: Int = 5
    /** Extra minutes on pass when weekend bonus is enabled (added to extraMinutesOnPass). */
    const val WEEKEND_BONUS_EXTRA_MINUTES: Int = 5
    /**
     * Cumulative XP required to reach Explorer levels 1..N.
     * Index 0 unused; index 1 = level 1 (0 XP), index 2 = level 2 threshold, etc.
     */
    val EXPLORER_LEVEL_XP_THRESHOLDS: List<Int> = listOf(
        0, // padding
        0, 20, 50, 90, 140, 200, 270, 350, 450, 580,
    )
    const val EXPLORER_LEVEL_MAX: Int = 10
    /** Hard floor for session / cushion chunk length (minutes). */
    const val SESSION_CHUNK_MIN_MINUTES: Int = 2
    /** Hard ceiling for session / cushion chunk length (minutes). */
    const val SESSION_CHUNK_MAX_MINUTES: Int = 240
    /** Standard session chunks shown in parent Time Limits. */
    val SESSION_CHUNK_STANDARD_MINUTES: List<Int> = listOf(20, 30, 45)
    /** Quick cushion pop-up intervals (soft posture breaks). */
    val SESSION_CHUNK_CUSHION_MINUTES: List<Int> = listOf(2, 3, 5)
    /** Extra custom lengths parents can pick without free-typing. */
    val SESSION_CHUNK_EXTENDED_MINUTES: List<Int> = listOf(10, 15, 50, 60)
    const val DEFAULT_QUESTIONS_PER_QUIZ: Int = 3
    const val DEFAULT_PASS_SCORE_PERCENT: Int = 70
    const val USAGE_FLUSH_INTERVAL_SECONDS: Int = 45
    /**
     * How often an in-progress session snapshot is written to Room while nothing else changed.
     * Phase transitions / minute boundaries always flush immediately (docs/05).
     */
    const val SESSION_PERSIST_INTERVAL_SECONDS: Int = 45
    /** Asked question IDs retained for de-duplication; matches local history cleanup. */
    const val QUIZ_QUESTION_HISTORY_DAYS: Int = 30
    /** Parent dashboard: coalesce children-listener bursts before fan-out one-shot reads. */
    const val DASHBOARD_CHILDREN_DEBOUNCE_MS: Long = 350L

    /** Parent reports window options (days). Kept in AppConfig so Remote Config can override later. */
    const val REPORTS_TODAY_DAYS: Int = 1
    const val REPORTS_DEFAULT_DAYS: Int = 7
    const val REPORTS_EXTENDED_DAYS: Int = 30
    /** Hard ceiling for one-shot usage/quiz reads — matches docs/03 retention guidance. */
    const val REPORTS_MAX_DAYS: Int = 90
    /** Ceiling for one-shot quiz-attempt fetches on the parent reports screen. */
    const val REPORTS_MAX_ATTEMPTS: Int = 100
    /** Recent quiz attempts shown under the accuracy summary on P17. */
    const val REPORTS_RECENT_ATTEMPTS: Int = 8

    /** Dashboard and Child Detail controls */
    const val DEFAULT_DAILY_CEILING_MINUTES: Int = 120
    const val BONUS_TIME_MINUTES_DEFAULT: Int = 15
    const val DEFAULT_BEDTIME_LABEL: String = "8:00 PM"
    /**
     * Parent UI treats a device as connected when `lastSeenAt` is newer than this.
     * Aligns with the ~30 min heartbeat cadence plus a short grace window.
     */
    const val DEVICE_ONLINE_THRESHOLD_MINUTES: Int = 15
    /** Coalesce Child Detail device-listener bursts before fan-out one-shot reads. */
    const val CHILD_DETAIL_DEVICES_DEBOUNCE_MS: Long = 300L

    /** Adaptive quiz, learning and lockout configuration (docs 06 & 08) */
    const val QUIZ_LOCKOUT_SECONDS: Int = 30
    const val QUIZ_PACK_LOW_THRESHOLD: Int = 12
    /** How many AI items to request per background generation (docs/08). */
    const val QUIZ_PACK_GENERATE_COUNT: Int = 12
    const val CUSTOM_PROMPT_GUIDELINES_MAX_CHARS: Int = 500
    const val MEDIA_CACHE_MAX_MB: Int = 64
    /**
     * Primary Gemini Flash model for background quiz packs (Firebase AI Logic /
     * Gemini Developer API). Prefer a currently documented stable Flash id — see
     * https://firebase.google.com/docs/ai-logic/models
     */
    const val GEMINI_FLASH_MODEL: String = "gemini-3.7-flash"
    /**
     * Ordered fallbacks when [GEMINI_FLASH_MODEL] is retired or unavailable for new projects.
     * Longer-support models first where possible.
     */
    val GEMINI_FLASH_MODEL_FALLBACKS: List<String> = listOf(
        "gemini-2.5-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
    )
    /**
     * Vertex AI / Agent Platform region for Firebase AI Logic when the Gemini Developer API
     * is blocked (e.g. prepaid credits depleted). Must match a region enabled for the project.
     */
    const val GEMINI_VERTEX_LOCATION: String = "us-central1"

    /** Child→Firestore installed-app inventory (icons are tiny WEBP thumbnails, optional). */
    const val INSTALLED_APPS_MAX_COUNT: Int = 200
    const val INSTALLED_APP_LABEL_MAX_CHARS: Int = 80
    /** Decode size for synced parent thumbnails (not full adaptive icons). */
    const val APP_ICON_SYNC_SIZE_PX: Int = 48
    /** Skip any single thumbnail larger than this after WEBP compression. */
    const val APP_ICON_MAX_BYTES: Int = 3_072
    /** Soft budget for all icon payloads in one device inventory write. */
    const val APP_ICON_SYNC_BUDGET_BYTES: Int = 350_000
    const val APP_ICON_WEBP_QUALITY: Int = 72
}
