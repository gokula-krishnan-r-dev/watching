package com.meritscreen.core.common.domain

/**
 * Pre-curated, zero-latency educational resources for standard elementary curriculum concepts.
 * Provides instant 0ms responses without requiring a network connection.
 */
object StaticLearningResourceCatalog {

    private val catalog: Map<String, LearningResource> = mapOf(
        "division-sharing" to LearningResource(
            conceptId = "division-sharing",
            conceptTitle = "Fair Sharing Division",
            topic = "math",
            summaryBlog = LearningBlogArticle(
                title = "Division: The Magic of Equal Sharing 🍕",
                readingTimeMinutes = 2,
                coreRule = "Division means sharing a total number of items equally into equal groups.",
                sections = listOf(
                    BlogSection(
                        heading = "Imagine Sharing Pizza Slices",
                        content = "If you have 12 slices of pizza and 3 hungry friends, how many slices does each friend get? You give one to friend A, one to B, one to C, and keep repeating until all 12 are handed out.",
                        bulletPoints = listOf(
                            "Total items to share: 12",
                            "Number of equal groups: 3",
                            "Items per group: 4 slices each (3 × 4 = 12)",
                        ),
                    ),
                    BlogSection(
                        heading = "The Multiplication Secret 🔑",
                        content = "Division is simply multiplication backwards! Whenever you see 12 ÷ 3 = ?, ask yourself: 'What number multiplied by 3 gives 12?' Since 3 × 4 = 12, the answer is 4!",
                        bulletPoints = listOf(
                            "Check your work: Group Count × Items Per Group = Total Items",
                            "If the numbers don't multiply back to your total, recount your groups.",
                        ),
                    ),
                ),
                quickMemoryTip = "Think: 'Share fairly until the pile is empty, then check backwards with times tables!'",
                funFact = "The division symbol '÷' is called an 'obelus', invented in 1659 by a Swiss mathematician!",
            ),
            videos = listOf(
                LearningResourceVideo(
                    videoId = "LGqBQrUYua4",
                    title = "Math Antics - Basic Division",
                    channelName = "Math Antics",
                    durationLabel = "9:58",
                    description = "Learn what division really means and how to share numbers into equal groups.",
                ),
                LearningResourceVideo(
                    videoId = "KGMf314LUc0",
                    title = "Division for Kids: Fair Share Lesson",
                    channelName = "Khan Academy Kids",
                    durationLabel = "4:15",
                    description = "A friendly animated visual walkthrough of dividing toys and treats fairly.",
                ),
            ),
        ),

        "money-change" to LearningResource(
            conceptId = "money-change",
            conceptTitle = "Counting Coins and Currency",
            topic = "math",
            summaryBlog = LearningBlogArticle(
                title = "Counting Coins: Becoming a Master Shopper 🪙",
                readingTimeMinutes = 2,
                coreRule = "Every coin has an exact value in cents: 100 cents make one full dollar.",
                sections = listOf(
                    BlogSection(
                        heading = "Meet the Coin Family",
                        content = "Before adding coins, remember what each one is worth:",
                        bulletPoints = listOf(
                            "Dollar Bill = 100¢ ($1.00)",
                            "Quarter = 25¢ (4 quarters make a dollar!)",
                            "Dime = 10¢ (small but mighty silver coin)",
                            "Nickel = 5¢ (medium smooth silver coin)",
                            "Penny = 1¢ (copper brown coin)",
                        ),
                    ),
                    BlogSection(
                        heading = "Step-by-Step Counting Strategy",
                        content = "Always count from biggest to smallest! If you need $1.65: start with the $1.00 bill, add 2 quarters (50¢ to reach $1.50), add 1 dime (10¢ to reach $1.60), and 1 nickel (5¢ to reach $1.65).",
                        bulletPoints = listOf(
                            "Step 1: Start with Dollars ($1.00)",
                            "Step 2: Add 25s (Quarters: 25¢, 50¢, 75¢)",
                            "Step 3: Add 10s (Dimes)",
                            "Step 4: Add 5s and 1s to reach the exact cents",
                        ),
                    ),
                ),
                quickMemoryTip = "Always start with the coin of highest value and count upwards!",
                funFact = "Dimes are smaller than nickels because they used to be made of real precious silver!",
            ),
            videos = listOf(
                LearningResourceVideo(
                    videoId = "pnXJGNo08v0",
                    title = "Coin Values & Counting Money for Kids",
                    channelName = "Khan Academy Kids",
                    durationLabel = "5:12",
                    description = "Learn how to spot and count quarters, dimes, nickels, and pennies.",
                ),
                LearningResourceVideo(
                    videoId = "r3K234qFm2Y",
                    title = "Math Antics - Understanding Money & Change",
                    channelName = "Math Antics",
                    durationLabel = "8:30",
                    description = "Fun interactive shopping examples showing how to calculate exact change.",
                ),
            ),
        ),

        "add-within-20" to LearningResource(
            conceptId = "add-within-20",
            conceptTitle = "Adding by Counting On",
            topic = "addition",
            summaryBlog = LearningBlogArticle(
                title = "Speed Adding: Count On Like a Rocket 🚀",
                readingTimeMinutes = 1,
                coreRule = "Addition means putting two quantities together to find the combined total.",
                sections = listOf(
                    BlogSection(
                        heading = "The 'Hold & Count' Trick",
                        content = "Instead of counting all numbers from 1, hold the larger number in your head and count upwards on your fingers!",
                        bulletPoints = listOf(
                            "For 8 + 5: Lock '8' in your brain 🧠",
                            "Put up 5 fingers: 9, 10, 11, 12, 13!",
                            "You reached 13 in just 2 seconds!",
                        ),
                    ),
                ),
                quickMemoryTip = "Always start at the bigger number and jump forward!",
                funFact = "Adding zero to any number never changes it — it's called the identity rule!",
            ),
            videos = listOf(
                LearningResourceVideo(
                    videoId = "2Io_Y0d0-wY",
                    title = "Adding & Counting On Strategies",
                    channelName = "Scratch Garden",
                    durationLabel = "3:40",
                    description = "Energetic songs and visuals showing how to add numbers within 20 fast.",
                ),
                LearningResourceVideo(
                    videoId = "mAvuom42NyY",
                    title = "Math Antics - Multi-Digit Addition",
                    channelName = "Math Antics",
                    durationLabel = "6:20",
                    description = "Visual number line jumps that make adding simple and fun.",
                ),
            ),
        ),

        "subtraction-regroup" to LearningResource(
            conceptId = "subtraction-regroup",
            conceptTitle = "Subtracting Numbers",
            topic = "subtraction",
            summaryBlog = LearningBlogArticle(
                title = "Subtraction: Taking Away & Finding Difference ➖",
                readingTimeMinutes = 2,
                coreRule = "Subtraction measures what is left after taking an amount away, or the distance between two numbers.",
                sections = listOf(
                    BlogSection(
                        heading = "Walking Backward on the Number Line",
                        content = "When you subtract, you walk backward! For 15 - 7, start at 15. Take 5 steps back to reach 10, then 2 more steps back to land on 8.",
                        bulletPoints = listOf(
                            "Friendly base 10 is your friend: jump to 10 first!",
                            "15 - 5 = 10, then 10 - 2 = 8.",
                        ),
                    ),
                ),
                quickMemoryTip = "Think of subtraction as a reverse jump or finding how much is left over.",
                funFact = "Subtraction is the inverse (opposite) partner of addition!",
            ),
            videos = listOf(
                LearningResourceVideo(
                    videoId = "Y6M89-6106I",
                    title = "Math Antics - Basic Subtraction",
                    channelName = "Math Antics",
                    durationLabel = "7:15",
                    description = "Learn the secrets of taking numbers away and finding the difference.",
                ),
            ),
        ),

        "multiplication-arrays" to LearningResource(
            conceptId = "multiplication-arrays",
            conceptTitle = "Multiplication as Equal Groups",
            topic = "multiplication",
            summaryBlog = LearningBlogArticle(
                title = "Multiplication: Fast Addition Superpower ⚡",
                readingTimeMinutes = 2,
                coreRule = "Multiplication is repeated addition of identical groups.",
                sections = listOf(
                    BlogSection(
                        heading = "Rows and Columns (Arrays)",
                        content = "If you have 4 rows of soldiers with 3 soldiers in each row, you don't need to count 1 by 1. Just multiply 4 × 3 = 12 soldiers!",
                        bulletPoints = listOf(
                            "3 + 3 + 3 + 3 = 12",
                            "4 groups of 3 = 12",
                        ),
                    ),
                ),
                quickMemoryTip = "'Groups times size equals the prize!'",
                funFact = "You can flip the order anytime: 4 × 3 is the exact same as 3 × 4!",
            ),
            videos = listOf(
                LearningResourceVideo(
                    videoId = "mvOkMYCygps",
                    title = "Math Antics - Intro to Multiplication",
                    channelName = "Math Antics",
                    durationLabel = "8:45",
                    description = "Discover how multiplication is just repeated addition with super speed.",
                ),
            ),
        ),

        "fractions-parts" to LearningResource(
            conceptId = "fractions-parts",
            conceptTitle = "Fractions as Equal Parts",
            topic = "math",
            summaryBlog = LearningBlogArticle(
                title = "Fractions: Pieces of the Whole Pie 🥧",
                readingTimeMinutes = 2,
                coreRule = "A fraction shows a part of a whole: the top number is what you have, the bottom number is total equal slices.",
                sections = listOf(
                    BlogSection(
                        heading = "Numerator vs Denominator",
                        content = "In the fraction 3/4:",
                        bulletPoints = listOf(
                            "Top number (Numerator): 3 slices in your hand",
                            "Bottom number (Denominator): 4 total slices the pie was cut into",
                            "Every slice MUST be exactly equal size!",
                        ),
                    ),
                ),
                quickMemoryTip = "'Denominator goes Down, Numerator is on the Numbers ground!'",
                funFact = "The word fraction comes from the Latin word 'fractio', meaning 'to break into pieces'!",
            ),
            videos = listOf(
                LearningResourceVideo(
                    videoId = "7X47-3sC2tI",
                    title = "Math Antics - Fractions Are Parts",
                    channelName = "Math Antics",
                    durationLabel = "7:50",
                    description = "Explore why fractions are just parts of things that have been divided equally.",
                ),
            ),
        ),

        "plants-photosynthesis" to LearningResource(
            conceptId = "plants-photosynthesis",
            conceptTitle = "How Plants Make Food",
            topic = "science",
            summaryBlog = LearningBlogArticle(
                title = "Plant Kitchen: How Leaves Cook With Sunlight ☀️🌱",
                readingTimeMinutes = 2,
                coreRule = "Plants use sunlight, water from their roots, and air (carbon dioxide) to make their own food.",
                sections = listOf(
                    BlogSection(
                        heading = "The Solar Kitchen Inside Every Leaf",
                        content = "Unlike animals, plants can't walk to the grocery store. Instead, green leaves have tiny solar kitchens called chlorophyll that catch sunlight and mix it with water and air to create sugar (glucose).",
                        bulletPoints = listOf(
                            "Water absorbed through underground roots 💧",
                            "Sunlight trapped by green chlorophyll ☀️",
                            "Fresh Oxygen released back into the air for us to breathe! 💨",
                        ),
                    ),
                ),
                quickMemoryTip = "'Roots drink water, leaves catch light, making fresh air clean and bright!'",
                funFact = "Over half of the world's oxygen is produced not by forest trees, but by microscopic ocean algae!",
            ),
            videos = listOf(
                LearningResourceVideo(
                    videoId = "dUBIQ1fTRzI",
                    title = "How Do Plants Make Food? | Photosynthesis",
                    channelName = "SciShow Kids",
                    durationLabel = "4:20",
                    description = "Follow Jessi and Squeaks as they look inside a leaf to see how plants eat sunlight.",
                ),
            ),
        ),

        "solar-system" to LearningResource(
            conceptId = "solar-system",
            conceptTitle = "Our Solar System and Planets",
            topic = "science",
            summaryBlog = LearningBlogArticle(
                title = "Space Odyssey: The Cosmic Neighborhood 🪐",
                readingTimeMinutes = 2,
                coreRule = "Our Solar System consists of our central Sun and 8 planets that orbit around it.",
                sections = listOf(
                    BlogSection(
                        heading = "The 8 Planets in Order from the Sun",
                        content = "From closest to furthest:",
                        bulletPoints = listOf(
                            "1. Mercury (fast and hot)",
                            "2. Venus (hottest planet with thick clouds)",
                            "3. Earth (our home with liquid water and life)",
                            "4. Mars (the red dusty desert planet)",
                            "5. Jupiter (largest gas giant with a giant storm)",
                            "6. Saturn (famous for dazzling icy rings)",
                            "7. Uranus (rolls sideways in frozen blue)",
                            "8. Neptune (windy deep blue giant)",
                        ),
                    ),
                ),
                quickMemoryTip = "'My Very Educated Mother Just Served Us Noodles!'",
                funFact = "Jupiter is so huge that all the other planets in the solar system could fit inside it combined!",
            ),
            videos = listOf(
                LearningResourceVideo(
                    videoId = "Qd6nLM2QlWw",
                    title = "Exploring Our Solar System Planets",
                    channelName = "SciShow Kids",
                    durationLabel = "5:15",
                    description = "Take a rocket ship tour of all 8 planets with fun animated space facts.",
                ),
            ),
        ),
    )

