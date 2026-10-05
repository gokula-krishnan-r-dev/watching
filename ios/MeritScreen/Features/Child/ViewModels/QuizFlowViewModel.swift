import SwiftUI

public enum QuizFlowStep: Equatable {
    case intro
    case question
    case explanation
    case result
}

/// View model driving the multi-step quiz flow (C07b–C11).
/// 100% offline — questions loaded from local builtin JSON bank.
@Observable
@MainActor
public final class QuizFlowViewModel {

    // MARK: - State Properties

    public var step: QuizFlowStep = .intro
    public var childName: String = "Leo"
    public var ageBand: AgeBand = .band_7_9
    public var targetRule: AppRule
    public var isRetryMode: Bool = false

    public var questions: [QuizQuestion] = []
    public var currentQuestionIndex: Int = 0
    public var totalQuestions: Int = 3
    public var selectedChoiceId: String?
    public var feedback: QuizAnswerFeedback?
    public var correctCount: Int = 0
    public var finalResult: QuizSessionResult?
    public var unlockedSticker: ChildSticker?

    public var currentQuestion: QuizQuestion? {
        guard currentQuestionIndex < questions.count else { return nil }
        return questions[currentQuestionIndex]
    }

    private let localStore: ChildLocalStore
    private let policy: ChildPolicy
    private var questionStartTimeMs: Int64 = 0

    public init(
        targetRule: AppRule,
        isRetryMode: Bool = false,
        localStore: ChildLocalStore = .shared,
        childName: String = "Leo",
        ageBand: AgeBand = .band_7_9
    ) {
        self.targetRule = targetRule
        self.isRetryMode = isRetryMode
        self.localStore = localStore
        self.childName = childName
        self.ageBand = ageBand
        self.policy = localStore.getCachedPolicy()
        self.totalQuestions = AdaptiveQuizEngine.questionsPerQuiz(configured: policy.questionsPerQuiz)
    }

    // MARK: - Flow Controls

    public func startQuiz() {
        let bank = AdaptiveQuizEngine.loadBuiltinQuizBank()
        let skills = localStore.getTopicSkills()
        let recentIds = localStore.getRecentQuestionIds()

        var selectedQuestions: [QuizQuestion] = []
        var usedInSession: Set<String> = []
        let lastWrongConcept: String? = nil

        let startLevel = AdaptiveQuizEngine.sessionStartLevel(skills: skills)

        for _ in 0..<totalQuestions {
            if let picked = AdaptiveQuizEngine.pickNext(
                bank: bank,
                targetAgeBand: ageBand,
                skills: skills,
                recentIds: recentIds,
                usedInSession: usedInSession,
                lastWrongConceptId: lastWrongConcept,
                preferredLevel: startLevel
            ) {
                selectedQuestions.append(picked)
                usedInSession.insert(picked.id)
            }
        }

        // Fallback safety if bank is tiny or empty
        if selectedQuestions.isEmpty {
            selectedQuestions = sampleFallbackQuestions()
        }

        self.questions = selectedQuestions
        self.totalQuestions = selectedQuestions.count
        self.currentQuestionIndex = 0
        self.correctCount = 0
        self.selectedChoiceId = nil
        self.feedback = nil
        self.step = .question
        self.questionStartTimeMs = Int64(Date().timeIntervalSince1970 * 1000)
    }

    public func selectChoice(_ choiceId: String) {
        selectedChoiceId = choiceId
    }

    public func submitAnswer() {
        guard let choiceId = selectedChoiceId, let question = currentQuestion else { return }

        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let responseTime = max(0, now - questionStartTimeMs)

        let skills = localStore.getTopicSkills()
        let currentSkill = skills[question.topic] ?? TopicSkill(topic: question.topic)

        let (resultFeedback, updatedSkill) = AdaptiveQuizEngine.gradeAnswer(
            question: question,
            choiceId: choiceId,
            skill: currentSkill,
            responseTimeMs: responseTime
        )

        localStore.updateTopicSkill(updatedSkill)
        localStore.recordQuestionAnswered(questionId: question.id, prompt: question.prompt)

        self.feedback = resultFeedback
        if resultFeedback.correct {
            correctCount += 1
        }

        if policy.showExplanations || !resultFeedback.correct {
            step = .explanation
        } else {
            advanceToNext()
        }
    }

    public func advanceToNext() {
        if currentQuestionIndex + 1 < questions.count {
            currentQuestionIndex += 1
            selectedChoiceId = nil
            feedback = nil
            step = .question
            questionStartTimeMs = Int64(Date().timeIntervalSince1970 * 1000)
        } else {
            finishQuiz()
        }
    }

    private func finishQuiz() {
        let result = AdaptiveQuizEngine.finalize(
            correctCount: correctCount,
            total: totalQuestions,
            passScorePercent: policy.passScorePercent,
            rule: targetRule,
            policy: policy
        )
        self.finalResult = result

        let now = Int64(Date().timeIntervalSince1970 * 1000)
        var snapshot = localStore.getSessionSnapshot()

        if result.passed {
            // Passed quiz -> update SessionEngine and award XP / sticker
            snapshot = SessionEngine.onQuizPassed(
                snapshot: snapshot,
                nowEpochMs: now,
                rule: targetRule,
                policy: policy
            )

            if result.xpGained > 0 {
                let newTotal = localStore.addXp(result.xpGained)
                // Check if new level or sticker unlock
                if newTotal >= 20 {
                    let freshSticker = ChildSticker(
                        id: "star_learner_\(UUID().uuidString.prefix(4))",
                        title: "Quiz Master",
                        emoji: "🏆",
                        stage: "Gold",
                        xpAtUnlock: newTotal
                    )
                    localStore.addSticker(freshSticker)
                    self.unlockedSticker = freshSticker
                }
            }
        } else {
            // Failed quiz -> device-wide fail lock
            if isRetryMode {
                // Invariant: Failed retry during cooldown restarts the cooldown timer!
                snapshot = SessionEngine.onRetryQuizFailed(
                    snapshot: snapshot,
                    nowEpochMs: now,
                    policy: policy,
                    rule: targetRule
                )
            } else {
                snapshot = SessionEngine.onQuizFailed(
                    snapshot: snapshot,
                    nowEpochMs: now,
                    policy: policy,
                    rule: targetRule
                )
            }
        }

        localStore.saveSessionSnapshot(snapshot)
        self.step = .result
    }

    // MARK: - Fallback Questions

    private func sampleFallbackQuestions() -> [QuizQuestion] {
        return [
            QuizQuestion(
                id: "sample_01",
                ageBand: ageBand,
                topic: "math",
                conceptId: "addition-basics",
                conceptTitle: "Adding small numbers",
                difficulty: 1,
                prompt: "What is 3 + 2?",
                choices: [
                    QuizChoice(id: "a", text: "4", correct: false),
                    QuizChoice(id: "b", text: "5", correct: true),
                    QuizChoice(id: "c", text: "6", correct: false)
                ],
                whyCorrect: "3 plus 2 equals 5!",
                whyWrongByChoice: ["a": "3 + 1 is 4. Add one more for 5.", "c": "3 + 3 is 6."],
                conceptExplainer: "Counting forward: start at 3, count 2 more: 4, 5."
            )
        ]
    }
}
