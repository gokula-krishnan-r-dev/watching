package com.meritscreen.feature.child.ui

import com.meritscreen.core.analytics.AnalyticsTracker
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.LearningResourceVideo
import com.meritscreen.core.common.domain.NurseryTeachItem
import com.meritscreen.core.common.domain.NurseryVideo
import com.meritscreen.core.common.domain.StaticLearningResourceCatalog
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.network.NetworkMonitor
import com.meritscreen.core.security.pairing.ChildPairingCredential
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.feature.child.data.ChildLocalProfile
import com.meritscreen.feature.child.data.ChildPolicyRepository
import com.meritscreen.feature.child.data.LearningResourceRepository
import com.meritscreen.feature.child.data.NurseryCurriculumRepository
import com.meritscreen.feature.child.data.QuizAttemptRepository
import com.meritscreen.feature.child.data.QuizPackGenerationScheduler
import com.meritscreen.feature.child.data.QuizRepository
import com.meritscreen.feature.child.data.StickerRepository
import com.meritscreen.feature.child.data.UsageSyncScheduler
import com.meritscreen.feature.child.domain.ChildSessionController
import com.meritscreen.feature.child.domain.MiniLesson
import com.meritscreen.feature.child.domain.QuizChoice
import com.meritscreen.feature.child.domain.QuizQuestion
import com.meritscreen.feature.child.domain.SessionSnapshot
import com.meritscreen.core.common.domain.SessionPhase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChildQuizViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val pairingStore: ChildPairingStore = mockk(relaxed = true)
    private val policyRepository: ChildPolicyRepository = mockk(relaxed = true)
    private val quizRepository: QuizRepository = mockk(relaxed = true)
    private val quizAttemptRepository: QuizAttemptRepository = mockk(relaxed = true)
    private val stickerRepository: StickerRepository = mockk(relaxed = true)
    private val usageSyncScheduler: UsageSyncScheduler = mockk(relaxed = true)
    private val quizPackGenerationScheduler: QuizPackGenerationScheduler = mockk(relaxed = true)
    private val sessionController: ChildSessionController = mockk(relaxed = true)
    private val analyticsTracker: AnalyticsTracker = mockk(relaxed = true)
    private val nurseryCurriculumRepository: NurseryCurriculumRepository = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)
    private val learningResourceRepository: LearningResourceRepository = mockk(relaxed = true)

    private val sampleQuestion = QuizQuestion(
        id = "test_q1",
        ageBand = AgeBand.AGE_7_TO_9,
        topic = "math",
        conceptId = "division-sharing",
        conceptTitle = "Fair Sharing Division",
        difficulty = 2,
        prompt = "What is 48 ÷ 6?",
        choices = listOf(
            QuizChoice(id = "c1", text = "6", correct = false),
            QuizChoice(id = "c2", text = "8", correct = true),
        ),
        whyCorrect = "48 / 6 = 8",
        whyWrongByChoice = mapOf("c1" to "6 x 6 = 36, which is 12 less than 48."),
        conceptExplainer = "Division divides into equal groups.",
        interactionType = "TAP_TEXT",
        miniLesson = MiniLesson(
            title = "Fair Sharing",
            bodyLines = listOf("48 divided into 6 equal parts is 8."),
            illustrationAssetId = "sim_div",
        ),
    )

    private fun createViewModel(): ChildQuizViewModel = ChildQuizViewModel(
        pairingStore = pairingStore,
        policyRepository = policyRepository,
        quizRepository = quizRepository,
        quizAttemptRepository = quizAttemptRepository,
        stickerRepository = stickerRepository,
        usageSyncScheduler = usageSyncScheduler,
        quizPackGenerationScheduler = quizPackGenerationScheduler,
        sessionController = sessionController,
        analyticsTracker = analyticsTracker,
        nurseryCurriculumRepository = nurseryCurriculumRepository,
        networkMonitor = networkMonitor,
        learningResourceRepository = learningResourceRepository,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        coEvery { pairingStore.get() } returns ChildPairingCredential(
            familyId = "fam_1",
            childId = "child_123",
            deviceId = "dev_1",
            parentPinHash = "hash",
            displayName = "Leo",
        )
        coEvery { policyRepository.getPolicy("child_123") } returns ChildPolicy(
            questionsPerQuiz = 1,
            passScorePercent = 70,
            showExplanations = true,
            rewardsEnabled = true,
            extraMinutesOnPass = 30,
        )
        coEvery { policyRepository.getProfile("child_123") } returns ChildLocalProfile(
            childId = "child_123",
            familyId = "fam_1",
            displayName = "Leo",
            ageBand = "AGE_7_TO_9",
            avatarId = "rabbit",
            language = "en",
        )
        coEvery { quizRepository.questionsFor(any(), any()) } returns listOf(
            sampleQuestion,
            sampleQuestion.copy(id = "test_q2", prompt = "How many groups are in 48 divided by 6?"),
            sampleQuestion.copy(id = "test_q3", prompt = "What is half of 16?"),
        )
        coEvery { quizRepository.skills("child_123") } returns emptyMap()
        coEvery { quizRepository.recentIds("child_123") } returns setOf("test_q2", "test_q3")
        every { sessionController.snapshot } returns MutableStateFlow(SessionSnapshot())
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { quizAttemptRepository.record(any(), any(), any(), any(), any(), any()) } returns "attempt_1"
        coEvery { sessionController.onQuizPassed(any()) } returns SessionSnapshot(
            phase = SessionPhase.InBlock,
            blockDurationMinutes = 30,
        )
        coEvery { stickerRepository.awardQuizPass(any(), any(), any()) } returns
            com.meritscreen.feature.child.data.StickerAwardResult(
                progress = com.meritscreen.core.common.domain.ExplorerProgress(xp = 10, explorerLevel = 1),
                unlocked = null,
                xpGained = 10,
                leveledUp = false,
                weekendBonusApplied = false,
            )
        every { nurseryCurriculumRepository.pickDiverseVideo(any(), any(), any()) } returns NurseryVideo(
            videoId = "75p6yQbP8QU",
            title = "ABC Song",
            maxSeconds = 180,
        )
        every { nurseryCurriculumRepository.pickNextVideo(any(), any()) } returns NurseryVideo(
            videoId = "75p6yQbP8QU",
            title = "ABC Song",
            maxSeconds = 180,
        )
        every { nurseryCurriculumRepository.pickDiverseTeachItems(any(), any(), any()) } returns listOf(
            NurseryTeachItem("a_apple", "apple", "A for Apple", "Letter A", "🍎", "A is for Apple", "#FFEBEE"),
        )
        every { nurseryCurriculumRepository.pickNextTeachItems(any(), any(), any()) } returns listOf(
            NurseryTeachItem("a_apple", "apple", "A for Apple", "Letter A", "🍎", "A is for Apple", "#FFEBEE"),
        )
        every { learningResourceRepository.getStaticBaseline(any(), any(), any()) } returns
            StaticLearningResourceCatalog.getFor("division-sharing", "math")
        every { learningResourceRepository.getResourceStream(any(), any(), any(), any(), any(), any(), any()) } returns
            flowOf(StaticLearningResourceCatalog.getFor("division-sharing", "math"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads profile and directly begins Question step bypassing Intro`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect() }
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        val successState = (state as UiState.Success).data
        assertEquals("Leo", successState.childName)
        assertTrue(successState.step is QuizUiStep.Question)
        val questionStep = successState.step as QuizUiStep.Question
        assertEquals("test_q1", questionStep.question.id)

        collectJob.cancel()
    }

    @Test
    fun `answering incorrectly stays on Question step with inline wrong-answer explanation and resources`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect() }
        testScheduler.runCurrent()

        // Submit wrong answer "c1"
        viewModel.answer("c1")
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        val successState = (state as UiState.Success).data
        // Stays on Question step directly on the same page
        assertTrue(successState.step is QuizUiStep.Question)
        val questionStep = successState.step as QuizUiStep.Question
        assertEquals("c1", questionStep.evaluatedChoiceId)
        assertEquals(false, questionStep.isCorrect)
        assertEquals("6 x 6 = 36, which is 12 less than 48.", questionStep.whyWrongText)
        assertEquals("8", questionStep.correctChoiceText)
        assertNotNull(questionStep.resource)
        assertEquals("division-sharing", questionStep.resource?.conceptId)
        assertFalse(questionStep.showResourceModal)

        collectJob.cancel()
    }

    @Test
    fun `opening and closing learning resource modal updates state reactively`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect() }
        testScheduler.runCurrent()

        viewModel.answer("c1")
        testScheduler.runCurrent()

        // Open modal
        viewModel.openResourceModal()
        testScheduler.runCurrent()

        var step = (viewModel.uiState.value as UiState.Success).data.step as QuizUiStep.Question
        assertTrue(step.showResourceModal)

        // Close modal
        viewModel.closeResourceModal()
        testScheduler.runCurrent()

        step = (viewModel.uiState.value as UiState.Success).data.step as QuizUiStep.Question
        assertFalse(step.showResourceModal)

        collectJob.cancel()
    }

    @Test
    fun `playing and closing fullscreen video updates state and restores`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect() }
        testScheduler.runCurrent()

        viewModel.answer("c1")
        testScheduler.runCurrent()

        val testVideo = LearningResourceVideo(
            videoId = "LGqBQrUYua4",
            title = "Math Antics - Basic Division",
            channelName = "Math Antics",
            durationLabel = "9:58",
            description = "Basic division lesson",
        )

        viewModel.playResourceVideo(testVideo)
        testScheduler.runCurrent()

        var step = (viewModel.uiState.value as UiState.Success).data.step as QuizUiStep.Question
        assertEquals(testVideo, step.activeFullscreenVideo)

        viewModel.closeResourceVideo()
        testScheduler.runCurrent()

        step = (viewModel.uiState.value as UiState.Success).data.step as QuizUiStep.Question
        assertNull(step.activeFullscreenVideo)

        collectJob.cancel()
    }

    @Test
    fun `answering correctly shows inline positive affirmation and proceed advances quiz`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect() }
        testScheduler.runCurrent()

        // Thinking time is unlimited: an invisible question timeout must never turn a
        // correct answer into a failed quiz/rest period.
        testScheduler.advanceTimeBy(60_000L)
        viewModel.answer("c2")
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        val successState = (state as UiState.Success).data
        assertTrue(successState.step is QuizUiStep.Question)
        val questionStep = successState.step as QuizUiStep.Question
        assertEquals("c2", questionStep.evaluatedChoiceId)
        assertEquals(true, questionStep.isCorrect)

        // Proceed to Question 2
        viewModel.proceedToNextQuestion()
        testScheduler.runCurrent()

        val q2State = (viewModel.uiState.value as UiState.Success).data.step as QuizUiStep.Question
        assertEquals(2, q2State.index)
        assertNull(q2State.evaluatedChoiceId)

        // Question 2:
        viewModel.answer("c2")
        testScheduler.runCurrent()
        viewModel.proceedToNextQuestion()
        testScheduler.runCurrent()

        // Question 3:
        viewModel.answer("c2")
        testScheduler.runCurrent()
        viewModel.proceedToNextQuestion()
        testScheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertTrue(finalState is UiState.Success)
        val completed = (finalState as UiState.Success).data.step as QuizUiStep.Completed
        assertTrue(completed.passed)
        assertEquals(30, completed.unlockedMinutes)
        coVerify { sessionController.onQuizPassed(any()) }
        coVerify(exactly = 3) { quizRepository.markAsked("child_123", any()) }

        collectJob.cancel()
    }

    @Test
    fun `early learner age band starts NurseryVideo when online`() = runTest(testDispatcher) {
        coEvery { policyRepository.getProfile("child_123") } returns ChildLocalProfile(
            childId = "child_123",
            familyId = "fam_1",
            displayName = "Mia",
            ageBand = "AGE_3_TO_6",
            avatarId = "cat",
            language = "en",
        )
        every { networkMonitor.isCurrentlyOnline() } returns true

        val viewModel = createViewModel()
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        val successState = (state as UiState.Success).data
        assertEquals("Mia", successState.childName)
        assertEquals(AgeBand.AGE_3_TO_6, successState.ageBand)
        assertTrue(successState.step is QuizUiStep.NurseryVideo)

        collectJob.cancel()
    }

    @Test
    fun `early learner age band falls back to NurseryTeach cards when offline`() = runTest(testDispatcher) {
        coEvery { policyRepository.getProfile("child_123") } returns ChildLocalProfile(
            childId = "child_123",
            familyId = "fam_1",
            displayName = "Mia",
            ageBand = "AGE_3_TO_6",
            avatarId = "cat",
            language = "en",
        )
        every { networkMonitor.isCurrentlyOnline() } returns false

        val viewModel = createViewModel()
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        val successState = (state as UiState.Success).data
        assertTrue(successState.step is QuizUiStep.NurseryTeach)

        // Completing nursery teach
        viewModel.onNurseryComplete()
        testScheduler.advanceUntilIdle()

        val completedState = viewModel.uiState.value
        assertTrue(completedState is UiState.Success)
        val completedData = (completedState as UiState.Success).data
        assertTrue(completedData.step is QuizUiStep.Completed)

        coVerify { sessionController.onQuizPassed(any()) }

        collectJob.cancel()
    }
}
