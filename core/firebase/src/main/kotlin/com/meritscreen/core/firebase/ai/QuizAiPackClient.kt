package com.meritscreen.core.firebase.ai

/**
 * Background-only Gemini pack generation via Firebase AI Logic.
 * Never call from Home / quiz ViewModels — workers only (docs/08).
 */
interface QuizAiPackClient {
    /**
     * Returns raw JSON text from Gemini (array or `{ "items": [...] }`).
     * Null when the model returns empty content; throws on hard transport failures.
     */
    suspend fun generatePackJson(
        systemInstruction: String,
        userPrompt: String,
    ): String?
}
