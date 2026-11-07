package com.example.calculators.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

interface SpeechRecognizerGateway {
    fun isRecognitionAvailable(): Boolean
    fun setRecognitionListener(listener: RecognitionListener)
    fun startListening(recognizerIntent: Intent)
    fun stopListening()
    fun cancel()
    fun destroy()
}

class AndroidSpeechRecognizerGateway(
    private val context: Context
) : SpeechRecognizerGateway {

    private var speechRecognizer: SpeechRecognizer? = null

    private fun ensureInstance(): SpeechRecognizer {
        val current = speechRecognizer
        if (current != null) return current
        val created = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        speechRecognizer = created
        return created
    }

    override fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context.applicationContext)
    }

    override fun setRecognitionListener(listener: RecognitionListener) {
        ensureInstance().setRecognitionListener(listener)
    }

    override fun startListening(recognizerIntent: Intent) {
        ensureInstance().startListening(recognizerIntent)
    }

    override fun stopListening() {
        speechRecognizer?.stopListening()
    }

    override fun cancel() {
        speechRecognizer?.cancel()
    }

    override fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

class SpeechRecognitionManager(
    private val gateway: SpeechRecognizerGateway,
    private val onStatusChanged: (VoiceListeningStatus) -> Unit,
    private val onPartialTranscript: (String) -> Unit,
    private val onFinalTranscript: (String) -> Unit,
    private val onError: (SpeechErrorType) -> Unit
) {

    constructor(
        context: Context,
        onStatusChanged: (VoiceListeningStatus) -> Unit,
        onPartialTranscript: (String) -> Unit,
        onFinalTranscript: (String) -> Unit,
        onError: (SpeechErrorType) -> Unit
    ) : this(
        gateway = AndroidSpeechRecognizerGateway(context),
        onStatusChanged = onStatusChanged,
        onPartialTranscript = onPartialTranscript,
        onFinalTranscript = onFinalTranscript,
        onError = onError
    )

    private var isSessionActive: Boolean = false

    private val recognitionListener = SpeechRecognitionListener(
        onListeningStarted = {
            isSessionActive = true
            onStatusChanged(VoiceListeningStatus.LISTENING)
        },
        onSpeechProcessing = {
            onStatusChanged(VoiceListeningStatus.PROCESSING)
        },
        onPartialResult = { partialText ->
            onPartialTranscript(partialText)
        },
        onFinalResult = { recognizedText ->
            isSessionActive = false
            onFinalTranscript(recognizedText)
        },
        onRecognitionError = { errorType ->
            isSessionActive = false
            onError(errorType)
        }
    )

    /**
     * Starts listening for speech in the selected language (English or Tamil).
     * Prevents multiple simultaneous recognition sessions.
     */
    fun startListening(language: RecognitionLanguage): Boolean {
        if (isSessionActive) {
            return false
        }

        if (!gateway.isRecognitionAvailable()) {
            onError(SpeechErrorType.INITIALIZATION_FAILURE)
            return false
        }

        return try {
            isSessionActive = true
            onStatusChanged(VoiceListeningStatus.LISTENING)
            gateway.setRecognitionListener(recognitionListener)
            val intent = buildRecognizerIntent(language)
            gateway.startListening(intent)
            true
        } catch (e: Exception) {
            isSessionActive = false
            onError(SpeechErrorType.INITIALIZATION_FAILURE)
            false
        }
    }

    /**
     * Stops active listening session and allows the speech recognizer to finalize results.
     */
    fun stopListening() {
        if (!isSessionActive) return
        try {
            gateway.stopListening()
            isSessionActive = false
            onStatusChanged(VoiceListeningStatus.IDLE)
        } catch (e: Exception) {
            isSessionActive = false
            onStatusChanged(VoiceListeningStatus.IDLE)
        }
    }

    /**
     * Cancels active recognition immediately and releases native SpeechRecognizer resources.
     */
    fun release() {
        isSessionActive = false
        try {
            gateway.cancel()
            gateway.destroy()
        } catch (_: Exception) {
            // Ignore cleanup exceptions on destroy
        }
    }

    companion object {
        fun buildRecognizerIntent(language: RecognitionLanguage): Intent {
            return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.localeTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, language.localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
        }
    }
}
