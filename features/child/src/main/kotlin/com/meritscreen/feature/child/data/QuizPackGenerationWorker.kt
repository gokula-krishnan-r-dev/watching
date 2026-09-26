package com.meritscreen.feature.child.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.CustomPromptSanitizer
import com.meritscreen.core.common.logging.AppLogger
import com.meritscreen.core.database.child.QuizDao
import com.meritscreen.core.database.child.QuizItemEntity
import com.meritscreen.core.firebase.ai.QuizAiPackClient
import com.meritscreen.core.network.NetworkMonitor
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.feature.child.domain.AdaptiveQuizEngine
import com.meritscreen.feature.child.domain.MiniLesson
import com.meritscreen.feature.child.domain.QuizChoice
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Background Gemini pack generation via Firebase AI Logic (docs/06 & 08).
 * Never blocks Home or quiz UI — failures leave the builtin bank intact.
 */
@HiltWorker
class QuizPackGenerationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pairingStore: ChildPairingStore,
    private val policyRepository: ChildPolicyRepository,
    private val quizRepository: QuizRepository,
    private val quizDao: QuizDao,
    private val aiPackClient: QuizAiPackClient,
    private val networkMonitor: NetworkMonitor,
    private val appLogger: AppLogger,
) : CoroutineWorker(context, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val credential = pairingStore.get() ?: return Result.success()
        val childId = credential.childId
        val policy = runCatching { policyRepository.getPolicy(childId) }.getOrNull()
            ?: return Result.success()

        if (!policy.aiQuizzesEnabled) {
            return Result.success()
        }
        if (!networkMonitor.isCurrentlyOnline()) {
            return Result.retry()
        }

        // Touch sanitizer so inject-style guidelines never reach the model.
        CustomPromptSanitizer.sanitize(policy.customPromptGuidelines)

        val profile = policyRepository.getProfile(childId)
        val ageBand = AgeBand.entries.firstOrNull { it.name == profile?.ageBand }
            ?: AgeBand.AGE_7_TO_9

        // Early learners (Ages 3-6) use curated static & Firebase DB curriculum (docs/09)
        if (ageBand == AgeBand.AGE_3_TO_6) {
            appLogger.d("Early learner (AGE_3_TO_6) uses static & Firebase DB curriculum; skipping AI generation")
            return Result.success()
        }

        val language = profile?.language?.takeIf { it.isNotBlank() } ?: "en"

        val skills = quizRepository.skills(childId)
        val preferredLevel = AdaptiveQuizEngine.sessionStartLevel(skills)
        val recentIds = quizRepository.recentIds(childId)
        val bank = quizRepository.questionsFor(ageBand, language)
        val nearRungUnused = bank.count { q ->
            q.id !in recentIds &&
                q.difficulty in (preferredLevel - 1)..(preferredLevel + 1)
        }
        val aiCount = quizDao.countAiForAgeBand(ageBand.name, language)
        if (!QuizPackValidator.shouldGenerate(nearRungUnused, aiCount)) {
            appLogger.d(
                "Quiz AI pack skip; bank still healthy",
                "nearRungUnused" to nearRungUnused,
                "aiCount" to aiCount,
            )
            return Result.success()
        }

        val avoidIds = (quizDao.idsForAgeBand(ageBand.name) + recentIds).toSet()
        val userPrompt = QuizPackPromptBuilder.buildUserPrompt(
            ageBand = ageBand,
            language = language,
            policy = policy,
            skills = skills,
            avoidQuestionIds = avoidIds,
        )

        val raw = try {
            aiPackClient.generatePackJson(
                systemInstruction = QuizPackPromptBuilder.SYSTEM_INSTRUCTION,
                userPrompt = userPrompt,
            )
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            appLogger.w(
                "Quiz AI pack generation failed; keeping builtin bank",
                error,
                "transient" to isTransientFailure(error),
            )
            // App Check / quota / network flaps are recoverable — never mark success so
            // WorkManager drops the expedited chain while the child is still on builtin.
            return if (isTransientFailure(error)) Result.retry() else Result.failure()
        } ?: run {
            appLogger.w("Quiz AI pack returned empty content")
            return Result.retry()
        }

        val validated = QuizPackValidator.parseAndValidate(
            rawJson = raw,
            expectedAgeBand = ageBand,
            avoidIds = avoidIds,
        )
        if (validated.isEmpty()) {
            appLogger.w(
                message = "Quiz AI pack produced zero valid items",
                throwable = null,
                "rawChars" to raw.length,
                "ageBand" to ageBand.name,
            )
            // Model responded but failed local validation — retry with a fresh sample.
            return Result.retry()
        }

        val packId = "pack_${System.currentTimeMillis()}"
        val entities = validated.map { item ->
            QuizItemEntity(
                id = item.id,
                ageBand = item.ageBand.name,
                topic = item.topic,
                conceptId = item.conceptId,
                conceptTitle = item.conceptTitle,
                difficulty = item.difficulty,
                prompt = item.prompt,
                choicesJson = json.encodeToString(ListSerializer(QuizChoice.serializer()), item.choices),
                whyCorrect = item.whyCorrect,
                whyWrongByChoiceJson = json.encodeToString(
                    kotlinx.serialization.serializer<Map<String, String>>(),
                    item.whyWrongByChoice,
                ),
                conceptExplainer = item.conceptExplainer,
                language = item.language,
                source = "ai",
                interactionType = item.interactionType,
                promptTag = item.promptTag,
                promptCount = item.promptCount,
                promptAudioKey = item.promptAudioKey,
                miniLessonJson = item.miniLesson?.let {
                    json.encodeToString(MiniLesson.serializer(), it)
                },
                packId = packId,
            )
        }
        quizDao.upsertItems(entities)
        appLogger.i(
            "Upserted AI quiz items",
            "count" to entities.size,
            "ageBand" to ageBand.name,
            "packId" to packId,
        )
        return Result.success()
    }

    private fun isTransientFailure(error: Throwable): Boolean {
        if (error is IOException) return true
        val chain = generateSequence(error) { it.cause }.toList()
        val message = chain.mapNotNull { it.message }.joinToString(" ").lowercase()
        val classNames = chain.map { it.javaClass.name }.joinToString(" ").lowercase()
        // Billing / prepaid credit exhaustion is not recovered by retrying the same call.
        if ("prepayment credits are depleted" in message ||
            "credits are depleted" in message ||
            ("billing" in message && "ai studio" in message)
        ) {
            return false
        }
        return "unavailable" in message ||
            "timeout" in message ||
            "deadline" in message ||
            "resource exhausted" in message ||
            "429" in message ||
            "500" in message ||
            "503" in message ||
            "403" in message ||
            "app attestation" in message ||
            "attestation failed" in message ||
            "appcheck" in message ||
            "app check" in message ||
            "firebaseexception" in classNames ||
            "quota" in message ||
            "too many attempts" in message ||
            "placeholder token" in message
    }
}
