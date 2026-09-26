package com.meritscreen.feature.child.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Lifecycle-safe, offline-only TextToSpeech voice narrator for Early Learner audio
 * prompts and the 30-second locked teaching screen (docs 06 & 08).
 * Never makes synchronous network calls.
 */
class QuizTtsNarrator(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(0.85f) // Gentle, slower cadence for children
                tts?.setPitch(1.05f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Suppress("DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
                _isReady.value = true
            }
        }
    }

    fun speak(text: String, languageTag: String = "en") {
        if (!_isReady.value || text.isBlank()) return
        val currentTts = tts ?: return

        try {
            val locale = Locale.forLanguageTag(languageTag)
            val langResult = currentTts.setLanguage(locale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                currentTts.language = Locale.ENGLISH
            }
        } catch (_: Throwable) {
            currentTts.language = Locale.ENGLISH
        }

        val utteranceId = UUID.randomUUID().toString()
        currentTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Throwable) {}
        _isSpeaking.value = false
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Throwable) {}
        tts = null
        _isReady.value = false
    }
}
