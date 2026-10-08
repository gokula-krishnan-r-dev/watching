package com.meritscreen.core.common.domain

/**
 * Age-banded "Quick add" chips for parent [ChildPolicy.customPromptGuidelines] /
 * Personalized Learning Guidance. Labels are UI-only; [snippet] is appended as topic-
 * weighting text (still sanitized on save).
 *
 * Keep snippets free of names, PII, and instruction-override phrases — they feed the
 * same path as free-text guidelines ([CustomPromptSanitizer]).
 */
data class LearningPromptQuickAdd(
    val id: String,
    val label: String,
    val snippet: String,
)

/**
 * Catalog of frequently useful parent focus hints, keyed by [AgeBand].
 * Shared by parent adaptive settings and onboarding AI learning context.
 */
object LearningPromptQuickAdds {

    private val earlyLearner: List<LearningPromptQuickAdd> = listOf(
        LearningPromptQuickAdd(
            id = "letter_sounds",
            label = "Letter sounds",
            snippet = "Practice letter sounds and simple phonics with short, playful prompts.",
        ),
        LearningPromptQuickAdd(
            id = "counting_play",
            label = "Counting play",
            snippet = "Focus on counting, number recognition, and comparing small quantities.",
        ),
        LearningPromptQuickAdd(
            id = "colors_shapes",
            label = "Colors & shapes",
            snippet = "Emphasize colors, shapes, matching, and sorting games.",
        ),
        LearningPromptQuickAdd(
            id = "picture_stories",
            label = "Picture stories",
            snippet = "Use picture-based stories and listening comprehension; keep text minimal.",
        ),
        LearningPromptQuickAdd(
            id = "visual_cues",
            label = "Visual learner",
            snippet = "Prefer pictures, icons, and simple diagrams over long text.",
        ),
        LearningPromptQuickAdd(
            id = "gentle_pace",
            label = "Gentle pace",
            snippet = "Keep questions short and encouraging; allow extra thinking time.",
        ),
    )

    private val elementary: List<LearningPromptQuickAdd> = listOf(
        LearningPromptQuickAdd(
            id = "math_foundations",
            label = "Math practice",
            snippet = "Needs extra practice with addition, subtraction, and early multiplication.",
        ),
        LearningPromptQuickAdd(
            id = "reading_phonics",
            label = "Reading & phonics",
            snippet = "Strengthen phonics, fluency, and short reading comprehension passages.",
        ),
        LearningPromptQuickAdd(
            id = "science_curiosity",
            label = "Science curiosity",
            snippet = "Include simple science and nature topics with everyday examples.",
        ),
        LearningPromptQuickAdd(
            id = "visual_learner",
            label = "Visual learner",
            snippet = "Responds best to visual cues, diagrams, and pictorial puzzles.",
        ),
        LearningPromptQuickAdd(
            id = "school_curriculum",
            label = "School curriculum",
            snippet = "Align topics with typical school curriculum for this grade level.",
        ),
        LearningPromptQuickAdd(
            id = "dyslexia_friendly",
            label = "Dyslexia friendly",
            snippet = "Prefer clear wording, shorter sentences, and dyslexia-friendly presentation.",
        ),
    )

    private val middlePrep: List<LearningPromptQuickAdd> = listOf(
        LearningPromptQuickAdd(
            id = "math_challenge",
            label = "Weak in Math",
            snippet = "Needs extra practice with fractions, decimals, and multi-step word problems.",
        ),
        LearningPromptQuickAdd(
            id = "reading_comprehension",
            label = "Reading depth",
            snippet = "Challenge with longer comprehension passages and inference questions.",
        ),
        LearningPromptQuickAdd(
            id = "board_focus",
            label = "Board / exam focus",
            snippet = "Favor exam-style reasoning and curriculum topics common in school boards.",
        ),
        LearningPromptQuickAdd(
            id = "coding_logic",
            label = "Coding & logic",
            snippet = "Include logic puzzles, patterns, and introductory computational thinking.",
        ),
        LearningPromptQuickAdd(
            id = "visual_learner",
            label = "Visual learner",
            snippet = "Responds best to visual cues, diagrams, and structured layouts.",
        ),
        LearningPromptQuickAdd(
            id = "fast_reader",
            label = "Fast reader",
            snippet = "Reads beyond grade level; likes challenging comprehension prompts.",
        ),
        LearningPromptQuickAdd(
            id = "dyslexia_friendly",
            label = "Dyslexia friendly",
            snippet = "Prefer clear wording, shorter sentences, and dyslexia-friendly presentation.",
        ),
    )

    /** Age-appropriate quick-add chips for the given band (stable order). */
    fun forAgeBand(ageBand: AgeBand): List<LearningPromptQuickAdd> = when (ageBand) {
        AgeBand.AGE_3_TO_6 -> earlyLearner
        AgeBand.AGE_7_TO_9 -> elementary
        AgeBand.AGE_10_TO_12 -> middlePrep
    }

    /**
     * Same as [forAgeBand], but drops chips whose snippet is already present in [currentText]
     * (case-insensitive) so parents don't re-tap duplicates.
     */
    fun forAgeBand(
        ageBand: AgeBand,
        currentText: String,
    ): List<LearningPromptQuickAdd> {
        val lower = currentText.lowercase()
        if (lower.isBlank()) return forAgeBand(ageBand)
        return forAgeBand(ageBand).filter { chip ->
            chip.snippet.lowercase() !in lower
        }
    }
}
