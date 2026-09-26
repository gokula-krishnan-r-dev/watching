package com.meritscreen.core.common.domain

import kotlinx.serialization.Serializable

/**
 * Product age bands from `docs/01-product-overview.md`. The parent picks the band; quiz
 * difficulty then adapts inside that band from the child's answers (see `docs/06`). Shared
 * across onboarding, child-profile editing, and the quiz engine, so it lives in `core:common`
 * rather than a single feature module.
 */
@Serializable
enum class AgeBand(
    val displayLabel: String,
    val quizStyleDescription: String,
) {
    AGE_3_TO_6(
        displayLabel = "3–6",
        quizStyleDescription = "Colors, shapes, matching, simple counting",
    ),
    AGE_7_TO_9(
        displayLabel = "7–9",
        quizStyleDescription = "Basic math, spelling, reading",
    ),
    AGE_10_TO_12(
        displayLabel = "10–12",
        quizStyleDescription = "General knowledge, reasoning, slightly harder math",
    ),
}
