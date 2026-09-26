package com.meritscreen.feature.child.data

import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.feature.child.domain.MiniLesson
import com.meritscreen.feature.child.domain.QuizChoice
import com.meritscreen.feature.child.domain.VisualTaxonomy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import java.util.UUID

/**
 * Parses + validates Gemini quiz pack JSON before Room upsert (docs/06 + 08).
 * Rejects items silently so the builtin bank keeps serving.
 */
object QuizPackValidator {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    data class PackItemDto(
        val id: String? = null,
        val ageBand: String? = null,
        val topic: String,
        val conceptId: String,
        val conceptTitle: String,
        val difficulty: Int,
        val prompt: String,
        val choices: List<ChoiceDto>,
        val whyCorrect: String,
        val whyWrongByChoice: Map<String, String> = emptyMap(),
        val conceptExplainer: String,
        val language: String = "en",
        val interactionType: String = "TAP_TEXT",
        val promptTag: String? = null,
        val promptCount: Int? = null,
        val promptAudioKey: String? = null,
        val miniLesson: MiniLessonDto? = null,
    )

    @Serializable
    data class ChoiceDto(
        val id: String,
        val text: String = "",
        val correct: Boolean,
        val imageTag: String? = null,
        val audioKey: String? = null,
    )

    @Serializable
    data class MiniLessonDto(
        val title: String,
        val bodyLines: List<String> = emptyList(),
        val illustrationAssetId: String? = null,
    )

    @Serializable
    private data class WrappedPack(val items: List<PackItemDto> = emptyList())

    data class ValidatedItem(
        val id: String,
        val ageBand: AgeBand,
        val topic: String,
        val conceptId: String,
        val conceptTitle: String,
        val difficulty: Int,
        val prompt: String,
        val choices: List<QuizChoice>,
        val whyCorrect: String,
        val whyWrongByChoice: Map<String, String>,
        val conceptExplainer: String,
        val language: String,
        val interactionType: String,
        val promptTag: String?,
        val promptCount: Int?,
        val promptAudioKey: String?,
        val miniLesson: MiniLesson?,
    )

    fun parseAndValidate(
        rawJson: String,
        expectedAgeBand: AgeBand,
        avoidIds: Set<String>,
        knownTaxonomyTags: Set<String> = VisualTaxonomy.allTags(),
    ): List<ValidatedItem> {
        val items = decodeItems(rawJson) ?: return emptyList()
        val accepted = linkedMapOf<String, ValidatedItem>()
        for (item in items) {
            val validated = validateOne(item, expectedAgeBand, avoidIds + accepted.keys, knownTaxonomyTags)
                ?: continue
            accepted[validated.id] = validated
        }
        return accepted.values.toList()
    }

    private fun decodeItems(rawJson: String): List<PackItemDto>? {
        val trimmed = rawJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching {
            val element = json.parseToJsonElement(trimmed)
            when (element) {
                is JsonArray -> json.decodeFromJsonElement(ListSerializer(PackItemDto.serializer()), element)
                is JsonObject -> {
                    if (element.containsKey("items")) {
                        json.decodeFromJsonElement(WrappedPack.serializer(), element).items
                    } else {
                        listOf(json.decodeFromJsonElement(PackItemDto.serializer(), element))
                    }
                }
                else -> emptyList()
            }
        }.getOrNull()
    }

