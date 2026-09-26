package com.meritscreen.feature.child.data

import android.content.Context
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.QuizAttemptUpload
import com.meritscreen.core.common.domain.SkillStateUpload
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.database.child.QuizAttemptDao
import com.meritscreen.core.database.child.QuizAttemptEntity
import com.meritscreen.core.database.child.QuizDao
import com.meritscreen.core.database.child.QuizItemEntity
import com.meritscreen.core.database.child.RecentQuestionEntity
import com.meritscreen.core.database.child.SkillStateEntity
import com.meritscreen.feature.child.domain.QuizChoice
import com.meritscreen.feature.child.domain.QuizQuestion
import com.meritscreen.feature.child.domain.TopicSkill
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuiltinQuizBankSeeder @Inject constructor(
    private val quizDao: QuizDao,
    private val dispatchers: AppDispatchers,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ensureSeeded(context: Context) = withContext(dispatchers.io) {
        if (quizDao.countItems() > 0) return@withContext
        val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val items = json.decodeFromString(ListSerializer(BuiltinQuizDto.serializer()), raw)
        quizDao.upsertItems(items.map { it.toEntity(json) })
    }

    private companion object {
        const val ASSET_NAME = "quiz_bank_builtin.json"
    }
}

@Serializable
private data class BuiltinQuizDto(
    val id: String,
    val ageBand: String,
    val topic: String,
    val conceptId: String,
    val conceptTitle: String,
    val difficulty: Int,
    val prompt: String,
    val choices: List<BuiltinChoiceDto>,
    val whyCorrect: String,
    val whyWrongByChoice: Map<String, String>,
    val conceptExplainer: String,
    val language: String = "en",
    val interactionType: String = "TAP_TEXT",
    val promptTag: String? = null,
    val promptCount: Int? = null,
    val promptAudioKey: String? = null,
    val miniLesson: com.meritscreen.feature.child.domain.MiniLesson? = null,
)

@Serializable
private data class BuiltinChoiceDto(
    val id: String,
    val text: String = "",
    val correct: Boolean,
    val imageTag: String? = null,
    val audioKey: String? = null,
)

private fun BuiltinQuizDto.toEntity(json: Json): QuizItemEntity = QuizItemEntity(
    id = id,
    ageBand = ageBand,
    topic = topic,
    conceptId = conceptId,
    conceptTitle = conceptTitle,
    difficulty = difficulty,
    prompt = prompt,
    choicesJson = json.encodeToString(ListSerializer(BuiltinChoiceDto.serializer()), choices),
    whyCorrect = whyCorrect,
    whyWrongByChoiceJson = json.encodeToString(
        kotlinx.serialization.serializer<Map<String, String>>(),
        whyWrongByChoice,
    ),
    conceptExplainer = conceptExplainer,
    language = language,
    source = "builtin",
    interactionType = interactionType,
    promptTag = promptTag,
    promptCount = promptCount,
    promptAudioKey = promptAudioKey,
    miniLessonJson = miniLesson?.let { json.encodeToString(com.meritscreen.feature.child.domain.MiniLesson.serializer(), it) },
)

