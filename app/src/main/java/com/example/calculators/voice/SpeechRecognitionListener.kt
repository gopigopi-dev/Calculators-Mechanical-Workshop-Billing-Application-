package com.example.calculators.voice

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

class SpeechRecognitionListener(
    private val onListeningStarted: () -> Unit,
    private val onSpeechProcessing: () -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onRecognitionError: (SpeechErrorType) -> Unit
) : RecognitionListener {

    override fun onReadyForSpeech(params: Bundle?) {
        onListeningStarted()
    }

    override fun onBeginningOfSpeech() {
        onListeningStarted()
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Optional audio level callback
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Audio buffer callback
    }

    override fun onEndOfSpeech() {
        onSpeechProcessing()
    }

    override fun onError(error: Int) {
        val mappedError = mapSpeechRecognizerError(error)
        onRecognitionError(mappedError)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        // Preserve recognized Tamil or English text verbatim without automatic translation
        val recognizedText = matches
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()

        if (recognizedText.isNotEmpty()) {
            onFinalResult(recognizedText)
        } else {
            onRecognitionError(SpeechErrorType.NO_SPEECH_DETECTED)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val partialText = partialMatches
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
        if (partialText.isNotEmpty()) {
            onPartialResult(partialText)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        // Standard RecognitionListener event callback
    }

    companion object {
        // Error code constants for Android 12+ language support (API 31+)
        private const val ERROR_LANGUAGE_NOT_SUPPORTED_CODE = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE_CODE = 13

        fun mapSpeechRecognizerError(errorCode: Int): SpeechErrorType {
            return when (errorCode) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    SpeechErrorType.PERMISSION_DENIED

                SpeechRecognizer.ERROR_NO_MATCH ->
                    SpeechErrorType.NO_SPEECH_DETECTED

                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    SpeechErrorType.RECOGNITION_TIMEOUT

                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    SpeechErrorType.NETWORK_OR_SERVICE_UNAVAILABLE

                ERROR_LANGUAGE_NOT_SUPPORTED_CODE,
                ERROR_LANGUAGE_UNAVAILABLE_CODE ->
                    SpeechErrorType.UNSUPPORTED_LANGUAGE

                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_AUDIO ->
                    SpeechErrorType.INITIALIZATION_FAILURE

                else ->
                    SpeechErrorType.GENERIC_ERROR
            }
        }
    }
}
