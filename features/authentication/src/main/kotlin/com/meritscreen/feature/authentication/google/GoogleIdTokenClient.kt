package com.meritscreen.feature.authentication.google

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.result.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

class GoogleSignInCancelled : Exception()

@Singleton
class GoogleIdTokenClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isConfigured(): Boolean = webClientId() != null

    suspend fun getIdToken(activity: Activity): Outcome<String> {
        val serverClientId = webClientId()
            ?: return Outcome.Failure(
                AppError.Validation("Google sign-in isn't configured for this build yet."),
            )
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                Outcome.Success(google.idToken)
            } else {
                Outcome.Failure(AppError.Auth("Google sign-in didn't complete. Try email instead."))
            }
        } catch (_: GetCredentialCancellationException) {
            throw GoogleSignInCancelled()
        } catch (_: NoCredentialException) {
            Outcome.Failure(AppError.Auth("No Google account is available on this device."))
        } catch (error: GoogleSignInCancelled) {
            throw error
        } catch (_: Throwable) {
            Outcome.Failure(AppError.Auth("Google sign-in didn't complete. Try email instead."))
        }
    }

    private fun webClientId(): String? {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName,
        )
        if (resourceId == 0) return null
        val value = context.getString(resourceId).trim()
        return value.takeIf { it.isNotEmpty() && !it.contains("YOUR_", ignoreCase = true) }
    }
}
