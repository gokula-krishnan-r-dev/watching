import Foundation

public struct QuizChoice: Codable, Sendable, Equatable, Identifiable {
    public let id: String
    public let text: String
    public let correct: Bool
    public let imageTag: String?
    public let audioKey: String?

    public init(
        id: String,
        text: String = "",
        correct: Bool,
        imageTag: String? = nil,
        audioKey: String? = nil
    ) {
        self.id = id
        self.text = text
        self.correct = correct
        self.imageTag = imageTag
        self.audioKey = audioKey
    }
}

public struct MiniLesson: Codable, Sendable, Equatable {
    public let title: String
    public let bodyLines: [String]
    public let illustrationAssetId: String?

    public init(
        title: String,
        bodyLines: [String],
        illustrationAssetId: String? = nil
    ) {
        self.title = title
        self.bodyLines = bodyLines
        self.illustrationAssetId = illustrationAssetId
    }
}

public struct QuizQuestion: Codable, Sendable, Equatable, Identifiable {
    public let id: String
    public let ageBand: AgeBand
    public let topic: String
    public let conceptId: String
    public let conceptTitle: String
    public let difficulty: Int
    public let prompt: String
    public let choices: [QuizChoice]
    public let whyCorrect: String
    public let whyWrongByChoice: [String: String]
    public let conceptExplainer: String
    public let language: String
    public let interactionType: String
    public let promptTag: String?
    public let promptCount: Int?
    public let promptAudioKey: String?
    public let miniLesson: MiniLesson?
    public let source: String

    public init(
        id: String,
        ageBand: AgeBand,
        topic: String,
        conceptId: String,
        conceptTitle: String,
        difficulty: Int,
        prompt: String,
        choices: [QuizChoice],
        whyCorrect: String,
        whyWrongByChoice: [String: String] = [:],
        conceptExplainer: String,
        language: String = "en",
        interactionType: String = "TAP_TEXT",
        promptTag: String? = nil,
        promptCount: Int? = nil,
        promptAudioKey: String? = nil,
        miniLesson: MiniLesson? = nil,
        source: String = "builtin"
    ) {
        self.id = id
        self.ageBand = ageBand
        self.topic = topic
        self.conceptId = conceptId
        self.conceptTitle = conceptTitle
        self.difficulty = difficulty
        self.prompt = prompt
        self.choices = choices
        self.whyCorrect = whyCorrect
        self.whyWrongByChoice = whyWrongByChoice
        self.conceptExplainer = conceptExplainer
        self.language = language
        self.interactionType = interactionType
        self.promptTag = promptTag
        self.promptCount = promptCount
        self.promptAudioKey = promptAudioKey
        self.miniLesson = miniLesson
        self.source = source
    }

    enum CodingKeys: String, CodingKey {
        case id, ageBand, topic, conceptId, conceptTitle, difficulty
        case prompt, choices, whyCorrect, whyWrongByChoice, conceptExplainer
        case language, interactionType, promptTag, promptCount, promptAudioKey
        case miniLesson, source
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(String.self, forKey: .id)
        let rawAge = try container.decode(String.self, forKey: .ageBand)
        self.ageBand = AgeBand.fromStorage(rawAge)
        self.topic = try container.decode(String.self, forKey: .topic)
        self.conceptId = try container.decode(String.self, forKey: .conceptId)
        self.conceptTitle = try container.decode(String.self, forKey: .conceptTitle)
        self.difficulty = try container.decode(Int.self, forKey: .difficulty)
        self.prompt = try container.decode(String.self, forKey: .prompt)
        self.choices = try container.decode([QuizChoice].self, forKey: .choices)
        self.whyCorrect = try container.decode(String.self, forKey: .whyCorrect)
        self.whyWrongByChoice = try container.decodeIfPresent([String: String].self, forKey: .whyWrongByChoice) ?? [:]
        self.conceptExplainer = try container.decode(String.self, forKey: .conceptExplainer)
        self.language = try container.decodeIfPresent(String.self, forKey: .language) ?? "en"
        self.interactionType = try container.decodeIfPresent(String.self, forKey: .interactionType) ?? "TAP_TEXT"
        self.promptTag = try container.decodeIfPresent(String.self, forKey: .promptTag)
        self.promptCount = try container.decodeIfPresent(Int.self, forKey: .promptCount)
        self.promptAudioKey = try container.decodeIfPresent(String.self, forKey: .promptAudioKey)
        self.miniLesson = try container.decodeIfPresent(MiniLesson.self, forKey: .miniLesson)
        self.source = try container.decodeIfPresent(String.self, forKey: .source) ?? "builtin"
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(ageBand.rawValue, forKey: .ageBand)
        try container.encode(topic, forKey: .topic)
        try container.encode(conceptId, forKey: .conceptId)
        try container.encode(conceptTitle, forKey: .conceptTitle)
        try container.encode(difficulty, forKey: .difficulty)
        try container.encode(prompt, forKey: .prompt)
        try container.encode(choices, forKey: .choices)
        try container.encode(whyCorrect, forKey: .whyCorrect)
        try container.encode(whyWrongByChoice, forKey: .whyWrongByChoice)
        try container.encode(conceptExplainer, forKey: .conceptExplainer)
        try container.encode(language, forKey: .language)
        try container.encode(interactionType, forKey: .interactionType)
        try container.encodeIfPresent(promptTag, forKey: .promptTag)
        try container.encodeIfPresent(promptCount, forKey: .promptCount)
        try container.encodeIfPresent(promptAudioKey, forKey: .promptAudioKey)
        try container.encodeIfPresent(miniLesson, forKey: .miniLesson)
        try container.encode(source, forKey: .source)
    }
}