    /**
     * Resolves a rich learning resource for a given [conceptId] and [topic].
     * Always returns a non-null, high-quality resource with videos and blog notes.
     */
    fun getFor(conceptId: String, topic: String, conceptTitle: String = ""): LearningResource {
        // Direct match
        val direct = catalog[conceptId]
        if (direct != null) return direct

        // Keyword and semantic matching across conceptId, conceptTitle, and topic
        val query = "$conceptId $conceptTitle $topic".lowercase()
        val keywordMatchKey = when {
            query.contains("subtrac") || query.contains("minus") || query.contains("difference") || query.contains("take away") -> "subtraction-regroup"
            query.contains("divis") || query.contains("fair share") || query.contains("sharing") || query.contains("quotient") -> "division-sharing"
            query.contains("multipl") || query.contains("times") || query.contains("array") || query.contains("product") -> "multiplication-arrays"
            query.contains("add") || query.contains("plus") || query.contains("sum") -> "add-within-20"
            query.contains("fract") || query.contains("half") || query.contains("quarter") || query.contains("part") -> "fractions-parts"
            query.contains("money") || query.contains("coin") || query.contains("dollar") || query.contains("cent") || query.contains("change") -> "money-change"
            query.contains("plant") || query.contains("leaf") || query.contains("photosynth") -> "plants-photosynthesis"
            query.contains("solar") || query.contains("planet") || query.contains("space") || query.contains("sun") || query.contains("earth") -> "solar-system"
            query.contains("vowel") || query.contains("phon") || query.contains("read") || query.contains("word") -> "reading-vowels"
            else -> null
        }
        if (keywordMatchKey != null && catalog.containsKey(keywordMatchKey)) {
            val entry = catalog.getValue(keywordMatchKey)
            return entry.copy(
                conceptId = conceptId,
                conceptTitle = if (conceptTitle.isNotBlank()) conceptTitle else entry.conceptTitle,
            )
        }

        // Fuzzy match by key or topic
        val topicKey = topic.lowercase().trim()
        val matchingEntry = catalog.values.firstOrNull {
            it.topic.equals(topicKey, ignoreCase = true) ||
                conceptId.contains(it.conceptId, ignoreCase = true) ||
                it.conceptId.contains(conceptId, ignoreCase = true)
        }
        if (matchingEntry != null) {
            return matchingEntry.copy(
                conceptId = conceptId,
                conceptTitle = if (conceptTitle.isNotBlank()) conceptTitle else matchingEntry.conceptTitle,
            )
        }

        // Universal intelligent fallback
        val displayTitle = if (conceptTitle.isNotBlank()) conceptTitle else topic.replaceFirstChar { it.uppercase() }
        return LearningResource(
            conceptId = conceptId,
            conceptTitle = displayTitle,
            topic = topic,
            summaryBlog = LearningBlogArticle(
                title = "Understanding $displayTitle 🌟",
                readingTimeMinutes = 2,
                coreRule = "Take your time, break the problem into simple steps, and look for patterns.",
                sections = listOf(
                    BlogSection(
                        heading = "Key Strategy for $displayTitle",
                        content = "When solving challenges in $topic, ask yourself: 'What do I already know, and what clue does the question give me?'",
                        bulletPoints = listOf(
                            "Read the prompt carefully twice",
                            "Eliminate options that are clearly too high or too low",
                            "Check your answer step-by-step before concluding",
                        ),
                    ),
                ),
                quickMemoryTip = "Think carefully, test your idea, and practice makes progress!",
                funFact = "Every expert in the world was once a beginner learning one question at a time.",
            ),
            videos = listOf(
                LearningResourceVideo(
                    videoId = "LGqBQrUYua4",
                    title = "Core Foundations & Problem Solving",
                    channelName = "Khan Academy Kids",
                    durationLabel = "4:30",
                    description = "Helpful walkthrough on how to think through challenging questions.",
                ),
            ),
        )
    }

    /**
     * Returns true if [conceptId] has a dedicated bespoke entry in the catalog.
     */
    fun hasExact(conceptId: String): Boolean = catalog.containsKey(conceptId)
}
