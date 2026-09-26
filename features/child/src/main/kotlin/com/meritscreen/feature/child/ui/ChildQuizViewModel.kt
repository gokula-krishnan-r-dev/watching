package com.meritscreen.feature.child.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.analytics.AnalyticsEvent
import com.meritscreen.core.analytics.AnalyticsTracker
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AppRule
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.feature.child.data.ChildPolicyRepository
import com.meritscreen.feature.child.data.QuizAttemptRepository
import com.meritscreen.feature.child.data.QuizPackGenerationScheduler
import com.meritscreen.feature.child.data.QuizRepository
import com.meritscreen.feature.child.data.StickerRepository
import com.meritscreen.feature.child.data.UsageSyncScheduler
import com.meritscreen.feature.child.domain.AdaptiveQuizEngine
import com.meritscreen.feature.child.domain.ChildSessionController
import com.meritscreen.feature.child.domain.MiniLesson
import com.meritscreen.feature.child.domain.QuizAnswerFeedback
import com.meritscreen.feature.child.domain.QuizQuestion
import com.meritscreen.feature.child.domain.QuizSessionResult
import com.meritscreen.feature.child.domain.SessionEngine
import com.meritscreen.feature.child.domain.TopicSkill
import com.meritscreen.core.common.domain.NurseryTeachItem
import com.meritscreen.core.common.domain.NurseryVideo
import com.meritscreen.core.network.NetworkMonitor
import com.meritscreen.core.common.domain.LearningResource
import com.meritscreen.core.common.domain.LearningResourceVideo
import com.meritscreen.feature.child.data.LearningResourceRepository
import com.meritscreen.feature.child.data.NurseryCurriculumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface QuizUiStep {
    data object Intro : QuizUiStep
    data class NurseryVideo(
        val video: com.meritscreen.core.common.domain.NurseryVideo,
        val childName: String,
    ) : QuizUiStep
    data class NurseryTeach(
        val items: List<NurseryTeachItem>,
        val childName: String,
    ) : QuizUiStep
    data class Completed(
        val passed: Boolean,
        val unlockedMinutes: Int,
        val result: QuizSessionResult? = null,
        val stickerTitle: String? = null,
        val stickerEmoji: String? = null,
        val stickerStageLabel: String? = null,
        val explorerLevel: Int = 1,
        val xpGained: Int = 0,
    ) : QuizUiStep
    data class Question(
        val index: Int,
        val total: Int,
        val question: QuizQuestion,
        val secondsRemaining: Int = AppConfig.QUIZ_QUESTION_TIME_LIMIT_SECONDS,
        val evaluatedChoiceId: String? = null,
        val isCorrect: Boolean? = null,
        val whyWrongText: String? = null,
        val correctChoiceText: String? = null,
        val resource: LearningResource? = null,
        val showResourceModal: Boolean = false,
        val activeFullscreenVideo: LearningResourceVideo? = null,
    ) : QuizUiStep
    data class Feedback(
        val index: Int,
        val total: Int,
        val feedback: QuizAnswerFeedback,
        val question: QuizQuestion,
    ) : QuizUiStep
    data class LockedExplanation(
        val index: Int,
        val total: Int,
        val feedback: QuizAnswerFeedback,
        val correctChoiceText: String,
        val miniLesson: MiniLesson,
        val lockSecondsRemaining: Int,
        val visual: Boolean,
        val question: QuizQuestion,
    ) : QuizUiStep
    data class Result(
        val result: QuizSessionResult,
        val unlockedMinutes: Int,
        val appLabel: String,
        /** Live seconds left on the device-wide fail lock. */
        val cooldownSecondsRemaining: Int = 0,
        /** Total cooldown length in seconds (for progress). */
        val cooldownTotalSeconds: Int = 0,
        /** Wall-clock end time for “finishes at …” label. */
        val cooldownEndsAtEpochMs: Long = 0L,
    ) : QuizUiStep
}

