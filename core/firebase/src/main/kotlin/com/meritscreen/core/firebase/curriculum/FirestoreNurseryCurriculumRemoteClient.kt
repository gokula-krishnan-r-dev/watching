package com.meritscreen.core.firebase.curriculum

import com.google.firebase.firestore.FirebaseFirestore
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.domain.NurseryCurriculum
import com.meritscreen.core.common.domain.NurseryPlaylist
import com.meritscreen.core.common.domain.NurseryTeachItem
import com.meritscreen.core.common.domain.NurseryTrack
import com.meritscreen.core.common.domain.NurseryVideo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreNurseryCurriculumRemoteClient @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val dispatchers: AppDispatchers,
) : NurseryCurriculumRemoteClient {

    private fun curriculumRef() =
        firestore.collection("curriculum").document("nursery")

    override fun observeCurriculum(): Flow<NurseryCurriculum?> = callbackFlow {
        val listener = curriculumRef().addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data
                if (data != null) {
                    trySend(mapToCurriculum(data))
                } else {
                    trySend(null)
                }
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }.flowOn(dispatchers.io)

    override suspend fun fetchCurriculum(): NurseryCurriculum? = withContext(dispatchers.io) {
        val snap = curriculumRef().get().await()
        if (!snap.exists() || snap.data == null) null else mapToCurriculum(snap.data!!)
    }

    override suspend fun saveCurriculum(curriculum: NurseryCurriculum): Unit = withContext(dispatchers.io) {
        curriculumRef().set(curriculumToMap(curriculum)).await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToCurriculum(data: Map<String, Any?>): NurseryCurriculum {
        val version = (data["version"] as? Number)?.toInt() ?: 1
        val updatedAtEpochMs = (data["updatedAtEpochMs"] as? Number)?.toLong() ?: 0L

        val rawPlaylists = (data["playlists"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        val playlists = rawPlaylists.map { p ->
            val rawVideos = (p["videos"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val videos = rawVideos.map { v ->
                NurseryVideo(
                    videoId = v["videoId"] as? String ?: "",
                    title = v["title"] as? String ?: "",
                    maxSeconds = (v["maxSeconds"] as? Number)?.toInt() ?: 180,
                    channel = v["channel"] as? String ?: "",
                )
            }.filter { it.videoId.isNotBlank() }

            NurseryPlaylist(
                id = p["id"] as? String ?: "",
                title = p["title"] as? String ?: "",
                category = p["category"] as? String ?: "alphabet",
                videos = videos,
            )
        }.filter { it.id.isNotBlank() }

        val rawTracks = (data["tracks"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        val tracks = rawTracks.map { t ->
            val rawItems = (t["items"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
            val items = rawItems.map { i ->
                NurseryTeachItem(
                    id = i["id"] as? String ?: "",
                    tag = i["tag"] as? String ?: "",
                    title = i["title"] as? String ?: "",
                    subtitle = i["subtitle"] as? String ?: "",
                    emoji = i["emoji"] as? String ?: "🌟",
                    speechText = i["speechText"] as? String ?: "",
                    colorHex = i["colorHex"] as? String ?: "#FFFFFF",
                    count = (i["count"] as? Number)?.toInt(),
                    soundEffect = i["soundEffect"] as? String,
                )
            }.filter { it.id.isNotBlank() }

            NurseryTrack(
                id = t["id"] as? String ?: "",
                title = t["title"] as? String ?: "",
                category = t["category"] as? String ?: "alphabet",
                items = items,
            )
        }.filter { it.id.isNotBlank() }

        return NurseryCurriculum(
            version = version,
            updatedAtEpochMs = updatedAtEpochMs,
            playlists = playlists,
            tracks = tracks,
        )
    }

    private fun curriculumToMap(curriculum: NurseryCurriculum): Map<String, Any?> = mapOf(
        "version" to curriculum.version,
        "updatedAtEpochMs" to curriculum.updatedAtEpochMs,
        "playlists" to curriculum.playlists.map { p ->
            mapOf(
                "id" to p.id,
                "title" to p.title,
                "category" to p.category,
                "videos" to p.videos.map { v ->
                    mapOf(
                        "videoId" to v.videoId,
                        "title" to v.title,
                        "maxSeconds" to v.maxSeconds,
                        "channel" to v.channel,
                    )
                },
            )
        },
        "tracks" to curriculum.tracks.map { t ->
            mapOf(
                "id" to t.id,
                "title" to t.title,
                "category" to t.category,
                "items" to t.items.map { i ->
                    mapOf(
                        "id" to i.id,
                        "tag" to i.tag,
                        "title" to i.title,
                        "subtitle" to i.subtitle,
                        "emoji" to i.emoji,
                        "speechText" to i.speechText,
                        "colorHex" to i.colorHex,
                        "count" to i.count,
                        "soundEffect" to i.soundEffect,
                    )
                },
            )
        },
    )
}
