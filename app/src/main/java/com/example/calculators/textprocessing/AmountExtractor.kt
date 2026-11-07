package com.example.calculators.textprocessing

import java.math.BigDecimal
import java.math.RoundingMode

data class ExtractedAmount(
    val originalExpression: String = "",
    val value: BigDecimal? = null,
    val isUnitPrice: Boolean = false,
    val status: FieldExtractionStatus = FieldExtractionStatus.MISSING
)

/**
 * Phase 7: Extracts monetary amounts into BigDecimal from numeric values,
 * English number words, Tamil number words, decimal values, and spoken amount expressions.
 *
 * Rules:
 * - By default, the extracted Amount represents the final line amount for that product.
 * - Does NOT multiply Quantity * Amount automatically unless isUnitPrice is explicitly true.
 * - Never invents missing amounts.
 */
object AmountExtractor {

    fun extractAmount(
        numberToken: NormalizedNumberToken?,
        isExplicitUnitPrice: Boolean = false
    ): ExtractedAmount {
        if (numberToken == null) {
            return ExtractedAmount(
                originalExpression = "",
                value = null,
                isUnitPrice = isExplicitUnitPrice,
                status = FieldExtractionStatus.MISSING
            )
        }

        val bd = numberToken.numericValue
        if (bd < BigDecimal.ZERO) {
            return ExtractedAmount(
                originalExpression = numberToken.originalExpression,
                value = null,
                isUnitPrice = isExplicitUnitPrice,
                status = FieldExtractionStatus.AMBIGUOUS
            )
        }

        return ExtractedAmount(
            originalExpression = numberToken.originalExpression,
            value = bd.setScale(2, RoundingMode.HALF_UP),
            isUnitPrice = isExplicitUnitPrice,
            status = FieldExtractionStatus.CONFIDENT
        )
    }

    /**
     * Standalone utility to extract normalized monetary amounts from raw text for unit testing.
     */
    fun extractFromText(text: String): List<ExtractedAmount> {
        val processed = TextNormalizer.processTranscript(text)
        return processed.normalizedNumbers.map { extractAmount(it) }
    }
}