data class QuizUi(
    val step: QuizUiStep,
    val appLabel: String,
    val childName: String = "Leo",
    val ageBand: AgeBand = AgeBand.AGE_7_TO_9,
)

@HiltViewModel
class ChildQuizViewModel @Inject constructor(
    private val pairingStore: ChildPairingStore,
    private val policyRepository: ChildPolicyRepository,
    private val quizRepository: QuizRepository,
    private val quizAttemptRepository: QuizAttemptRepository,
    private val stickerRepository: StickerRepository,
    private val usageSyncScheduler: UsageSyncScheduler,
    private val quizPackGenerationScheduler: QuizPackGenerationScheduler,
    private val sessionController: ChildSessionController,
    private val analyticsTracker: AnalyticsTracker,
    private val nurseryCurriculumRepository: NurseryCurriculumRepository,
    private val networkMonitor: NetworkMonitor,
    private val learningResourceRepository: LearningResourceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<QuizUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<QuizUi>> = _uiState.asStateFlow()

    private var bank: List<QuizQuestion> = emptyList()
    private var skills = mutableMapOf<String, TopicSkill>()
    private var recent = emptySet<String>()
    private var used = mutableSetOf<String>()
    private var questions = mutableListOf<QuizQuestion>()
    private var correctCount = 0
    private var index = 0
    private var total = 3
    private var passPercent = 70
    private var lastWrongConcept: String? = null
    private var preferredLevel = 2
    private var activeRule: AppRule? = null
    private var appLabel = "YouTube Kids"
    private var childName = "Leo"
    private var childId: String = ""
    private var ageBand = AgeBand.AGE_7_TO_9
    private var showExplanations = true
    private var rewardsEnabled = true
    private var extraMinutesOnPass = 30
    private var currentPolicy: ChildPolicy? = null
    private var completionStarted = false

    private var questionStartTimeMs: Long = 0L
    private var lockoutCountdownJob: Job? = null
    private var questionTimerJob: Job? = null
    private var cooldownTickerJob: Job? = null

    init {
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        val credential = pairingStore.get()
        if (credential == null) {
            _uiState.value = UiState.Error(AppError.Auth())
            return
        }
        childId = credential.childId
        val policy = policyRepository.getPolicy(childId)
        currentPolicy = policy
        val profile = policyRepository.getProfile(childId)
        childName = profile?.displayName?.ifBlank { null } ?: "Leo"
        ageBand = AgeBand.entries.firstOrNull { it.name == profile?.ageBand } ?: AgeBand.AGE_7_TO_9
        total = AdaptiveQuizEngine.questionsPerQuiz(policy.questionsPerQuiz)
        passPercent = policy.passScorePercent
        showExplanations = policy.showExplanations
        rewardsEnabled = policy.rewardsEnabled
        extraMinutesOnPass = if (policy.extraMinutesOnPass > 0) policy.extraMinutesOnPass else 30
        val session = sessionController.snapshot.value
        activeRule = policyRepository.listAppRules(childId)
            .firstOrNull { it.appId == session.activeAppId || it.packageOrBundleId == session.activePackage }
        appLabel = activeRule?.displayName?.ifBlank { null }
            ?: session.activePackage?.substringAfterLast('.')
            ?: "YouTube Kids"

        // For Early Learners (Ages 3-6: Nursery, LKG, UKG) - Teach First (docs/09)
        if (ageBand == AgeBand.AGE_3_TO_6 && !policy.nurseryCheckEnabled) {
            startNurseryTeaching(policy)
            return
        }

        // Direct-to-Questions: remove "Start Quick Quiz" intro page as requested
        bank = quizRepository.questionsFor(ageBand, profile?.language ?: "en")
        if (bank.isEmpty()) {
            if (ageBand == AgeBand.AGE_3_TO_6) {
                startNurseryTeaching(policy)
                return
            }
            _uiState.value = UiState.Error(AppError.NotFound("Quiz questions aren’t ready yet. Try again offline later."))
            return
        }
        skills = quizRepository.skills(childId).toMutableMap()
        recent = quizRepository.recentIds(childId)
        preferredLevel = AdaptiveQuizEngine.sessionStartLevel(skills)
        beginQuestions()
    }

    private fun startNurseryTeaching(policy: ChildPolicy) {
        val isOnline = networkMonitor.isCurrentlyOnline()
        if (policy.nurseryVideoEnabled && isOnline) {
            val playlistId = policy.nurseryPlaylistId.ifBlank { "all" }
            val video = nurseryCurriculumRepository.pickDiverseVideo(preferredPlaylistId = playlistId)
                ?: nurseryCurriculumRepository.pickNextVideo(playlistId)
            if (video != null) {
                _uiState.value = UiState.Success(
                    QuizUi(
                        step = QuizUiStep.NurseryVideo(video, childName),
                        appLabel = appLabel,
                        childName = childName,
                        ageBand = ageBand,
                    ),
                )
                return
            }
        }
        fallbackToNurseryCards()
    }

    fun onNurseryVideoError(failedVideoId: String) {
        val policy = currentPolicy ?: ChildPolicy()
        val playlistId = policy.nurseryPlaylistId.ifBlank { "all" }
        val nextVideo = nurseryCurriculumRepository.pickDiverseVideo(
            preferredPlaylistId = playlistId,
            currentVideoId = failedVideoId,
            excludeVideoIds = setOf(failedVideoId),
        ) ?: nurseryCurriculumRepository.pickNextVideo(playlistId, currentVideoId = failedVideoId)

        if (nextVideo != null && nextVideo.videoId != failedVideoId) {
            _uiState.value = UiState.Success(
                QuizUi(
                    step = QuizUiStep.NurseryVideo(nextVideo, childName),
                    appLabel = appLabel,
                    childName = childName,
                    ageBand = ageBand,
                ),
            )
        } else {
            fallbackToNurseryCards()
        }
    }

    fun fallbackToNurseryCards() {
        val items = nurseryCurriculumRepository.pickDiverseTeachItems(count = 3)
            .ifEmpty { nurseryCurriculumRepository.pickNextTeachItems("alphabet", count = 3) }
        _uiState.value = UiState.Success(
            QuizUi(
                step = QuizUiStep.NurseryTeach(items, childName),
                appLabel = appLabel,
                childName = childName,
                ageBand = ageBand,
            ),
        )
    }

    fun onNurseryComplete() {
        viewModelScope.launch {
            val policy = currentPolicy
            val snapshot = sessionController.onQuizPassed(activeRule)
            val unlockedMinutes = snapshot.blockDurationMinutes
            val award = if (policy != null && childId.isNotBlank()) {
                stickerRepository.awardQuizPass(
                    childId = childId,
                    policy = policy,
                    attemptId = null,
                )
            } else {
                null
            }
            usageSyncScheduler.runSoon()
            analyticsTracker.track(AnalyticsEvent.QuizCompleted)
            _uiState.value = UiState.Success(
                QuizUi(
                    step = QuizUiStep.Completed(
                        passed = true,
                        unlockedMinutes = unlockedMinutes,
                        result = QuizSessionResult(
                            passed = true,
                            correctCount = 0,
                            total = 0,
                            percent = 100,
                        ),
                        stickerTitle = award?.unlocked?.title,
                        stickerEmoji = award?.unlocked?.emoji,
                        stickerStageLabel = award?.unlocked?.stage?.displayLabel,
                        explorerLevel = award?.progress?.explorerLevel ?: 1,
                        xpGained = award?.xpGained ?: 0,
                    ),
                    appLabel = appLabel,
                    childName = childName,
                    ageBand = ageBand,
                ),
            )
        }
    }

    fun beginQuestions() {
        viewModelScope.launch {
            if (questions.isNotEmpty() && _uiState.value is UiState.Success &&
                (_uiState.value as UiState.Success).data.step is QuizUiStep.Question
            ) return@launch
            completionStarted = false
            questions.clear()
            used.clear()
            correctCount = 0
            index = 0
            lastWrongConcept = null
            repeat(total) {
                val q = AdaptiveQuizEngine.pickNext(
                    bank = bank,
                    skills = skills,
                    recentIds = recent,
                    usedInSession = used,
                    lastWrongConceptId = lastWrongConcept,
                    preferredLevel = preferredLevel,
                ) ?: return@repeat
                questions += q
                used += q.id
            }
            if (questions.isEmpty()) {
                _uiState.value = UiState.Error(AppError.NotFound("No quiz questions available."))
                return@launch
            }
            total = questions.size
            emitQuestion()
        }
    }

    fun answer(choiceId: String) {
        questionTimerJob?.cancel()
        val visible = _uiState.value as? UiState.Success ?: return
        val visibleQuestion = visible.data.step as? QuizUiStep.Question ?: return
        if (visibleQuestion.evaluatedChoiceId != null || completionStarted) return
        val current = questions.getOrNull(index) ?: return
        val responseTimeMs = (SystemClock.elapsedRealtime() - questionStartTimeMs).coerceAtLeast(0)
        val skill = skills[current.topic] ?: TopicSkill(current.topic)
        val (feedback, updated) = AdaptiveQuizEngine.gradeAnswer(current, choiceId, skill, responseTimeMs)
        skills[current.topic] = updated
        preferredLevel = updated.level

        if (feedback.correct) {
            correctCount += 1
            lastWrongConcept = null
        } else {
            lastWrongConcept = current.conceptId
        }

        viewModelScope.launch {
            quizRepository.saveTopicSkill(childId, updated)
            quizRepository.markAsked(childId, current.id)
        }

        val correctChoice = current.choices.firstOrNull { it.correct }
        val correctText = correctChoice?.text?.ifBlank { null }
            ?: correctChoice?.imageTag?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
            ?: "Correct Answer"

        if (feedback.correct) {
            _uiState.value = UiState.Success(
                QuizUi(
                    step = QuizUiStep.Question(
                        index = index + 1,
                        total = total,
                        question = current,
                        secondsRemaining = 0,
                        evaluatedChoiceId = choiceId,
                        isCorrect = true,
                        whyWrongText = null,
                        correctChoiceText = correctText,
                    ),
                    appLabel = appLabel,
                    childName = childName,
                    ageBand = ageBand,
                ),
            )
        } else {
            val whyWrong = current.whyWrongByChoice[choiceId]
                ?: "That choice isn't quite right. Let's look closer at this idea!"
            val initialResource = learningResourceRepository.getStaticBaseline(
                conceptId = current.conceptId,
                topic = current.topic,
                conceptTitle = current.conceptTitle,
            )

            _uiState.value = UiState.Success(
                QuizUi(
                    step = QuizUiStep.Question(
                        index = index + 1,
                        total = total,
                        question = current,
                        secondsRemaining = 0,
                        evaluatedChoiceId = choiceId,
                        isCorrect = false,
                        whyWrongText = whyWrong,
                        correctChoiceText = correctText,
                        resource = initialResource,
                    ),
                    appLabel = appLabel,
                    childName = childName,
                    ageBand = ageBand,
                ),
            )

            // Asynchronously fetch Firebase AI enrichment without blocking
            val wrongChoice = current.choices.firstOrNull { it.id == choiceId }
            val wrongText = wrongChoice?.text?.ifBlank { null } ?: choiceId
            viewModelScope.launch {
                learningResourceRepository.getResourceStream(
                    prompt = current.prompt,
                    wrongAnswerText = wrongText,
                    correctAnswerText = correctText,
                    topic = current.topic,
                    conceptId = current.conceptId,
                    conceptTitle = current.conceptTitle,
                    ageBand = ageBand,
                ).collect { enriched ->
                    val cur = _uiState.value
                    if (cur is UiState.Success && cur.data.step is QuizUiStep.Question) {
                        val currentStep = cur.data.step as QuizUiStep.Question
                        if (currentStep.question.id == current.id && currentStep.isCorrect == false) {
                            _uiState.value = UiState.Success(
                                cur.data.copy(step = currentStep.copy(resource = enriched)),
                            )
                        }
                    }
                }
            }
        }
    }

    fun proceedToNextQuestion() {
        advanceAfterFeedback()
    }

    fun openResourceModal() {
        val cur = _uiState.value
        if (cur is UiState.Success && cur.data.step is QuizUiStep.Question) {
            val step = cur.data.step as QuizUiStep.Question
            _uiState.value = UiState.Success(
                cur.data.copy(step = step.copy(showResourceModal = true)),
            )
        }
    }

    fun closeResourceModal() {
        val cur = _uiState.value
        if (cur is UiState.Success && cur.data.step is QuizUiStep.Question) {
            val step = cur.data.step as QuizUiStep.Question
            _uiState.value = UiState.Success(
                cur.data.copy(step = step.copy(showResourceModal = false)),
            )
        }
    }

    fun playResourceVideo(video: LearningResourceVideo) {
        val cur = _uiState.value
        if (cur is UiState.Success && cur.data.step is QuizUiStep.Question) {
            val step = cur.data.step as QuizUiStep.Question
            _uiState.value = UiState.Success(
                cur.data.copy(step = step.copy(activeFullscreenVideo = video)),
            )
        }
    }

    fun closeResourceVideo() {
        val cur = _uiState.value
        if (cur is UiState.Success && cur.data.step is QuizUiStep.Question) {
            val step = cur.data.step as QuizUiStep.Question
            _uiState.value = UiState.Success(
                cur.data.copy(step = step.copy(activeFullscreenVideo = null)),
            )
        }
    }

    fun onQuestionTimeout() {
        questionTimerJob?.cancel()
        val visible = _uiState.value as? UiState.Success ?: return
        val visibleQuestion = visible.data.step as? QuizUiStep.Question ?: return
        if (visibleQuestion.evaluatedChoiceId != null || completionStarted) return
        val current = questions.getOrNull(index) ?: return
        val skill = skills[current.topic] ?: TopicSkill(current.topic)
        lastWrongConcept = current.conceptId

        val feedback = QuizAnswerFeedback(
            correct = false,
            resultLine = "Time’s up — let’s look together",
            whyLine = "Thinking carefully is great! Let’s review this question together.",
            conceptLine = current.conceptExplainer,
            nextLevel = (skill.level - 1).coerceAtLeast(1),
        )
        val updated = skill.copy(
            level = feedback.nextLevel,
            streakCorrect = 0,
            weakConcepts = skill.weakConcepts + current.conceptId,
            totalAttempts = skill.totalAttempts + 1,
        )
        skills[current.topic] = updated
        preferredLevel = updated.level

        viewModelScope.launch {
            quizRepository.saveTopicSkill(childId, updated)
            quizRepository.markAsked(childId, current.id)
        }

        val correctChoice = current.choices.firstOrNull { it.correct }
        val correctText = correctChoice?.text?.ifBlank { null }
            ?: correctChoice?.imageTag?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
            ?: "Correct Answer"
        val initialResource = learningResourceRepository.getStaticBaseline(
            conceptId = current.conceptId,
            topic = current.topic,
            conceptTitle = current.conceptTitle,
        )

        _uiState.value = UiState.Success(
            QuizUi(
                step = QuizUiStep.Question(
                    index = index + 1,
                    total = total,
                    question = current,
                    secondsRemaining = 0,
                    evaluatedChoiceId = "",
                    isCorrect = false,
                    whyWrongText = "Time was up! Take a moment to review this idea without any rush.",
                    correctChoiceText = correctText,
                    resource = initialResource,
                ),
                appLabel = appLabel,
                childName = childName,
                ageBand = ageBand,
            ),
        )
    }

    private fun startLockoutTeaching(current: QuizQuestion, feedback: QuizAnswerFeedback) {
        val correctChoice = current.choices.firstOrNull { it.correct }
        val correctText = correctChoice?.text?.ifBlank { null }
            ?: correctChoice?.imageTag?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
            ?: "Correct Answer"

        val lesson = current.miniLesson ?: MiniLesson(
            title = current.conceptTitle,
            bodyLines = listOf(current.conceptExplainer),
        )
        val isVisual = current.interactionType != "TAP_TEXT"

        lockoutCountdownJob?.cancel()
        lockoutCountdownJob = viewModelScope.launch {
            var remaining = AppConfig.QUIZ_LOCKOUT_SECONDS
            while (remaining >= 0) {
                _uiState.value = UiState.Success(
                    QuizUi(
                        step = QuizUiStep.LockedExplanation(
                            index = index + 1,
                            total = total,
                            feedback = feedback,
                            correctChoiceText = correctText,
                            miniLesson = lesson,
                            lockSecondsRemaining = remaining,
                            visual = isVisual,
                            question = current,
                        ),
                        appLabel = appLabel,
                        childName = childName,
                        ageBand = ageBand,
                    ),
                )
                if (remaining > 0) {
                    delay(1000L)
                }
                remaining--
            }
        }
    }

    fun continueAfterFeedback() {
        lockoutCountdownJob?.cancel()
        advanceAfterFeedback()
    }

    private fun advanceAfterFeedback() {
        if (completionStarted) return
        val current = _uiState.value as? UiState.Success ?: return
        val step = current.data.step
        val canAdvance = when (step) {
            is QuizUiStep.Question -> step.evaluatedChoiceId != null
            is QuizUiStep.Feedback, is QuizUiStep.LockedExplanation -> true
            else -> false
        }
        if (!canAdvance) return
        index += 1
        if (index >= total) {
            finish()
        } else {
            emitQuestion()
        }
    }

    private fun emitQuestion() {
        val q = questions[index]
        questionStartTimeMs = SystemClock.elapsedRealtime()
        _uiState.value = UiState.Success(
            QuizUi(QuizUiStep.Question(index + 1, total, q), appLabel, childName, ageBand),
        )

        // Question countdown timer
        questionTimerJob?.cancel()
        questionTimerJob = viewModelScope.launch {
            var left = if (ageBand == AgeBand.AGE_3_TO_6) 60 else AppConfig.QUIZ_QUESTION_TIME_LIMIT_SECONDS
            while (left > 0) {
                delay(1000L)
                left--
                val current = _uiState.value
                if (current is UiState.Success && current.data.step is QuizUiStep.Question) {
                    val qStep = current.data.step as QuizUiStep.Question
                    if (qStep.evaluatedChoiceId != null) break
                    _uiState.value = UiState.Success(
                        current.data.copy(
                            step = qStep.copy(secondsRemaining = left),
                        ),
                    )
                } else {
                    break
                }
            }
            val current = _uiState.value
            if (left == 0 && current is UiState.Success && current.data.step is QuizUiStep.Question) {
                val qStep = current.data.step as QuizUiStep.Question
                if (qStep.evaluatedChoiceId == null) {
                    onQuestionTimeout()
                }
            }
        }
    }

    private fun finish() {
        if (completionStarted) return
        completionStarted = true
        questionTimerJob?.cancel()
        lockoutCountdownJob?.cancel()
        viewModelScope.launch {
            val result = AdaptiveQuizEngine.finalize(correctCount, total, passPercent)
            val policy = currentPolicy
            var extraMinutesGranted = if (result.passed && rewardsEnabled) extraMinutesOnPass else 0
            if (result.passed && rewardsEnabled && policy?.weekendBonusEnabled == true &&
                SessionEngine.isWeekendLocal()
            ) {
                extraMinutesGranted += AppConfig.WEEKEND_BONUS_EXTRA_MINUTES
            }
            val attemptId = quizAttemptRepository.record(
                childId = childId,
                topics = questions.map { it.topic },
                score = correctCount,
                total = total,
                passed = result.passed,
                extraMinutesGranted = extraMinutesGranted,
            )
            if (result.passed) {
                val snapshot = sessionController.onQuizPassed(activeRule)
                val unlockedMinutes = snapshot.blockDurationMinutes
                val award = if (policy != null && childId.isNotBlank()) {
                    stickerRepository.awardQuizPass(
                        childId = childId,
                        policy = policy,
                        attemptId = attemptId,
                    )
                } else {
                    null
                }
                usageSyncScheduler.runSoon()
                quizPackGenerationScheduler.runSoon()
                analyticsTracker.track(AnalyticsEvent.QuizCompleted)
                _uiState.value = UiState.Success(
                    QuizUi(
                        step = QuizUiStep.Completed(
                            passed = true,
                            unlockedMinutes = unlockedMinutes,
                            result = result,
                            stickerTitle = award?.unlocked?.title,
                            stickerEmoji = award?.unlocked?.emoji,
                            stickerStageLabel = award?.unlocked?.stage?.displayLabel,
                            explorerLevel = award?.progress?.explorerLevel ?: 1,
                            xpGained = award?.xpGained ?: 0,
                        ),
                        appLabel = appLabel,
                        childName = childName,
                        ageBand = ageBand,
                    ),
                )
            } else {
                val snapshot = sessionController.onQuizFailed(activeRule)
                usageSyncScheduler.runSoon()
                quizPackGenerationScheduler.runSoon()
                analyticsTracker.track(AnalyticsEvent.QuizCompleted)
                val nowElapsed = SystemClock.elapsedRealtime()
                val remaining = snapshot.remainingCooldownSeconds(nowElapsed)
                val totalSeconds = (snapshot.cooldownMinutes.coerceIn(1, 180) * 60)
                    .coerceAtLeast(remaining)
                val endsAt = System.currentTimeMillis() + remaining * 1_000L
                _uiState.value = UiState.Success(
                    QuizUi(
                        step = QuizUiStep.Result(
                            result = result,
                            unlockedMinutes = 0,
                            appLabel = appLabel,
                            cooldownSecondsRemaining = remaining,
                            cooldownTotalSeconds = totalSeconds,
                            cooldownEndsAtEpochMs = endsAt,
                        ),
                        appLabel = appLabel,
                        childName = childName,
                        ageBand = ageBand,
                    ),
                )
                startCooldownTicker()
            }
        }
    }

    /**
     * Keeps the fail-lock countdown in sync with [ChildSessionController] every second
     * so the Calm Cooldown pane never shows a frozen placeholder timer.
     */
    private fun startCooldownTicker() {
        cooldownTickerJob?.cancel()
        cooldownTickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                sessionController.tick()
                val remaining = sessionController.snapshot.value
                    .remainingCooldownSeconds(SystemClock.elapsedRealtime())
                val current = _uiState.value
                if (current !is UiState.Success || current.data.step !is QuizUiStep.Result) break
                val step = current.data.step as QuizUiStep.Result
                if (step.result.passed) break
                val endsAt = if (remaining > 0) {
                    System.currentTimeMillis() + remaining * 1_000L
                } else {
                    step.cooldownEndsAtEpochMs
                }
                _uiState.value = UiState.Success(
                    current.data.copy(
                        step = step.copy(
                            cooldownSecondsRemaining = remaining,
                            cooldownEndsAtEpochMs = endsAt,
                        ),
                    ),
                )
                if (remaining <= 0) break
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        questionTimerJob?.cancel()
        lockoutCountdownJob?.cancel()
        cooldownTickerJob?.cancel()
    }
}
