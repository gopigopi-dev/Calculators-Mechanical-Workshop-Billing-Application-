package com.example.calculators.voice

import androidx.annotation.StringRes
import com.example.R
import com.example.calculators.textprocessing.ParsingResult
import com.example.calculators.textprocessing.ProcessedTranscript

enum class RecognitionLanguage(
    val localeTag: String,
    @param:StringRes val labelResId: Int
) {
    ENGLISH_IN("en-IN", R.string.voice_language_english),
    TAMIL_IN("ta-IN", R.string.voice_language_tamil)
}

enum class VoiceListeningStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    SUCCESS,
    ERROR
}

enum class SpeechErrorType(@param:StringRes val messageResId: Int) {
    PERMISSION_DENIED(R.string.voice_error_permission_denied),
    NO_SPEECH_DETECTED(R.string.voice_error_no_speech),
    NETWORK_OR_SERVICE_UNAVAILABLE(R.string.voice_error_network_unavailable),
    RECOGNITION_TIMEOUT(R.string.voice_error_timeout),
    UNSUPPORTED_LANGUAGE(R.string.voice_error_unsupported_language),
    INITIALIZATION_FAILURE(R.string.voice_error_init_failed),
    GENERIC_ERROR(R.string.voice_error_generic)
}

data class SpeechRecognitionState(
    val status: VoiceListeningStatus = VoiceListeningStatus.IDLE,
    val selectedLanguage: RecognitionLanguage = RecognitionLanguage.ENGLISH_IN,
    val originalTranscript: String = "",
    val transcript: String = "",
    val partialTranscript: String = "",
    val processedTranscript: ProcessedTranscript = ProcessedTranscript(),
    val parsingResult: ParsingResult = ParsingResult(),
    val errorType: SpeechErrorType? = null,
    val isVoicePanelVisible: Boolean = false
) {
    val isListening: Boolean
        get() = status == VoiceListeningStatus.LISTENING

    val isProcessing: Boolean
        get() = status == VoiceListeningStatus.PROCESSING
}
