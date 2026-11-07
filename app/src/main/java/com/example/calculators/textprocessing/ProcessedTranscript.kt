package com.example.calculators.textprocessing

data class ProcessedTranscript(
    val originalTranscript: String = "",
    val cleanedTranscript: String = "",
    val normalizedTranscript: String = "",
    val detectedLanguage: DetectedLanguage = DetectedLanguage.UNKNOWN,
    val tokens: List<TranscriptToken> = emptyList(),
    val normalizedNumbers: List<NormalizedNumberToken> = emptyList(),
    val preservedProductWords: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = originalTranscript.isBlank()

    val hasProcessedData: Boolean
        get() = tokens.isNotEmpty() || warnings.isNotEmpty()

    val formattedNormalizedNumbers: String
        get() = normalizedNumbers.joinToString(", ") { it.normalizedValue }

    val formattedProductWords: String
        get() = preservedProductWords.joinToString(", ")
}