public struct TopicSkill: Codable, Sendable, Equatable {
    public var topic: String
    public var level: Int
    public var streakCorrect: Int
    public var weakConcepts: Set<String>
    public var conceptTitles: [String: String]
    public var masteredConcepts: Set<String>
    public var tierLabel: String
    public var totalAttempts: Int
    public var totalCorrect: Int
    public var avgResponseTimeMs: Int64

    public init(
        topic: String,
        level: Int = 2,
        streakCorrect: Int = 0,
        weakConcepts: Set<String> = [],
        conceptTitles: [String: String] = [:],
        masteredConcepts: Set<String> = [],
        tierLabel: String = "basic",
        totalAttempts: Int = 0,
        totalCorrect: Int = 0,
        avgResponseTimeMs: Int64 = 0
    ) {
        self.topic = topic
        self.level = level
        self.streakCorrect = streakCorrect
        self.weakConcepts = weakConcepts
        self.conceptTitles = conceptTitles
        self.masteredConcepts = masteredConcepts
        self.tierLabel = tierLabel
        self.totalAttempts = totalAttempts
        self.totalCorrect = totalCorrect
        self.avgResponseTimeMs = avgResponseTimeMs
    }
}

public struct QuizAnswerFeedback: Sendable, Equatable {
    public let correct: Bool
    public let resultLine: String
    public let whyLine: String
    public let conceptLine: String
    public let nextLevel: Int

    public init(
        correct: Bool,
        resultLine: String,
        whyLine: String,
        conceptLine: String,
        nextLevel: Int
    ) {
        self.correct = correct
        self.resultLine = resultLine
        self.whyLine = whyLine
        self.conceptLine = conceptLine
        self.nextLevel = nextLevel
    }
}

public struct QuizSessionResult: Codable, Sendable, Equatable {
    public let passed: Bool
    public let correctCount: Int
    public let total: Int
    public let percent: Int
    public let xpGained: Int
    public let unlockedMinutes: Int

    public init(
        passed: Bool,
        correctCount: Int,
        total: Int,
        percent: Int,
        xpGained: Int = 0,
        unlockedMinutes: Int = 0
    ) {
        self.passed = passed
        self.correctCount = correctCount
        self.total = total
        self.percent = percent
        self.xpGained = xpGained
        self.unlockedMinutes = unlockedMinutes
    }
}

public struct ChildSticker: Codable, Sendable, Equatable, Identifiable {
    public var id: String
    public var title: String
    public var emoji: String
    public var stage: String
    public var xpAtUnlock: Int
    public var unlockedAtEpochMs: Int64

    public init(
        id: String,
        title: String,
        emoji: String,
        stage: String = "Bronze",
        xpAtUnlock: Int = 0,
        unlockedAtEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.title = title
        self.emoji = emoji
        self.stage = stage
        self.xpAtUnlock = xpAtUnlock
        self.unlockedAtEpochMs = unlockedAtEpochMs
    }
}

public struct QuizVisualTaxonomyItem: Codable, Sendable, Equatable, Identifiable {
    public var id: String { tag }
    public let tag: String
    public let category: String
    public let label: String
    public let emoji: String

    public init(tag: String, category: String, label: String, emoji: String) {
        self.tag = tag
        self.category = category
        self.label = label
        self.emoji = emoji
    }
}

public struct QuizVisualTaxonomyEnvelope: Codable, Sendable {
    public let version: Int
    public let tags: [QuizVisualTaxonomyItem]
}
