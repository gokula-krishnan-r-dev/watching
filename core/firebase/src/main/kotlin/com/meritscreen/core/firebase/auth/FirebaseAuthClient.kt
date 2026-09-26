package com.meritscreen.core.firebase.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorException
import com.meritscreen.core.firebase.error.FirebaseErrorMapper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthClient @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val dispatchers: AppDispatchers,
) : AuthClient {

    @Volatile
    private var cachedUser: AuthUser? = firebaseAuth.currentUser?.toAuthUser(emptyMap())

    override val currentUser: AuthUser?
        get() = cachedUser

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user == null) {
                cachedUser = null
                trySend(null)
                return@AuthStateListener
            }
            launch {
                try {
                    trySend(mapUser(user))
                } catch (_: Exception) {
                    trySend(user.toAuthUser(emptyMap()).also { cachedUser = it })
                }
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun refreshCurrentUser(): AuthUser? = runAuth {
        val user = firebaseAuth.currentUser
        if (user == null) {
            cachedUser = null
            null
        } else {
            mapUser(user)
        }
    }

    override suspend fun ensureIdToken(forceRefresh: Boolean) {
        runAuth {
            val user = firebaseAuth.currentUser ?: throw AppErrorException(AppError.Auth())
            user.getIdToken(forceRefresh).await()
            mapUser(user)
            Unit
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): AuthUser =
        runAuth {
            val result = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user.toRequiredUser()
            firebaseAuth.currentUser?.getIdToken(true)?.await()
            user
        }

    override suspend fun signInWithEmail(email: String, password: String): AuthUser =
        runAuth {
            val result = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user.toRequiredUser()
            firebaseAuth.currentUser?.getIdToken(true)?.await()
            user
        }

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthUser =
        runAuth {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user.toRequiredUser()
            firebaseAuth.currentUser?.getIdToken(true)?.await()
            user
        }

    override suspend fun signInWithCustomToken(customToken: String): AuthUser =
        runAuth {
            val result = firebaseAuth.signInWithCustomToken(customToken).await()
            val user = result.user.toRequiredUser()
            firebaseAuth.currentUser?.getIdToken(true)?.await()
            user
        }

    override suspend fun sendPasswordReset(email: String) {
        runAuth { firebaseAuth.sendPasswordResetEmail(email.trim()).await() }
    }

    override suspend fun sendEmailVerification() {
        runAuth {
            val user = firebaseAuth.currentUser ?: throw AppErrorException(AppError.Auth())
            user.sendEmailVerification().await()
        }
    }

    override suspend fun signOut() {
        withContext(dispatchers.io) {
            firebaseAuth.signOut()
            cachedUser = null
        }
    }

    private suspend fun <T> runAuth(block: suspend () -> T): T = try {
        withContext(dispatchers.io) { block() }
    } catch (error: AppErrorException) {
        throw error
    } catch (error: Throwable) {
        throw AppErrorException(FirebaseErrorMapper.from(error), error)
    }

    private suspend fun FirebaseUser?.toRequiredUser(): AuthUser {
        val firebaseUser = this ?: throw AppErrorException(AppError.Auth())
        return mapUser(firebaseUser)
    }

    private suspend fun mapUser(firebaseUser: FirebaseUser): AuthUser {
        val claims = try {
            firebaseUser.getIdToken(false).await().claims
        } catch (_: Exception) {
            emptyMap()
        }
        return firebaseUser.toAuthUser(claims).also { cachedUser = it }
    }

    private fun FirebaseUser.toAuthUser(claims: Map<String, Any>): AuthUser {
        val role = claims["role"] as? String
        val isChild = role == CHILD_DEVICE_ROLE
        return AuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            isEmailVerified = isEmailVerified,
            // Prefer the explicit custom-claim role. The `dev_` uid prefix is only a
            // defensive fallback for older tokens minted before claims were populated.
            isChildDevice = isChild || (role == null && uid.startsWith(CHILD_DEVICE_UID_PREFIX)),
            familyId = claims["familyId"] as? String,
            childId = claims["childId"] as? String,
            deviceId = claims["deviceId"] as? String,
        )
    }

    private companion object {
        const val CHILD_DEVICE_ROLE = "child_device"
        const val CHILD_DEVICE_UID_PREFIX = "dev_"
    }
}
