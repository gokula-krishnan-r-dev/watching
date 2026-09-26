package com.meritscreen.core.common.domain

import kotlinx.serialization.Serializable

@Serializable
data class NurseryVideo(
    val videoId: String,
    val title: String,
    val maxSeconds: Int = 180,
    val channel: String = "",
)

@Serializable
data class NurseryPlaylist(
    val id: String,
    val title: String,
    val category: String,
    val videos: List<NurseryVideo>,
)

@Serializable
data class NurseryTeachItem(
    val id: String,
    val tag: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val speechText: String,
    val colorHex: String,
    val count: Int? = null,
    val soundEffect: String? = null,
)

@Serializable
data class NurseryTrack(
    val id: String,
    val title: String,
    val category: String,
    val items: List<NurseryTeachItem>,
)

@Serializable
data class NurseryCurriculum(
    val version: Int = 1,
    val updatedAtEpochMs: Long = 0L,
    val playlists: List<NurseryPlaylist> = emptyList(),
    val tracks: List<NurseryTrack> = emptyList(),
)
