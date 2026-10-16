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

// MARK: - Learning resources (parity with Android StaticLearningResourceCatalog)

public struct LearningResourceVideo: Codable, Sendable, Equatable, Identifiable {
    public var id: String { videoId }
    public let videoId: String
    public let title: String
    public let channelName: String
    public let durationLabel: String
    public let description: String

    public init(
        videoId: String,
        title: String,
        channelName: String,
        durationLabel: String,
        description: String
    ) {
        self.videoId = videoId
        self.title = title
        self.channelName = channelName
        self.durationLabel = durationLabel
        self.description = description
    }

    public var youtubeURL: URL? {
        URL(string: "https://www.youtube.com/watch?v=\(videoId)")
    }
}

public struct BlogSection: Codable, Sendable, Equatable {
    public let heading: String
    public let content: String
    public let bulletPoints: [String]

    public init(heading: String, content: String, bulletPoints: [String] = []) {
        self.heading = heading
        self.content = content
        self.bulletPoints = bulletPoints
    }
}

public struct LearningBlogArticle: Codable, Sendable, Equatable {
    public let title: String
    public let readingTimeMinutes: Int
    public let coreRule: String
    public let sections: [BlogSection]
    public let quickMemoryTip: String
    public let funFact: String?

    public init(
        title: String,
        readingTimeMinutes: Int = 2,
        coreRule: String,
        sections: [BlogSection],
        quickMemoryTip: String,
        funFact: String? = nil
    ) {
        self.title = title
        self.readingTimeMinutes = readingTimeMinutes
        self.coreRule = coreRule
        self.sections = sections
        self.quickMemoryTip = quickMemoryTip
        self.funFact = funFact
    }
}

public struct LearningResource: Codable, Sendable, Equatable {
    public let conceptId: String
    public let conceptTitle: String
    public let topic: String
    public let summaryBlog: LearningBlogArticle
    public let videos: [LearningResourceVideo]
    public let isAiGenerated: Bool

    public init(
        conceptId: String,
        conceptTitle: String,
        topic: String,
        summaryBlog: LearningBlogArticle,
        videos: [LearningResourceVideo],
        isAiGenerated: Bool = false
    ) {
        self.conceptId = conceptId
        self.conceptTitle = conceptTitle
        self.topic = topic
        self.summaryBlog = summaryBlog
        self.videos = videos
        self.isAiGenerated = isAiGenerated
    }
}

