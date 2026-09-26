package com.meritscreen.core.firebase.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase AI Logic client for quiz pack generation.
 * App Check is installed at application start and attached by the Firebase AI SDK.
 *
 * Tries [AppConfig.GEMINI_FLASH_MODEL] (+ fallbacks) on the Gemini Developer API first,
 * then the same models on Vertex AI / Agent Platform when the Developer API rejects the
 * call for billing / prepaid-credit reasons (common on local demos after free credit burn).
 */
@Singleton
class FirebaseQuizAiPackClient @Inject constructor(
    private val appLogger: AppLogger,
) : QuizAiPackClient {

    override suspend fun generatePackJson(
        systemInstruction: String,
        userPrompt: String,
    ): String? {
        val models = (listOf(AppConfig.GEMINI_FLASH_MODEL) + AppConfig.GEMINI_FLASH_MODEL_FALLBACKS)
            .distinct()
        val backends = listOf(
            BackendAttempt("googleAI", GenerativeBackend.googleAI()),
            BackendAttempt(
                "vertexAI",
                GenerativeBackend.vertexAI(AppConfig.GEMINI_VERTEX_LOCATION),
            ),
        )

        var lastError: Throwable? = null
        for ((backendIndex, backend) in backends.withIndex()) {
            for ((modelIndex, modelName) in models.withIndex()) {
                val result = runCatching {
                    generateWith(backend.backend, modelName, systemInstruction, userPrompt)
                }
                val text = result.getOrNull()
                if (!text.isNullOrBlank()) {
                    if (backendIndex > 0 || modelIndex > 0) {
                        appLogger.i(
                            "Quiz AI used fallback",
                            "backend" to backend.label,
                            "model" to modelName,
                        )
                    } else {
                        appLogger.d(
                            "Quiz AI pack generated",
                            "backend" to backend.label,
                            "model" to modelName,
                            "chars" to text.length,
                        )
                    }
                    return text
                }
                val error = result.exceptionOrNull() ?: continue
                lastError = error
                val hasModelFallback = modelIndex < models.lastIndex
                val hasBackendFallback = backendIndex < backends.lastIndex
                when {
                    isBillingOrQuotaExhausted(error) && hasBackendFallback -> {
                        appLogger.w(
                            "Quiz AI billing/quota blocked; trying next backend",
                            error,
                            "backend" to backend.label,
                            "model" to modelName,
                        )
                        break // next backend
                    }
                    isModelUnavailable(error) && hasModelFallback -> {
                        appLogger.w(
                            "Quiz AI model unavailable; trying fallback",
                            error,
                            "backend" to backend.label,
                            "model" to modelName,
                        )
                        continue
                    }
                    hasModelFallback && isTransientModelError(error) -> {
                        appLogger.w(
                            "Quiz AI transient model error; trying fallback",
                            error,
                            "backend" to backend.label,
                            "model" to modelName,
                        )
                        continue
                    }
                    hasBackendFallback -> {
                        appLogger.w(
                            "Quiz AI backend failed; trying next backend",
                            error,
                            "backend" to backend.label,
                            "model" to modelName,
                        )
                        break
                    }
                    else -> throw error
                }
            }
        }
        lastError?.let { throw it }
        return null
    }

    private suspend fun generateWith(
        backend: GenerativeBackend,
        modelName: String,
        systemInstruction: String,
        userPrompt: String,
    ): String? {
        val model = Firebase.ai(backend = backend)
            .generativeModel(
                modelName = modelName,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    temperature = 0.7f
                    maxOutputTokens = 8192
                },
                systemInstruction = content { text(systemInstruction) },
            )
        val response = model.generateContent(userPrompt)
        return response.text?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun isModelUnavailable(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return "no longer available" in message ||
            "not found" in message ||
            "is not found" in message ||
            "unsupported model" in message ||
            ("models/" in message && "unavailable" in message)
    }

    private fun isBillingOrQuotaExhausted(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return "prepayment credits are depleted" in message ||
            "credits are depleted" in message ||
            "billing" in message && ("require" in message || "enable" in message || "manage" in message) ||
            "quota exceeded" in message ||
            "resource exhausted" in message ||
            "insufficient" in message && "credit" in message
    }

    private fun isTransientModelError(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return "unavailable" in message ||
            "high demand" in message ||
            "try again" in message ||
            "503" in message ||
            "500" in message
    }

    private data class BackendAttempt(
        val label: String,
        val backend: GenerativeBackend,
    )
}