    private fun validateOne(
        item: PackItemDto,
        expectedAgeBand: AgeBand,
        avoidIds: Set<String>,
        knownTaxonomyTags: Set<String>,
    ): ValidatedItem? {
        if (item.topic.isBlank() || item.conceptId.isBlank() || item.prompt.isBlank()) return null
        if (item.whyCorrect.isBlank() || item.conceptExplainer.isBlank()) return null
        if (item.difficulty !in 1..5) return null
        if (containsUnsafeLanguage(item.prompt) || containsUnsafeLanguage(item.whyCorrect)) return null

        val choices = item.choices.mapNotNull { choice ->
            if (choice.id.isBlank()) return@mapNotNull null
            QuizChoice(
                id = choice.id,
                text = choice.text,
                correct = choice.correct,
                imageTag = choice.imageTag,
                audioKey = choice.audioKey,
            )
        }
        if (choices.size < 2) return null
        if (choices.count { it.correct } != 1) return null
        val wrongIds = choices.filter { !it.correct }.map { it.id }
        val whyWrong = wrongIds.associateWith { id ->
            item.whyWrongByChoice[id]?.takeIf { it.isNotBlank() }
                ?: "Not quite — try another answer."
        }

        var interaction = item.interactionType.ifBlank { "TAP_TEXT" }
        var promptTag = item.promptTag
        var promptCount = item.promptCount
        var promptAudioKey = item.promptAudioKey
        var validatedChoices = choices

        if (expectedAgeBand == AgeBand.AGE_3_TO_6) {
            val tags = buildList {
                promptTag?.let { add(it) }
                choices.mapNotNullTo(this) { it.imageTag }
            }
            val unknownTags = tags.any { it !in knownTaxonomyTags }
            val textOnly = choices.any { it.text.isNotBlank() }
            if (unknownTags && textOnly) {
                // Model invented tags — still usable as TAP_TEXT for this age band.
                interaction = "TAP_TEXT"
                promptTag = null
                promptCount = null
                promptAudioKey = null
                validatedChoices = choices.map { it.copy(imageTag = null, audioKey = null) }
            } else if (unknownTags) {
                return null
            } else if (interaction == "TAP_TEXT" && choices.all { it.text.isBlank() && it.imageTag == null }) {
                return null
            }
        }

        val mini = item.miniLesson?.let { lesson ->
            if (lesson.title.isBlank() || lesson.bodyLines.isEmpty()) return@let null
            val capped = lesson.bodyLines.take(3).map { it.trim() }.filter { it.isNotEmpty() }
            if (capped.isEmpty()) return@let null
            if (expectedAgeBand == AgeBand.AGE_3_TO_6 &&
                (item.conceptExplainer.split(Regex("\\s+")).size > 12 || capped.size > 1)
            ) {
                return@let MiniLesson(title = lesson.title.trim(), bodyLines = capped.take(1))
            }
            MiniLesson(title = lesson.title.trim(), bodyLines = capped)
        }

        val id = item.id?.takeIf { it.isNotBlank() && it !in avoidIds }
            ?: "ai_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        if (id in avoidIds) return null

        return ValidatedItem(
            id = id,
            ageBand = expectedAgeBand,
            topic = item.topic.trim().lowercase().replace(' ', '-'),
            conceptId = item.conceptId.trim(),
            conceptTitle = item.conceptTitle.ifBlank { item.conceptId }.trim(),
            difficulty = item.difficulty,
            prompt = item.prompt.trim(),
            choices = validatedChoices.shuffled(),
            whyCorrect = item.whyCorrect.trim(),
            whyWrongByChoice = whyWrong,
            conceptExplainer = item.conceptExplainer.trim(),
            language = item.language.ifBlank { "en" },
            interactionType = interaction,
            promptTag = promptTag,
            promptCount = promptCount,
            promptAudioKey = promptAudioKey,
            miniLesson = mini,
        )
    }

    private fun containsUnsafeLanguage(text: String): Boolean {
        val lower = text.lowercase()
        val blocked = listOf(
            "kill", "die", "suicide", "blood", "gun", "weapon", "hate you",
            "@gmail", "http://", "https://", "password",
        )
        return blocked.any { it in lower }
    }

    fun shouldGenerate(nearRungUnused: Int, aiCountForBand: Int): Boolean =
        aiCountForBand == 0 || nearRungUnused < AppConfig.QUIZ_PACK_LOW_THRESHOLD
}
