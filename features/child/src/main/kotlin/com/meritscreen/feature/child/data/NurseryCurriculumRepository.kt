package com.meritscreen.feature.child.data

import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.domain.NurseryCurriculum
import com.meritscreen.core.common.domain.NurseryPlaylist
import com.meritscreen.core.common.domain.NurseryStaticCatalog
import com.meritscreen.core.common.domain.NurseryTeachItem
import com.meritscreen.core.common.domain.NurseryTrack
import com.meritscreen.core.common.domain.NurseryVideo
import com.meritscreen.core.common.logging.AppLogger
import com.meritscreen.core.firebase.curriculum.NurseryCurriculumRemoteClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the Nursery / Early-Learner Curriculum (ages 3–6).
 * Backed by static local catalog for guaranteed offline operation, and synced in real-time
 * with Firebase Firestore DB so changes are immediately visible to all children.
 */
@Singleton
class NurseryCurriculumRepository @Inject constructor(
    private val remoteClient: NurseryCurriculumRemoteClient,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val _curriculum = MutableStateFlow(NurseryStaticCatalog.DEFAULT_CURRICULUM)
    val curriculum: StateFlow<NurseryCurriculum> = _curriculum.asStateFlow()

    init {
        startRealtimeSync()
    }

    private fun startRealtimeSync() {
        repositoryScope.launch {
            // First check if Firestore has existing data or needs initial seeding
            runCatching {
                val remote = remoteClient.fetchCurriculum()
                if (remote != null && remote.version >= NurseryStaticCatalog.DEFAULT_CURRICULUM.version && remote.playlists.isNotEmpty()) {
                    _curriculum.value = remote
                    logger.i("Loaded Nursery Curriculum from Firebase DB (version ${remote.version})")
                } else {
                    // Seed or update Firestore with default static catalog so admin/console has verified active videos
                    remoteClient.saveCurriculum(NurseryStaticCatalog.DEFAULT_CURRICULUM)
                    _curriculum.value = NurseryStaticCatalog.DEFAULT_CURRICULUM
                    logger.i("Seeded/Updated initial Nursery Curriculum to Firebase DB")
                }
            }.onFailure { error ->
                logger.w("Failed to fetch/seed initial Nursery Curriculum, using static catalog", error)
            }

            // Real-time snapshot listener: updates instantly whenever Firestore DB changes!
            remoteClient.observeCurriculum()
                .catch { e -> logger.w("Error in Nursery Curriculum real-time stream", e) }
                .collect { updated ->
                    if (updated != null && updated.playlists.isNotEmpty()) {
                        _curriculum.value = updated
                        logger.i("Real-time Nursery Curriculum update applied (version ${updated.version})")
                    }
                }
        }
    }

    fun getCurrentCurriculum(): NurseryCurriculum = _curriculum.value

    fun getPlaylist(playlistId: String): NurseryPlaylist? {
        val all = _curriculum.value.playlists
        return all.firstOrNull { it.id == playlistId } ?: all.firstOrNull()
    }

    private val recentVideoIds = mutableListOf<String>()
    private var lastCategory: String? = null

    /**
     * Picks a random, diverse nursery video across curriculum playlists
     * (ABC Phonics, Counting/Numbers, Animals & Sounds, Colors & Shapes, Nursery Rhymes).
     *
     * Prevents immediate repetition by maintaining a recent-played queue and alternating
     * learning categories so young children (3-6) receive well-rounded educational variety.
     */
    fun pickDiverseVideo(
        preferredPlaylistId: String? = null,
        currentVideoId: String? = null,
        excludeVideoIds: Set<String> = emptySet(),
    ): NurseryVideo? {
        val allPlaylists = _curriculum.value.playlists
        if (allPlaylists.isEmpty()) return null

        val isDiverseCurriculum = preferredPlaylistId.isNullOrBlank() ||
            preferredPlaylistId == "all" ||
            preferredPlaylistId == "abc_phonics" // Default fallback from policy treated as full curriculum

        val videoWithCategory: List<Pair<NurseryVideo, String>> = if (!isDiverseCurriculum) {
            val pl = allPlaylists.firstOrNull { it.id == preferredPlaylistId }
            if (pl != null && pl.videos.isNotEmpty()) {
                pl.videos.map { it to pl.category }
            } else {
                allPlaylists.flatMap { p -> p.videos.map { it to p.category } }
            }
        } else {
            allPlaylists.flatMap { p -> p.videos.map { it to p.category } }
        }

        if (videoWithCategory.isEmpty()) return null

        val exclusions = excludeVideoIds.toMutableSet()
        if (currentVideoId != null) {
            exclusions.add(currentVideoId)
        }

        // Filter out recently played videos to guarantee fresh variety
        val unplayed = videoWithCategory.filter { (vid, _) -> vid.videoId !in exclusions && vid.videoId !in recentVideoIds }
        val fallbackAvailable = videoWithCategory.filter { (vid, _) -> vid.videoId !in exclusions }

        val pool = when {
            unplayed.isNotEmpty() -> unplayed
            fallbackAvailable.isNotEmpty() -> fallbackAvailable
            else -> videoWithCategory
        }

        // Prioritize a category different from the last played video (e.g. Numbers or Animals after ABC)
        val differentCategoryPool = if (lastCategory != null && isDiverseCurriculum) {
            val different = pool.filter { (_, cat) -> cat != lastCategory }
            if (different.isNotEmpty()) different else pool
        } else {
            pool
        }

        val chosenPair = differentCategoryPool.randomOrNull() ?: pool.randomOrNull() ?: videoWithCategory.first()
        recordPlayedVideo(chosenPair.first, chosenPair.second)
        return chosenPair.first
    }

    private fun recordPlayedVideo(video: NurseryVideo, category: String) {
        lastCategory = category
        recentVideoIds.remove(video.videoId)
        recentVideoIds.add(video.videoId)
        // Keep last 8 videos in history so children never see immediate repeats
        while (recentVideoIds.size > 8) {
            recentVideoIds.removeAt(0)
        }
    }

    fun pickNextVideo(playlistId: String, currentVideoId: String? = null): NurseryVideo? {
        return pickDiverseVideo(
            preferredPlaylistId = playlistId,
            currentVideoId = currentVideoId,
            excludeVideoIds = if (currentVideoId != null) setOf(currentVideoId) else emptySet(),
        )
    }

    fun getTrack(trackId: String): NurseryTrack? {
        val all = _curriculum.value.tracks
        return all.firstOrNull { it.id == trackId } ?: all.firstOrNull()
    }

    fun getTrackItems(trackId: String): List<NurseryTeachItem> {
        return getTrack(trackId)?.items ?: emptyList()
    }

    fun pickDiverseTeachItems(
        preferredTrackId: String? = null,
        count: Int = 3,
        usedTags: Set<String> = emptySet(),
    ): List<NurseryTeachItem> {
        val allTracks = _curriculum.value.tracks
        if (allTracks.isEmpty()) return emptyList()

        val track = if (!preferredTrackId.isNullOrBlank() && preferredTrackId != "all") {
            getTrack(preferredTrackId) ?: allTracks.random()
        } else {
            allTracks.random()
        }
        val items = track.items
        if (items.isEmpty()) return emptyList()
        val unused = items.filter { it.tag !in usedTags }
        val candidates = if (unused.size >= count) unused else items
        return candidates.shuffled().take(count)
    }

    fun pickNextTeachItems(trackId: String, count: Int = 3, usedTags: Set<String> = emptySet()): List<NurseryTeachItem> {
        val targetTrackId = if (trackId == "all" || trackId.isBlank()) null else trackId
        return pickDiverseTeachItems(preferredTrackId = targetTrackId, count = count, usedTags = usedTags)
    }
}
