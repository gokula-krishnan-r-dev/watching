package com.meritscreen.core.firebase.family

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.common.domain.ChildPolicy
import com.meritscreen.core.common.domain.ChildPolicyMapper
import com.meritscreen.core.common.domain.CurriculumFocusCatalog
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.firebase.error.FirebaseErrorMapper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreFamilyStore @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val dispatchers: AppDispatchers,
) : FamilyStore {

    override suspend fun getUserFamilyId(uid: String): String? = runStore {
        // One-shot get (not a long-lived listener). Missing docs are a normal new-parent path.
        val snapshot = firestore.collection(USERS).document(uid).get().await()
        if (!snapshot.exists()) return@runStore null
        snapshot.getString("familyId")?.takeIf { it.isNotBlank() }
    }

    override suspend fun listChildren(familyId: String): List<FamilyChildProfile> = runStore {
        val snapshot = firestore.collection(FAMILIES).document(familyId)
            .collection(CHILDREN)
            .get()
            .await()
        snapshot.documents.mapNotNull { doc ->
            val name = doc.getString("displayName") ?: return@mapNotNull null
            FamilyChildProfile(
                childId = doc.id,
                displayName = name,
                ageBand = parseAgeBand(doc.getString("ageBand")),
                avatar = parseAvatar(doc.getString("avatarId")),
                language = doc.getString("language") ?: "en",
            )
        }
    }

    override suspend fun createFamilyFromDraft(
        uid: String,
        email: String?,
        draft: FamilyDraft,
    ): CreatedFamily = runStore {
        val familyId = UUID.randomUUID().toString()
        val children = draft.children.map { child ->
            FamilyChildProfile(
                childId = child.localId.ifBlank { UUID.randomUUID().toString() },
                displayName = child.name,
                ageBand = child.ageBand,
                avatar = child.avatar,
                language = child.language,
            )
        }
        val focusByChildId = draft.children
            .mapIndexed { index, draftChild ->
                children[index].childId to CurriculumFocusCatalog.reconcile(
                    draftChild.ageBand,
                    draftChild.curriculumFocusIds,
                )
            }
            .toMap()
        val batch = firestore.batch()
        val familyRef = firestore.collection(FAMILIES).document(familyId)
        batch.set(
            familyRef,
            mapOf(
                "ownerUid" to uid,
                "name" to draft.familyName,
                "parentPinHash" to draft.parentPinHash,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            familyRef.collection("members").document(uid),
            mapOf(
                "role" to "owner",
                "joinedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            firestore.collection(USERS).document(uid),
            mapOf(
                "familyId" to familyId,
                "email" to (email ?: ""),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        )
        children.forEach { child ->
            val childRef = familyRef.collection(CHILDREN).document(child.childId)
            batch.set(
                childRef,
                mapOf(
                    "displayName" to child.displayName,
                    "ageBand" to child.ageBand.name,
                    "avatarId" to child.avatar.name,
                    "language" to child.language,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.set(
                childRef.collection("policy").document("current"),
                ChildPolicyMapper.toMap(
                    ChildPolicy(
                        curriculumFocusIds = focusByChildId[child.childId].orEmpty(),
                    ),
                ) + ("updatedAt" to FieldValue.serverTimestamp()),
            )
        }
        batch.commit().await()
        CreatedFamily(familyId = familyId, children = children)
    }

    override suspend fun updateParentPinHash(familyId: String, pinHash: String) {
        runStore {
            firestore.collection(FAMILIES).document(familyId)
                .update(
                    mapOf(
                        "parentPinHash" to pinHash,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                .await()
        }
    }

    override fun observeDeviceCount(familyId: String, childId: String): Flow<Int> =
        callbackFlow {
            val registration = firestore.collection(FAMILIES).document(familyId)
                .collection(CHILDREN).document(childId)
                .collection("devices")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val count = snapshot?.documents?.count { doc ->
                        doc.getBoolean("revoked") != true
                    } ?: 0
                    trySend(count)
                }
            awaitClose { registration.remove() }
        }.flowOn(dispatchers.io)

    private suspend fun <T> runStore(block: suspend () -> T): T = try {
        withContext(dispatchers.io) { block() }
    } catch (error: AppErrorException) {
        throw error
    } catch (error: Throwable) {
        throw AppErrorException(FirebaseErrorMapper.from(error), error)
    }

    private fun parseAgeBand(raw: String?): AgeBand =
        AgeBand.entries.firstOrNull { it.name == raw } ?: AgeBand.AGE_7_TO_9

    private fun parseAvatar(raw: String?): AvatarPreset =
        AvatarPreset.entries.firstOrNull { it.name == raw } ?: AvatarPreset.Default

    private companion object {
        const val USERS = "users"
        const val FAMILIES = "families"
        const val CHILDREN = "children"
    }
}
