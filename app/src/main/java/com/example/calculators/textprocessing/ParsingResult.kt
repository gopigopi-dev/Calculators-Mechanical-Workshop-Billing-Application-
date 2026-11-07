package com.example.calculators.textprocessing

data class ParsingResult(
    val originalTranscript: String = "",
    val processedTranscript: ProcessedTranscript = ProcessedTranscript(),
    val parsedProducts: List<ParsedProduct> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val confidentProducts: List<ParsedProduct>
        get() = parsedProducts.filter { it.isReadyForAutoInsert }

    val uncertainProducts: List<ParsedProduct>
        get() = parsedProducts.filter { !it.isReadyForAutoInsert }

    val hasUncertainProducts: Boolean
        get() = uncertainProducts.isNotEmpty()

    val transcriptSignature: String
        get() = processedTranscript.normalizedTranscript.trim().lowercase()
}
