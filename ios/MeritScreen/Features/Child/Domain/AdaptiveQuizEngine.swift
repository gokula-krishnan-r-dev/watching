import Foundation

/// Pure Swift adaptive quiz question picker and answer grader.
/// 100% offline — zero network or Firestore calls on child quiz path.
/// Levels stay inside child's age band (L1–L5).
public enum AdaptiveQuizEngine {

    /// Loads the builtin quiz bank bundled with the application.
    public static func loadBuiltinQuizBank(bundle: Bundle = .main) -> [QuizQuestion] {
        guard let url = bundle.url(forResource: "quiz_bank_builtin", withExtension: "json") ??
                Bundle(for: BundleToken.self).url(forResource: "quiz_bank_builtin", withExtension: "json") else {
            return []
        }
        do {
            let data = try Data(contentsOf: url)
            let decoder = JSONDecoder()
            return try decoder.decode([QuizQuestion].self, from: data)
        } catch {
            print("[AdaptiveQuizEngine] Failed to load quiz bank: \(error)")
            return []
        }
    }

    /// Loads the visual taxonomy bundled with the application.
    public static func loadVisualTaxonomy(bundle: Bundle = .main) -> [QuizVisualTaxonomyItem] {
        guard let url = bundle.url(forResource: "quiz_visual_taxonomy", withExtension: "json") ??
                Bundle(for: BundleToken.self).url(forResource: "quiz_visual_taxonomy", withExtension: "json") else {
            return []
        }
        do {
            let data = try Data(contentsOf: url)
            let envelope = try JSONDecoder().decode(QuizVisualTaxonomyEnvelope.self, from: data)
            return envelope.tags
        } catch {
            print("[AdaptiveQuizEngine] Failed to load visual taxonomy: \(error)")
            return []
        }
    }

    /// Shuffles choices so the correct answer is not statically pinned to the first position.
    public static func withShuffledChoices(_ question: QuizQuestion) -> QuizQuestion {
        guard question.choices.count > 1 else { return question }
        return QuizQuestion(
            id: question.id,
            ageBand: question.ageBand,
            topic: question.topic,
            conceptId: question.conceptId,
            conceptTitle: question.conceptTitle,
            difficulty: question.difficulty,
            prompt: question.prompt,
            choices: question.choices.shuffled(),
            whyCorrect: question.whyCorrect,
            whyWrongByChoice: question.whyWrongByChoice,
            conceptExplainer: question.conceptExplainer,
            language: question.language,
            interactionType: question.interactionType,
            promptTag: question.promptTag,
            promptCount: question.promptCount,
            promptAudioKey: question.promptAudioKey,
            miniLesson: question.miniLesson,
            source: question.source
        )
    }

    public static func tierLabel(level: Int, streakCorrect: Int) -> String {
        if level >= 5 && streakCorrect >= 2 { return "mastery" }
        if level >= 5 || level == 4 { return "advanced" }
        if level == 3 { return "intermediate" }
        return "basic"
    }

    public static func sessionStartLevel(skills: [String: TopicSkill]) -> Int {
        if skills.isEmpty { return 2 }
        let levels = skills.values.map { $0.level }.sorted()
        return min(max(levels[levels.count / 2], 1), 5)
    }

    public static func pickNext(
        bank: [QuizQuestion],
        targetAgeBand: AgeBand,
        skills: [String: TopicSkill] = [:],
        recentIds: Set<String> = [],
        usedInSession: Set<String> = [],
        lastWrongConceptId: String? = nil,
        preferredLevel: Int = 2
    ) -> QuizQuestion? {
        if bank.isEmpty { return nil }

        // Filter by age band first
        let ageMatched = bank.filter { $0.ageBand == targetAgeBand }
        let effectiveBank = ageMatched.isEmpty ? bank : ageMatched

        let freshUnused = effectiveBank.filter {
            !usedInSession.contains($0.id) &&
            !recentIds.contains($0.id) &&
            !recentIds.contains(promptHistoryKey($0.prompt))
        }

        // If history window covers the entire bank, allow older questions again,
        // but never repeat one inside the active quiz session.
        let pool = freshUnused.isEmpty ? effectiveBank.filter { !usedInSession.contains($0.id) } : freshUnused
        if pool.isEmpty { return nil }

        // If last question was wrong, provide an easier question on the same concept
        if let lastWrong = lastWrongConceptId {
            let easier = pool.filter {
                $0.conceptId == lastWrong && $0.difficulty <= preferredLevel
            }.sorted { $0.difficulty < $1.difficulty }
            if let firstEasier = easier.first {
                return withShuffledChoices(firstEasier)
            }
        }

        let near = pool.filter { abs($0.difficulty - preferredLevel) <= 1 }
        let candidates = near.isEmpty ? pool : near

        // Pick random from candidates
        if let chosen = candidates.randomElement() {
            return withShuffledChoices(chosen)
        }
        return pool.randomElement().map { withShuffledChoices($0) }
    }

