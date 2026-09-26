package com.meritscreen.feature.child.domain

import androidx.compose.ui.graphics.Color

data class VisualTaxonomyItem(
    val tag: String,
    val category: String,
    val label: String,
    val emoji: String,
    val tintColor: Color = Color.Unspecified,
    val assetPath: String = "visual/$category/$tag.webp",
)

/**
 * Local offline taxonomy resolver for Early Learner (Ages 3-5) visual questions (docs 06 & 08).
 * Pure Kotlin / Compose mapping without network calls.
 */
object VisualTaxonomy {

    private val ITEMS = listOf(
        // Animals
        VisualTaxonomyItem("dog", "animals", "Dog", "🐶"),
        VisualTaxonomyItem("cat", "animals", "Cat", "🐱"),
        VisualTaxonomyItem("cow", "animals", "Cow", "🐮"),
        VisualTaxonomyItem("lion", "animals", "Lion", "🦁"),
        VisualTaxonomyItem("elephant", "animals", "Elephant", "🐘"),
        VisualTaxonomyItem("rabbit", "animals", "Rabbit", "🐰"),
        VisualTaxonomyItem("fox", "animals", "Fox", "🦊"),
        VisualTaxonomyItem("bear", "animals", "Bear", "🐻"),
        VisualTaxonomyItem("duck", "animals", "Duck", "🦆"),
        VisualTaxonomyItem("owl", "animals", "Owl", "🦉"),

        // Fruits
        VisualTaxonomyItem("apple", "fruits", "Apple", "🍎"),
        VisualTaxonomyItem("banana", "fruits", "Banana", "🍌"),
        VisualTaxonomyItem("orange", "fruits", "Orange", "🍊"),
        VisualTaxonomyItem("strawberry", "fruits", "Strawberry", "🍓"),
        VisualTaxonomyItem("grapes", "fruits", "Grapes", "🍇"),
        VisualTaxonomyItem("watermelon", "fruits", "Watermelon", "🍉"),

        // Shapes
        VisualTaxonomyItem("circle", "shapes", "Circle", "⭕", Color(0xFF00514D)),
        VisualTaxonomyItem("square", "shapes", "Square", "⬛", Color(0xFF685D4B)),
        VisualTaxonomyItem("triangle", "shapes", "Triangle", "🔺", Color(0xFF13522F)),
        VisualTaxonomyItem("star", "shapes", "Star", "⭐", Color(0xFFE59A00)),
        VisualTaxonomyItem("heart", "shapes", "Heart", "❤️", Color(0xFFBA1A1A)),

        // Colors
        VisualTaxonomyItem("color_red", "colors", "Red", "🔴", Color(0xFFBA1A1A)),
        VisualTaxonomyItem("color_blue", "colors", "Blue", "🔵", Color(0xFF1976D2)),
        VisualTaxonomyItem("color_yellow", "colors", "Yellow", "🟡", Color(0xFFFBC02D)),
        VisualTaxonomyItem("color_green", "colors", "Green", "🟢", Color(0xFF13522F)),
        VisualTaxonomyItem("color_orange", "colors", "Orange", "🟠", Color(0xFFF57C00)),
        VisualTaxonomyItem("color_purple", "colors", "Purple", "🟣", Color(0xFF7B1FA2)),

        // Numerals
        VisualTaxonomyItem("numeral_1", "numerals", "1", "1"),
        VisualTaxonomyItem("numeral_2", "numerals", "2", "2"),
        VisualTaxonomyItem("numeral_3", "numerals", "3", "3"),
        VisualTaxonomyItem("numeral_4", "numerals", "4", "4"),
        VisualTaxonomyItem("numeral_5", "numerals", "5", "5"),
        VisualTaxonomyItem("numeral_6", "numerals", "6", "6"),
        VisualTaxonomyItem("numeral_7", "numerals", "7", "7"),
        VisualTaxonomyItem("numeral_8", "numerals", "8", "8"),
        VisualTaxonomyItem("numeral_9", "numerals", "9", "9"),
        VisualTaxonomyItem("numeral_10", "numerals", "10", "10"),
    )

    private val BY_TAG = ITEMS.associateBy { it.tag }

    fun isKnown(tag: String): Boolean = tag in BY_TAG

    fun allTags(): Set<String> = BY_TAG.keys

    fun getItem(tag: String): VisualTaxonomyItem? = BY_TAG[tag]

    fun label(tag: String): String = BY_TAG[tag]?.label ?: tag.replace('_', ' ').replaceFirstChar { it.uppercase() }

    fun emoji(tag: String): String = BY_TAG[tag]?.emoji ?: "🌟"

    fun tagsForCategory(category: String): List<String> = ITEMS.filter { it.category == category }.map { it.tag }
}
