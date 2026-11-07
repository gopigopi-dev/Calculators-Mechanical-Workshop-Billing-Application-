package com.example.calculators.textprocessing

enum class FieldExtractionStatus {
    CONFIDENT,
    FUZZY_MATCH,
    USER_DEFINED,
    OPTIONAL_OMITTED,
    MISSING,
    AMBIGUOUS
}

data class ExtractedQuantity(
    val originalExpression: String = "",
    val value: Double? = null,
    val status: FieldExtractionStatus = FieldExtractionStatus.OPTIONAL_OMITTED
) {
    companion object {
        fun omitted(): ExtractedQuantity = ExtractedQuantity(
            originalExpression = "",
            value = null,
            status = FieldExtractionStatus.OPTIONAL_OMITTED
        )
    }
}

/**
 * Extracts optional part/calibration quantities from numeric digits, English number words,
 * Tamil number words, and combined expressions.
 *
 * Rules:
 * - Quantity is optional; when not provided, value remains null (never invented or defaulted to 1).
 */
object QuantityExtractor {

    fun extractQuantity(numberToken: NormalizedNumberToken?): ExtractedQuantity {
        if (numberToken == null) {
            return ExtractedQuantity.omitted()
        }

        val doubleVal = numberToken.numericValue.toDouble()
        if (doubleVal.isNaN() || doubleVal.isInfinite() || doubleVal <= 0.0) {
            return ExtractedQuantity(
                originalExpression = numberToken.originalExpression,
                value = null,
                status = FieldExtractionStatus.AMBIGUOUS
            )
        }

        return ExtractedQuantity(
            originalExpression = numberToken.originalExpression,
            value = doubleVal,
            status = FieldExtractionStatus.CONFIDENT
        )
    }

    /**
     * Standalone utility to extract candidate quantities from raw text for unit testing.
     */
    fun extractFromText(text: String): List<ExtractedQuantity> {
        val processed = TextNormalizer.processTranscript(text)
        return processed.normalizedNumbers.map { extractQuantity(it) }
    }
}
