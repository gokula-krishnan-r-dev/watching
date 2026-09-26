package com.meritscreen.feature.onboarding.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.meritscreen.feature.onboarding.domain.OnboardingDraft
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * JSON-backed [Serializer] for the typed onboarding-draft DataStore. On corruption we fall
 * back to an empty draft rather than crashing — losing an in-progress, not-yet-submitted
 * first-run form is acceptable; crashing on every app start is not.
 */
object OnboardingDraftSerializer : Serializer<OnboardingDraft> {

    override val defaultValue: OnboardingDraft = OnboardingDraft()

    override suspend fun readFrom(input: InputStream): OnboardingDraft {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            Json.decodeFromString(OnboardingDraft.serializer(), bytes.decodeToString())
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read onboarding draft", exception)
        }
    }

    override suspend fun writeTo(t: OnboardingDraft, output: OutputStream) {
        val json = Json.encodeToString(OnboardingDraft.serializer(), t)
        output.write(json.encodeToByteArray())
    }
}
