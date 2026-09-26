package com.meritscreen.core.common.domain

import kotlinx.serialization.Serializable

/**
 * Educational video recommendation matched to a curriculum concept.
 * [videoId] is a standard YouTube 11-character video ID that can be rendered in an iframe.
 */
@Serializable
data class LearningResourceVideo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val durationLabel: String,
    val description: String,
)

/**
 * Structured section of an educational explainer blog for kids.
 */
@Serializable
data class BlogSection(
    val heading: String,
    val content: String,
    val bulletPoints: List<String> = emptyList(),
)

/**
 * Child-friendly educational explainer document/blog report.
 * Provides intuitive real-world explanations, steps, memory hacks, and fun facts.
 */
@Serializable
data class LearningBlogArticle(
    val title: String,
    val readingTimeMinutes: Int = 2,
    val coreRule: String,
    val sections: List<BlogSection>,
    val quickMemoryTip: String,
    val funFact: String? = null,
)

/**
 * Comprehensive learning resource bundle for a concept, combining structured blog articles
 * and curated educational videos.
 */
@Serializable
data class LearningResource(
    val conceptId: String,
    val conceptTitle: String,
    val topic: String,
    val summaryBlog: LearningBlogArticle,
    val videos: List<LearningResourceVideo>,
    val isAiGenerated: Boolean = false,
)
