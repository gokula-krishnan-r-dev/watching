package com.meritscreen.core.firebase.auth

import kotlinx.coroutines.flow.Flow

interface AuthClient {
    val currentUser: AuthUser?
    val authState: Flow<AuthUser?>

    /** Reloads the Firebase user + custom claims into [currentUser]. Used on cold start. */
    suspend fun refreshCurrentUser(): AuthUser?

    /**
     * Forces a fresh ID token so the next Firestore/Functions call carries Auth.
     * Call after sign-up/sign-in before the first privileged query.
     */
    suspend fun ensureIdToken(forceRefresh: Boolean = true)

    suspend fun signUpWithEmail(email: String, password: String): AuthUser
    suspend fun signInWithEmail(email: String, password: String): AuthUser
    suspend fun signInWithGoogleIdToken(idToken: String): AuthUser
    suspend fun signInWithCustomToken(customToken: String): AuthUser
    suspend fun sendPasswordReset(email: String)
    suspend fun sendEmailVerification()
    suspend fun signOut()
}