    public static func gradeAnswer(
        question: QuizQuestion,
        choiceId: String,
        skill: TopicSkill,
        responseTimeMs: Int64 = 0
    ) -> (QuizAnswerFeedback, TopicSkill) {
        let choice = question.choices.first { $0.id == choiceId }
        let correct = choice?.correct == true

        let nextLevel: Int
        if correct && (skill.streakCorrect + 1) >= 2 {
            nextLevel = min(5, skill.level + 1)
        } else if correct {
            nextLevel = skill.level
        } else {
            nextLevel = max(1, skill.level - 1)
        }

        let nextStreak = correct ? (skill.streakCorrect + 1) : 0
        var weak = skill.weakConcepts
        var titles = skill.conceptTitles
        if correct {
            weak.remove(question.conceptId)
            titles.removeValue(forKey: question.conceptId)
        } else {
            weak.insert(question.conceptId)
            titles[question.conceptId] = question.conceptTitle
        }

        let newAttempts = skill.totalAttempts + 1
        let newCorrect = correct ? (skill.totalCorrect + 1) : skill.totalCorrect
        let newAvgTime: Int64
        if skill.totalAttempts == 0 {
            newAvgTime = responseTimeMs
        } else if responseTimeMs > 0 {
            newAvgTime = (skill.avgResponseTimeMs * Int64(skill.totalAttempts) + responseTimeMs) / Int64(newAttempts)
        } else {
            newAvgTime = skill.avgResponseTimeMs
        }

        let effectiveStreak = (correct && nextLevel > skill.level) ? 0 : nextStreak
        let nextTier = tierLabel(level: nextLevel, streakCorrect: effectiveStreak)
        var mastered = skill.masteredConcepts
        if correct && (nextLevel >= 4 || effectiveStreak >= 2) {
            mastered.insert(question.conceptId)
        }

        let whyLine: String
        if correct {
            whyLine = question.whyCorrect
        } else if let specificWhy = question.whyWrongByChoice[choiceId], !specificWhy.isEmpty {
            whyLine = specificWhy
        } else {
            whyLine = "That choice doesn’t match. Let’s look at the idea."
        }

        let feedback = QuizAnswerFeedback(
            correct: correct,
            resultLine: correct ? "That’s it! 🎉" : "Not this one — let’s learn!",
            whyLine: whyLine,
            conceptLine: question.conceptExplainer,
            nextLevel: nextLevel
        )

        let updatedSkill = TopicSkill(
            topic: skill.topic,
            level: nextLevel,
            streakCorrect: effectiveStreak,
            weakConcepts: weak,
            conceptTitles: titles,
            masteredConcepts: mastered,
            tierLabel: nextTier,
            totalAttempts: newAttempts,
            totalCorrect: newCorrect,
            avgResponseTimeMs: newAvgTime
        )

        return (feedback, updatedSkill)
    }

    public static func finalize(
        correctCount: Int,
        total: Int,
        passScorePercent: Int,
        rule: AppRule?,
        policy: ChildPolicy
    ) -> QuizSessionResult {
        let safeTotal = max(1, total)
        let percent = (correctCount * 100) / safeTotal
        let passed = percent >= passScorePercent

        let xpGained: Int
        let unlockedMinutes: Int

        if passed {
            var xp = policy.rewardsEnabled ? AppConfig.stickerQuizPassXp : 0
            if policy.rewardsEnabled && policy.weekendBonusEnabled && SessionEngine.isWeekend() {
                xp += AppConfig.stickerWeekendBonusXp
            }
            xpGained = xp

            let grant = (rule?.grantOnPassMinutes ?? policy.defaultBlockMinutes)
                .clamped(to: AppConfig.sessionChunkMinMinutes...AppConfig.sessionChunkMaxMinutes)
            var extra = policy.rewardsEnabled ? policy.extraMinutesOnPass : 0
            if policy.rewardsEnabled && policy.weekendBonusEnabled && SessionEngine.isWeekend() {
                extra += AppConfig.weekendBonusExtraMinutes
            }
            unlockedMinutes = (grant + extra).clamped(to: AppConfig.sessionChunkMinMinutes...AppConfig.sessionChunkMaxMinutes)
        } else {
            xpGained = 0
            unlockedMinutes = 0
        }

        return QuizSessionResult(
            passed: passed,
            correctCount: correctCount,
            total: safeTotal,
            percent: percent,
            xpGained: xpGained,
            unlockedMinutes: unlockedMinutes
        )
    }

    public static func questionsPerQuiz(configured: Int) -> Int {
        min(max(configured, 3), 5)
    }

    public static func promptHistoryKey(_ prompt: String) -> String {
        let filtered = prompt.lowercased().filter { $0.isLetter || $0.isNumber }
        return "prompt:\(filtered)"
    }
}

private final class BundleToken {}

private extension Comparable {
    func clamped(to limits: ClosedRange<Self>) -> Self {
        min(max(self, limits.lowerBound), limits.upperBound)
    }
}
