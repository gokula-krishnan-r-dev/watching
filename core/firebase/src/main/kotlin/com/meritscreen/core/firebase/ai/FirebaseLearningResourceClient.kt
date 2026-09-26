package com.meritscreen.core.firebase.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.BlogSection
import com.meritscreen.core.common.domain.LearningBlogArticle
import com.meritscreen.core.common.domain.LearningResource
import com.meritscreen.core.common.domain.LearningResourceVideo
import com.meritscreen.core.common.domain.StaticLearningResourceCatalog
import com.meritscreen.core.common.logging.AppLogger
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase AI Client for dynamically generating rich educational explanations,
 * blog notes, and learning resources when a child answers incorrectly.
 *
 * Backed by Gemini Flash via Firebase AI Logic with an in-memory cache and
 * instant fallback to [StaticLearningResourceCatalog].
 */
@Singleton
class FirebaseLearningResourceClient @Inject constructor(
    private val appLogger: AppLogger,
) {
    private val memoryCache = ConcurrentHashMap<String, LearningResource>()

    /**
     * Fetches an enriched AI learning resource for the specified concept and wrong answer.
     * Guaranteed to finish or time out within 4 seconds without stalling the UI.
     */
    suspend fun generateOrFetchResource(
        prompt: String,
        wrongAnswerText: String,
        correctAnswerText: String,
        topic: String,
        conceptId: String,
        conceptTitle: String,
        ageBand: AgeBand = AgeBand.AGE_7_TO_9,
    ): LearningResource? {
        val cacheKey = "$conceptId-$wrongAnswerText-$correctAnswerText"
        memoryCache[cacheKey]?.let { return it }

        val baseline = StaticLearningResourceCatalog.getFor(conceptId, topic, conceptTitle)

        return runCatching {
            withTimeoutOrNull(4000L) {
                generateFromGemini(
                    prompt = prompt,
                    wrongAnswerText = wrongAnswerText,
                    correctAnswerText = correctAnswerText,
                    topic = topic,
                    conceptTitle = conceptTitle,
                    ageBand = ageBand,
                    baseline = baseline,
                )
            }
        }.getOrNull()?.also { resource ->
            memoryCache[cacheKey] = resource
        }
    }

    private suspend fun generateFromGemini(
        prompt: String,
        wrongAnswerText: String,
        correctAnswerText: String,
        topic: String,
        conceptTitle: String,
        ageBand: AgeBand,
        baseline: LearningResource,
    ): LearningResource? {
        val models = (listOf(AppConfig.GEMINI_FLASH_MODEL) + AppConfig.GEMINI_FLASH_MODEL_FALLBACKS).distinct()
        val backends = listOf(
            GenerativeBackend.googleAI(),
            GenerativeBackend.vertexAI(AppConfig.GEMINI_VERTEX_LOCATION),
        )

        val systemInstruction = """
            You are a cheerful, friendly, world-class educator teaching children aged 7-12.
            The child just answered a quiz question incorrectly.
            Explain why the wrong answer is an understandable mistake, explain why the correct answer is right,
            and provide a step-by-step breakdown using relatable, delightful examples (such as pizza, toys, spaceships, or animals).
            Also recommend 1 or 2 high-quality educational YouTube video lessons from trusted creators (e.g. Khan Academy, Math Antics, CrashCourse Kids, SciShow Kids, Numberblocks) specifically matching this exact topic.
            
            Return strictly valid JSON with this exact schema:
            {
              "blogTitle": "string",
              "readingTimeMinutes": 2,
              "coreRule": "string (1 memorable sentence)",
              "whyWrongExplanation": "string (gentle, encouraging explanation of why that specific answer didn't match)",
              "stepByStep": ["step 1", "step 2", "step 3"],
              "memoryTrick": "string (fun mnemonic or rhyme)",
              "funFact": "string (curious, exciting fact for kids)",
              "videos": [
                {
                  "videoId": "string (11-character YouTube video ID if known, or empty string)",
                  "title": "string (engaging lesson title for kids)",
                  "channelName": "string (e.g. Khan Academy, Math Antics)",
                  "durationLabel": "string (e.g. '5:10')",
                  "description": "string (clear summary of what this video lesson teaches)"
                }
              ]
            }
        """.trimIndent()

        val userPrompt = """
            Child Age Band: ${ageBand.name}
            Topic: $topic
            Concept: $conceptTitle
            Question: "$prompt"
            Child's Answer (Incorrect): "$wrongAnswerText"
            Correct Answer: "$correctAnswerText"
            
            Generate the kid-friendly explanation and study blog JSON now.
        """.trimIndent()

        for (backend in backends) {
            for (modelName in models) {
                val jsonText = runCatching {
                    val model = Firebase.ai(backend = backend).generativeModel(
                        modelName = modelName,
                        generationConfig = generationConfig {
                            responseMimeType = "application/json"
                            temperature = 0.5f
                            maxOutputTokens = 2048
                        },
                        systemInstruction = content { text(systemInstruction) },
                    )
                    model.generateContent(userPrompt).text?.trim()
                }.getOrNull()

                if (!jsonText.isNullOrBlank()) {
                    val parsed = parseJsonResponse(jsonText, baseline)
                    if (parsed != null) {
                        appLogger.i("Firebase AI generated learning resource for $conceptTitle", "model" to modelName)
                        return parsed
                    }
                }
            }
        }
        return null
    }

    private fun parseJsonResponse(jsonStr: String, baseline: LearningResource): LearningResource? {
        return runCatching {
            val clean = jsonStr.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = JSONObject(clean)
            val title = obj.optString("blogTitle", baseline.summaryBlog.title)
            val readingTime = obj.optInt("readingTimeMinutes", 2)
            val coreRule = obj.optString("coreRule", baseline.summaryBlog.coreRule)
            val whyWrong = obj.optString("whyWrongExplanation", "")
            val memoryTrick = obj.optString("memoryTrick", baseline.summaryBlog.quickMemoryTip)
            val funFact = obj.optString("funFact", baseline.summaryBlog.funFact.orEmpty())

            val steps = mutableListOf<String>()
            val stepsArray = obj.optJSONArray("stepByStep")
            if (stepsArray != null) {
                for (i in 0 until stepsArray.length()) {
                    steps.add(stepsArray.optString(i))
                }
            }

            val sections = mutableListOf<BlogSection>()
            if (whyWrong.isNotBlank()) {
                sections.add(
                    BlogSection(
                        heading = "Let's Look Closer 🔍",
                        content = whyWrong,
                        bulletPoints = emptyList(),
                    ),
                )
            }
            if (steps.isNotEmpty()) {
                sections.add(
                    BlogSection(
                        heading = "Step-by-Step Walkthrough 📝",
                        content = "Here is how to solve it easily every single time:",
                        bulletPoints = steps,
                    ),
                )
            }
            if (sections.isEmpty()) {
                sections.addAll(baseline.summaryBlog.sections)
            }

            val aiVideos = mutableListOf<LearningResourceVideo>()
            val videosArray = obj.optJSONArray("videos")
            if (videosArray != null) {
                for (i in 0 until videosArray.length()) {
                    val vObj = videosArray.optJSONObject(i) ?: continue
                    val vId = vObj.optString("videoId").trim()
                    val vTitle = vObj.optString("title").trim()
                    val vChannel = vObj.optString("channelName", "Educational Lesson").trim()
                    val vDuration = vObj.optString("durationLabel", "4:30").trim()
                    val vDesc = vObj.optString("description").trim()

                    // Validate YouTube video ID format (11 chars: alphanumeric, _, -)
                    val resolvedId = if (vId.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                        vId
                    } else {
                        // Fallback to verified playable video ID for this topic/concept
                        baseline.videos.firstOrNull()?.videoId ?: "LGqBQrUYua4"
                    }

                    if (vTitle.isNotBlank()) {
                        aiVideos.add(
                            LearningResourceVideo(
                                videoId = resolvedId,
                                title = vTitle,
                                channelName = vChannel,
                                durationLabel = vDuration,
                                description = vDesc.ifBlank { "Learn with step-by-step visual examples." },
                            ),
                        )
                    }
                }
            }

            val finalVideos = if (aiVideos.isNotEmpty()) aiVideos else baseline.videos

            val article = LearningBlogArticle(
                title = title,
                readingTimeMinutes = readingTime,
                coreRule = coreRule,
                sections = sections,
                quickMemoryTip = memoryTrick,
                funFact = funFact.takeIf { it.isNotBlank() } ?: baseline.summaryBlog.funFact,
            )

            baseline.copy(
                summaryBlog = article,
                videos = finalVideos,
                isAiGenerated = true,
            )
        }.getOrNull()
    }
}