/// Offline learning resource catalog mirrored from Android `StaticLearningResourceCatalog`.
public enum StaticLearningResourceCatalog {
    public static func getFor(conceptId: String, topic: String, conceptTitle: String = "") -> LearningResource {
        if let direct = catalog[conceptId] {
            return direct
        }

        let query = "\(conceptId) \(conceptTitle) \(topic)".lowercased()
        let keywordKey: String?
        if query.contains("subtrac") || query.contains("minus") || query.contains("difference") || query.contains("take away") {
            keywordKey = "subtraction-regroup"
        } else if query.contains("divis") || query.contains("fair share") || query.contains("sharing") || query.contains("quotient") {
            keywordKey = "division-sharing"
        } else if query.contains("multipl") || query.contains("times") || query.contains("array") || query.contains("product") {
            keywordKey = "multiplication-arrays"
        } else if query.contains("add") || query.contains("plus") || query.contains("sum") {
            keywordKey = "add-within-20"
        } else if query.contains("fract") || query.contains("half") || query.contains("quarter") || query.contains("part") {
            keywordKey = "fractions-parts"
        } else if query.contains("money") || query.contains("coin") || query.contains("dollar") || query.contains("cent") || query.contains("change") {
            keywordKey = "money-change"
        } else if query.contains("plant") || query.contains("leaf") || query.contains("photosynth") {
            keywordKey = "plants-photosynthesis"
        } else if query.contains("solar") || query.contains("planet") || query.contains("space") || query.contains("sun") || query.contains("earth") {
            keywordKey = "solar-system"
        } else {
            keywordKey = nil
        }

        if let key = keywordKey, let entry = catalog[key] {
            return LearningResource(
                conceptId: conceptId,
                conceptTitle: conceptTitle.isEmpty ? entry.conceptTitle : conceptTitle,
                topic: entry.topic,
                summaryBlog: entry.summaryBlog,
                videos: entry.videos,
                isAiGenerated: entry.isAiGenerated
            )
        }

        if let matching = catalog.values.first(where: {
            $0.topic.caseInsensitiveCompare(topic) == .orderedSame ||
                conceptId.localizedCaseInsensitiveContains($0.conceptId) ||
                $0.conceptId.localizedCaseInsensitiveContains(conceptId)
        }) {
            return LearningResource(
                conceptId: conceptId,
                conceptTitle: conceptTitle.isEmpty ? matching.conceptTitle : conceptTitle,
                topic: matching.topic,
                summaryBlog: matching.summaryBlog,
                videos: matching.videos,
                isAiGenerated: matching.isAiGenerated
            )
        }

        let displayTitle = conceptTitle.isEmpty
            ? topic.prefix(1).uppercased() + topic.dropFirst()
            : conceptTitle
        return LearningResource(
            conceptId: conceptId,
            conceptTitle: displayTitle,
            topic: topic,
            summaryBlog: LearningBlogArticle(
                title: "Understanding \(displayTitle)",
                readingTimeMinutes: 2,
                coreRule: "Take your time, break the problem into simple steps, and look for patterns.",
                sections: [
                    BlogSection(
                        heading: "Key Strategy for \(displayTitle)",
                        content: "When solving challenges in \(topic), ask yourself: 'What do I already know, and what clue does the question give me?'",
                        bulletPoints: [
                            "Read the prompt carefully twice",
                            "Eliminate options that are clearly too high or too low",
                            "Check your answer step-by-step before concluding"
                        ]
                    )
                ],
                quickMemoryTip: "Think carefully, test your idea, and practice makes progress!",
                funFact: "Every expert in the world was once a beginner learning one question at a time."
            ),
            videos: [
                LearningResourceVideo(
                    videoId: "LGqBQrUYua4",
                    title: "Core Foundations & Problem Solving",
                    channelName: "Khan Academy Kids",
                    durationLabel: "4:30",
                    description: "Helpful walkthrough on how to think through challenging questions."
                )
            ]
        )
    }