@Singleton
class QuizRepository @Inject constructor(
    private val quizDao: QuizDao,
    private val dispatchers: AppDispatchers,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun questionsFor(ageBand: AgeBand, language: String = "en"): List<QuizQuestion> =
        withContext(dispatchers.io) {
            quizDao.listForAgeBand(ageBand.name, language).mapNotNull { it.toDomain(json) }
                .ifEmpty { quizDao.listForAgeBand(ageBand.name, "en").mapNotNull { it.toDomain(json) } }
        }

    suspend fun skills(childId: String): Map<String, TopicSkill> = withContext(dispatchers.io) {
        quizDao.listSkills(childId)
            .filter { it.conceptId == null }
            .associate { entity ->
                val weakIds = entity.weakConceptsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                val masteredIds = entity.masteredConceptsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                val titles = decodeTitles(entity.weakConceptTitlesJson)
                entity.topic to TopicSkill(
                    topic = entity.topic,
                    level = entity.level,
                    streakCorrect = entity.streakCorrect,
                    weakConcepts = weakIds,
                    conceptTitles = titles.filterKeys { it in weakIds },
                    masteredConcepts = masteredIds,
                    tierLabel = entity.tierLabel,
                    totalAttempts = entity.totalAttempts,
                    totalCorrect = entity.totalCorrect,
                    avgResponseTimeMs = entity.avgResponseTimeMs,
                )
            }
    }

    suspend fun saveTopicSkill(childId: String, skill: TopicSkill) = withContext(dispatchers.io) {
        quizDao.upsertSkill(
            SkillStateEntity(
                key = "$childId:${skill.topic}",
                childId = childId,
                topic = skill.topic,
                conceptId = null,
                level = skill.level,
                streakCorrect = skill.streakCorrect,
                weak = skill.weakConcepts.isNotEmpty(),
                weakConceptsCsv = skill.weakConcepts.joinToString(","),
                weakConceptTitlesJson = encodeTitles(skill.conceptTitles.filterKeys { it in skill.weakConcepts }),
                masteredConceptsCsv = skill.masteredConcepts.joinToString(","),
                tierLabel = skill.tierLabel,
                totalAttempts = skill.totalAttempts,
                totalCorrect = skill.totalCorrect,
                avgResponseTimeMs = skill.avgResponseTimeMs,
                dirty = true,
            ),
        )
    }

    suspend fun recentIds(childId: String): Set<String> = withContext(dispatchers.io) {
        quizDao.recentQuestionIds(childId, AppConfig.QUIZ_REPEAT_WINDOW).toSet()
    }

    suspend fun markAsked(childId: String, questionId: String) = withContext(dispatchers.io) {
        quizDao.insertRecent(
            RecentQuestionEntity(
                childId = childId,
                questionId = questionId,
                answeredAtEpochMs = System.currentTimeMillis(),
            ),
        )
        quizDao.deleteRecentBefore(
            childId,
            System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000,
        )
    }

    /** Skill rows touched since the last successful upload (Phase 7 sync). */
    suspend fun dirtySkillsForUpload(childId: String): List<SkillStateUpload> = withContext(dispatchers.io) {
        quizDao.listDirtySkills(childId).map { entity ->
            val weakIds = entity.weakConceptsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }
            val masteredIds = entity.masteredConceptsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }
            SkillStateUpload(
                topic = entity.topic,
                level = entity.level,
                streakCorrect = entity.streakCorrect,
                weak = entity.weak || weakIds.isNotEmpty(),
                weakConcepts = weakIds,
                weakConceptTitles = decodeTitles(entity.weakConceptTitlesJson).filterKeys { it in weakIds.toSet() },
                masteredConcepts = masteredIds,
                tierLabel = entity.tierLabel,
                totalAttempts = entity.totalAttempts,
                totalCorrect = entity.totalCorrect,
                avgResponseTimeMs = entity.avgResponseTimeMs,
            )
        }
    }

    suspend fun markSkillsSynced(childId: String) = withContext(dispatchers.io) {
        quizDao.markSkillsSynced(childId, System.currentTimeMillis())
    }

    private fun encodeTitles(titles: Map<String, String>): String =
        json.encodeToString(kotlinx.serialization.serializer<Map<String, String>>(), titles)

    private fun decodeTitles(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank() || raw == "{}") return emptyMap()
        return runCatching {
            json.decodeFromString(kotlinx.serialization.serializer<Map<String, String>>(), raw)
        }.getOrDefault(emptyMap())
    }
}

/** Append-only local record of completed quiz sessions, uploaded once by the sync worker. */
@Singleton
class QuizAttemptRepository @Inject constructor(
    private val dao: QuizAttemptDao,
    private val dispatchers: AppDispatchers,
) {
    suspend fun record(
        childId: String,
        topics: List<String>,
        score: Int,
        total: Int,
        passed: Boolean,
        extraMinutesGranted: Int,
    ): String = withContext(dispatchers.io) {
        val attemptId = UUID.randomUUID().toString()
        dao.insert(
            QuizAttemptEntity(
                attemptId = attemptId,
                childId = childId,
                createdAtEpochMs = System.currentTimeMillis(),
                topicsCsv = topics.distinct().joinToString(","),
                score = score,
                total = total,
                passed = passed,
                extraMinutesGranted = extraMinutesGranted,
            ),
        )
        attemptId
    }

    suspend fun dirtyForUpload(childId: String): List<QuizAttemptUpload> = withContext(dispatchers.io) {
        dao.listDirty(childId).map {
            QuizAttemptUpload(
                attemptId = it.attemptId,
                createdAtEpochMs = it.createdAtEpochMs,
                topics = it.topicsCsv.split(',').filter { topic -> topic.isNotBlank() },
                score = it.score,
                total = it.total,
                passed = it.passed,
                extraMinutesGranted = it.extraMinutesGranted,
            )
        }
    }

    suspend fun markSynced(attemptId: String) = withContext(dispatchers.io) {
        dao.markSynced(attemptId, System.currentTimeMillis())
    }
}

private fun QuizItemEntity.toDomain(json: Json): QuizQuestion? = runCatching {
    val age = AgeBand.entries.firstOrNull { it.name == ageBand } ?: AgeBand.AGE_7_TO_9
    val choices = json.decodeFromString(ListSerializer(QuizChoice.serializer()), choicesJson)
    val whyWrong = json.decodeFromString(
        kotlinx.serialization.serializer<Map<String, String>>(),
        whyWrongByChoiceJson,
    )
    val lesson = miniLessonJson?.let {
        runCatching {
            json.decodeFromString(com.meritscreen.feature.child.domain.MiniLesson.serializer(), it)
        }.getOrNull()
    }
    QuizQuestion(
        id = id,
        ageBand = age,
        topic = topic,
        conceptId = conceptId,
        conceptTitle = conceptTitle,
        difficulty = difficulty,
        prompt = prompt,
        choices = choices,
        whyCorrect = whyCorrect,
        whyWrongByChoice = whyWrong,
        conceptExplainer = conceptExplainer,
        language = language,
        interactionType = interactionType,
        promptTag = promptTag,
        promptCount = promptCount,
        promptAudioKey = promptAudioKey,
        miniLesson = lesson,
        source = source,
    )
}.getOrNull()
