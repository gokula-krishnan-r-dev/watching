package com.meritscreen.core.common.domain

import kotlinx.serialization.Serializable

/**
 * A small preset avatar pack so a child profile never needs a camera or photo upload
 * (the product explicitly avoids storing a child's photo).
 */
@Serializable
enum class AvatarPreset(val emoji: String, val label: String = "") {
    RABBIT("🐰", "Rabbit"),
    BEAR("🐻", "Bear"),
    FOX("🦊", "Fox"),
    OWL("🦉", "Owl"),
    TURTLE("🐢", "Turtle"),
    LION("🦁", "Lion"),
    KOALA("🐨", "Koala"),
    DOLPHIN("🐬", "Dolphin"),
    TIGER("🐯", "Tiger"),
    PANDA("🐼", "Panda"),
    ASTRO("🚀", "Astro"),
    ;

    companion object {
        val Default: AvatarPreset = RABBIT
    }
}