    private static let catalog: [String: LearningResource] = [
        "division-sharing": LearningResource(
            conceptId: "division-sharing",
            conceptTitle: "Fair Sharing Division",
            topic: "math",
            summaryBlog: LearningBlogArticle(
                title: "Division: The Magic of Equal Sharing",
                coreRule: "Division means sharing a total number of items equally into equal groups.",
                sections: [
                    BlogSection(
                        heading: "Imagine Sharing Pizza Slices",
                        content: "If you have 12 slices of pizza and 3 hungry friends, how many slices does each friend get? You give one to friend A, one to B, one to C, and keep repeating until all 12 are handed out.",
                        bulletPoints: [
                            "Total items to share: 12",
                            "Number of equal groups: 3",
                            "Items per group: 4 slices each (3 × 4 = 12)"
                        ]
                    ),
                    BlogSection(
                        heading: "The Multiplication Secret",
                        content: "Division is simply multiplication backwards! Whenever you see 12 ÷ 3 = ?, ask yourself: 'What number multiplied by 3 gives 12?' Since 3 × 4 = 12, the answer is 4!",
                        bulletPoints: [
                            "Check your work: Group Count × Items Per Group = Total Items",
                            "If the numbers don't multiply back to your total, recount your groups."
                        ]
                    )
                ],
                quickMemoryTip: "Think: 'Share fairly until the pile is empty, then check backwards with times tables!'",
                funFact: "The division symbol '÷' is called an 'obelus', invented in 1659 by a Swiss mathematician!"
            ),
            videos: [
                LearningResourceVideo(
                    videoId: "LGqBQrUYua4",
                    title: "Math Antics - Basic Division",
                    channelName: "Math Antics",
                    durationLabel: "9:58",
                    description: "Learn what division really means and how to share numbers into equal groups."
                )
            ]
        ),
        "money-change": LearningResource(
            conceptId: "money-change",
            conceptTitle: "Counting Coins and Currency",
            topic: "math",
            summaryBlog: LearningBlogArticle(
                title: "Counting Coins: Becoming a Master Shopper",
                coreRule: "Every coin has an exact value in cents: 100 cents make one full dollar.",
                sections: [
                    BlogSection(
                        heading: "Meet the Coin Family",
                        content: "Before adding coins, remember what each one is worth:",
                        bulletPoints: [
                            "Dollar Bill = 100¢ ($1.00)",
                            "Quarter = 25¢ (4 quarters make a dollar!)",
                            "Dime = 10¢",
                            "Nickel = 5¢",
                            "Penny = 1¢"
                        ]
                    )
                ],
                quickMemoryTip: "Always count from biggest to smallest!",
                funFact: "A nickel is worth more than a dime even though it is larger — value is not about size!"
            ),
            videos: [
                LearningResourceVideo(
                    videoId: "r3K234qFm2Y",
                    title: "Math Antics - Understanding Money & Change",
                    channelName: "Math Antics",
                    durationLabel: "8:30",
                    description: "Fun interactive shopping examples showing how to calculate exact change."
                )
            ]
        ),
        "add-within-20": LearningResource(
            conceptId: "add-within-20",
            conceptTitle: "Adding by Counting On",
            topic: "addition",
            summaryBlog: LearningBlogArticle(
                title: "Speed Adding: Count On Like a Rocket",
                readingTimeMinutes: 1,
                coreRule: "Addition means putting two quantities together to find the combined total.",
                sections: [
                    BlogSection(
                        heading: "The 'Hold & Count' Trick",
                        content: "Instead of counting all numbers from 1, hold the larger number in your head and count upwards on your fingers!",
                        bulletPoints: [
                            "For 8 + 5: Lock '8' in your brain",
                            "Put up 5 fingers: 9, 10, 11, 12, 13!",
                            "You reached 13 in just 2 seconds!"
                        ]
                    )
                ],
                quickMemoryTip: "Always start at the bigger number and jump forward!",
                funFact: "Adding zero to any number never changes it — it's called the identity rule!"
            ),
            videos: [
                LearningResourceVideo(
                    videoId: "2Io_Y0d0-wY",
                    title: "Adding & Counting On Strategies",
                    channelName: "Scratch Garden",
                    durationLabel: "3:40",
                    description: "Energetic songs and visuals showing how to add numbers within 20 fast."
                )
            ]
        ),
        "subtraction-regroup": LearningResource(
            conceptId: "subtraction-regroup",
            conceptTitle: "Subtracting Numbers",
            topic: "subtraction",
            summaryBlog: LearningBlogArticle(
                title: "Subtraction: Taking Away & Finding Difference",
                coreRule: "Subtraction measures what is left after taking an amount away, or the distance between two numbers.",
                sections: [
                    BlogSection(
                        heading: "Walking Backward on the Number Line",
                        content: "When you subtract, you walk backward! For 15 - 7, start at 15. Take 5 steps back to reach 10, then 2 more steps back to land on 8.",
                        bulletPoints: [
                            "Friendly base 10 is your friend: jump to 10 first!",
                            "15 - 5 = 10, then 10 - 2 = 8."
                        ]
                    )
                ],
                quickMemoryTip: "Think of subtraction as a reverse jump or finding how much is left over.",
                funFact: "Subtraction is the inverse (opposite) partner of addition!"
            ),
            videos: [
                LearningResourceVideo(
                    videoId: "Y6M89-6106I",
                    title: "Math Antics - Basic Subtraction",
                    channelName: "Math Antics",
                    durationLabel: "7:15",
                    description: "Learn the secrets of taking numbers away and finding the difference."
                )
            ]
        ),
        "multiplication-arrays": LearningResource(
            conceptId: "multiplication-arrays",
            conceptTitle: "Multiplication as Equal Groups",
            topic: "multiplication",
            summaryBlog: LearningBlogArticle(
                title: "Multiplication: Fast Addition Superpower",
                coreRule: "Multiplication is repeated addition of identical groups.",
                sections: [
                    BlogSection(
                        heading: "Rows and Columns (Arrays)",
                        content: "If you have 4 rows of soldiers with 3 soldiers in each row, you don't need to count 1 by 1. Just multiply 4 × 3 = 12 soldiers!",
                        bulletPoints: [
                            "3 + 3 + 3 + 3 = 12",
                            "4 groups of 3 = 12"
                        ]
                    )
                ],
                quickMemoryTip: "'Groups times size equals the prize!'",
                funFact: "You can flip the order anytime: 4 × 3 is the exact same as 3 × 4!"
            ),
            videos: [
                LearningResourceVideo(
                    videoId: "mvOkMYCygps",
                    title: "Math Antics - Intro to Multiplication",
                    channelName: "Math Antics",
                    durationLabel: "8:45",
                    description: "Discover how multiplication is just repeated addition with super speed."
                )
            ]
        ),
        "fractions-parts": LearningResource(
            conceptId: "fractions-parts",
            conceptTitle: "Fractions as Equal Parts",
            topic: "math",
            summaryBlog: LearningBlogArticle(
                title: "Fractions: Pieces of the Whole Pie",
                coreRule: "A fraction shows a part of a whole: the top number is what you have, the bottom number is total equal slices.",
                sections: [
                    BlogSection(
                        heading: "Numerator vs Denominator",
                        content: "In the fraction 3/4:",
                        bulletPoints: [
                            "Top number (Numerator): 3 slices in your hand",
                            "Bottom number (Denominator): 4 total slices the pie was cut into",
                            "Every slice MUST be exactly equal size!"
                        ]
                    )
                ],
                quickMemoryTip: "'Denominator goes Down, Numerator is on the Numbers ground!'",
                funFact: "The word fraction comes from the Latin word 'fractio', meaning 'to break into pieces'!"
            ),
            videos: [
                LearningResourceVideo(
                    videoId: "7X47-3sC2tI",
                    title: "Math Antics - Fractions Are Parts",
                    channelName: "Math Antics",
                    durationLabel: "7:50",
                    description: "Explore why fractions are just parts of things that have been divided equally."
                )
            ]
        ),
        "plants-photosynthesis": LearningResource(
            conceptId: "plants-photosynthesis",
            conceptTitle: "How Plants Make Food",
            topic: "science",
            summaryBlog: LearningBlogArticle(
                title: "Plant Kitchen: How Leaves Cook With Sunlight",
                coreRule: "Plants use sunlight, water from their roots, and air (carbon dioxide) to make their own food.",
                sections: [
                    BlogSection(
                        heading: "The Solar Kitchen Inside Every Leaf",
                        content: "Unlike animals, plants can't walk to the grocery store. Instead, green leaves have tiny solar kitchens called chlorophyll that catch sunlight and mix it with water and air to create sugar (glucose).",
                        bulletPoints: [
                            "Water absorbed through underground roots",
                            "Sunlight trapped by green chlorophyll",
                            "Fresh Oxygen released back into the air for us to breathe!"
                        ]
                    )
                ],
                quickMemoryTip: "'Roots drink water, leaves catch light, making fresh air clean and bright!'",
                funFact: "Over half of the world's oxygen is produced not by forest trees, but by microscopic ocean algae!"
            ),
            videos: [
                LearningResourceVideo(
                    videoId: "dUBIQ1fTRzI",
                    title: "How Do Plants Make Food? | Photosynthesis",
                    channelName: "SciShow Kids",
                    durationLabel: "4:20",
                    description: "Follow Jessi and Squeaks as they look inside a leaf to see how plants eat sunlight."
                )
            ]
        ),
        "solar-system": LearningResource(
            conceptId: "solar-system",
            conceptTitle: "Our Solar System and Planets",
            topic: "science",
            summaryBlog: LearningBlogArticle(
                title: "Space Odyssey: The Cosmic Neighborhood",
                coreRule: "Our Solar System consists of our central Sun and 8 planets that orbit around it.",
                sections: [
                    BlogSection(
                        heading: "The 8 Planets in Order from the Sun",
                        content: "From closest to furthest:",
                        bulletPoints: [
                            "1. Mercury (fast and hot)",
                            "2. Venus (hottest planet with thick clouds)",
                            "3. Earth (our home with liquid water and life)",
                            "4. Mars (the red dusty desert planet)",
                            "5. Jupiter (largest gas giant with a giant storm)",
                            "6. Saturn (famous for dazzling icy rings)",
                            "7. Uranus (rolls sideways in frozen blue)",
                            "8. Neptune (windy deep blue giant)"
                        ]
                    )
                ],
                quickMemoryTip: "'My Very Educated Mother Just Served Us Noodles!'",
                funFact: "Jupiter is so huge that all the other planets in the solar system could fit inside it combined!"
            ),
            videos: [
                LearningResourceVideo(
                    videoId: "Qd6nLM2QlWw",
                    title: "Exploring Our Solar System Planets",
                    channelName: "SciShow Kids",
                    durationLabel: "5:15",
                    description: "Take a rocket ship tour of all 8 planets with fun animated space facts."
                )
            ]
        )
    ]
}
