package com.meritscreen.core.common.domain

/**
 * Age-appropriate curriculum focus chips shown on Add Child.
 * Stable [id] values are persisted on [ChildPolicy.curriculumFocusIds] and sent to the
 * quiz pack generator as topic weights — display labels may change without breaking storage.
 */
enum class CurriculumFocusTopic(
    val id: String,
    val displayLabel: String,
    val ageBands: Set<AgeBand>,
) {
    // Ages 3–6 — sensory / early literacy, no formal math or finance
    COLORS_SHAPES(
        id = "colors_shapes",
        displayLabel = "Colors & Shapes",
        ageBands = setOf(AgeBand.AGE_3_TO_6),
    ),
    LETTERS_SOUNDS(
        id = "letters_sounds",
        displayLabel = "Letters & Sounds",
        ageBands = setOf(AgeBand.AGE_3_TO_6),
    ),
    COUNTING_PLAY(
        id = "counting_play",
        displayLabel = "Counting Play",
        ageBands = setOf(AgeBand.AGE_3_TO_6),
    ),
    STORIES_SONGS(
        id = "stories_songs",
        displayLabel = "Stories & Songs",
        ageBands = setOf(AgeBand.AGE_3_TO_6),
    ),
    NATURE_ANIMALS(
        id = "nature_animals",
        displayLabel = "Nature & Animals",
        ageBands = setOf(AgeBand.AGE_3_TO_6),
    ),
    SOCIAL_PLAY(
        id = "social_play",
        displayLabel = "Feelings & Sharing",
        ageBands = setOf(AgeBand.AGE_3_TO_6),
    ),

    // Ages 7–9 — elementary foundations
    MATH_NUMBERS(
        id = "math_numbers",
        displayLabel = "Math & Numbers",
        ageBands = setOf(AgeBand.AGE_7_TO_9, AgeBand.AGE_10_TO_12),
    ),
    READING_PHONICS(
        id = "reading_phonics",
        displayLabel = "Reading & Phonics",
        ageBands = setOf(AgeBand.AGE_7_TO_9),
    ),
    SCIENCE_NATURE(
        id = "science_nature",
        displayLabel = "Science & Nature",
        ageBands = setOf(AgeBand.AGE_7_TO_9, AgeBand.AGE_10_TO_12),
    ),
    PROBLEM_SOLVING(
        id = "problem_solving",
        displayLabel = "Problem Solving",
        ageBands = setOf(AgeBand.AGE_7_TO_9, AgeBand.AGE_10_TO_12),
    ),
    CREATIVE_WRITING(
        id = "creative_writing",
        displayLabel = "Creative Writing",
        ageBands = setOf(AgeBand.AGE_7_TO_9),
    ),
    WORLD_AROUND_US(
        id = "world_around_us",
        displayLabel = "World Around Us",
        ageBands = setOf(AgeBand.AGE_7_TO_9),
    ),

    // Ages 10–12 — middle prep
    READING_COMPREHENSION(
        id = "reading_comprehension",
        displayLabel = "Reading Comprehension",
        ageBands = setOf(AgeBand.AGE_10_TO_12),
    ),
    FINANCIAL_LITERACY(
        id = "financial_literacy",
        displayLabel = "Financial Literacy",
        ageBands = setOf(AgeBand.AGE_10_TO_12),
    ),
    CODING_LOGIC(
        id = "coding_logic",
        displayLabel = "Coding & Logic",
        ageBands = setOf(AgeBand.AGE_10_TO_12),
    ),
    HISTORY_CIVICS(
        id = "history_civics",
        displayLabel = "History & Civics",
        ageBands = setOf(AgeBand.AGE_10_TO_12),
    ),
    ;

    companion object {
        fun fromId(id: String): CurriculumFocusTopic? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/**
 * Pure helpers for age-banded curriculum focus selection (UI + policy persistence).
 */
object CurriculumFocusCatalog {

    const val MIN_SELECTION: Int = 1
    const val MAX_SELECTION: Int = 4

    fun optionsFor(ageBand: AgeBand): List<CurriculumFocusTopic> =
        CurriculumFocusTopic.entries.filter { ageBand in it.ageBands }

    fun defaultIds(ageBand: AgeBand): List<String> = when (ageBand) {
        AgeBand.AGE_3_TO_6 -> listOf(
            CurriculumFocusTopic.COLORS_SHAPES.id,
            CurriculumFocusTopic.LETTERS_SOUNDS.id,
        )
        AgeBand.AGE_7_TO_9 -> listOf(
            CurriculumFocusTopic.MATH_NUMBERS.id,
            CurriculumFocusTopic.READING_PHONICS.id,
        )
        AgeBand.AGE_10_TO_12 -> listOf(
            CurriculumFocusTopic.MATH_NUMBERS.id,
            CurriculumFocusTopic.READING_COMPREHENSION.id,
        )
    }

    /**
     * Keeps only ids valid for [ageBand]. If nothing remains, returns [defaultIds].
     * Caps at [MAX_SELECTION] and guarantees at least [MIN_SELECTION] when options exist.
     */
    fun reconcile(ageBand: AgeBand, selectedIds: Collection<String>): List<String> {
        val allowed = optionsFor(ageBand).map { it.id }.toSet()
        val kept = selectedIds
            .map { it.trim() }
            .filter { it in allowed }
            .distinct()
            .take(MAX_SELECTION)
        return kept.ifEmpty { defaultIds(ageBand) }
    }

    fun toggle(ageBand: AgeBand, selectedIds: Collection<String>, topicId: String): List<String> {
        val allowed = optionsFor(ageBand).map { it.id }.toSet()
        if (topicId !in allowed) return reconcile(ageBand, selectedIds)
        val current = reconcile(ageBand, selectedIds).toMutableList()
        if (topicId in current) {
            if (current.size <= MIN_SELECTION) return current
            current.remove(topicId)
        } else if (current.size < MAX_SELECTION) {
            current.add(topicId)
        }
        return current
    }

    fun displayLabels(ids: Collection<String>): List<String> =
        ids.mapNotNull { CurriculumFocusTopic.fromId(it)?.displayLabel }

    /** Compact hint string for AI pack generation (topic weights only). */
    fun parentFocusHint(ids: Collection<String>): String {
        val labels = displayLabels(ids)
        if (labels.isEmpty()) return ""
        return "Prioritize topics: ${labels.joinToString(", ")}."
    }
}
