package com.example.calculators.textprocessing

import com.example.calculators.image.ImageIdentificationResult
import com.example.calculators.model.BillingCalculator
import com.example.calculators.model.Product
import java.math.BigDecimal
import java.util.UUID

enum class OverallParseStatus {
    CONFIDENT,
    INCOMPLETE,
    AMBIGUOUS
}

data class ParsedProduct(
    val id: String = UUID.randomUUID().toString(),
    val rawSegmentText: String,
    val productName: String,
    val productIdentificationStatus: ProductIdentificationStatus,
    val productStatus: FieldExtractionStatus,
    val extractedQuantity: ExtractedQuantity,
    val extractedAmount: ExtractedAmount,
    val overallStatus: OverallParseStatus,
    val statusMessage: String = "",
    val imageIdentification: ImageIdentificationResult = ImageIdentificationResult.noMatch(productName)
) {
    /**
     * Quantity is optional (`extractedQuantity.value == null` is valid).
     * Amount and part name are required for automatic insertion into the calibration table.
     */
    val isReadyForAutoInsert: Boolean
        get() = overallStatus == OverallParseStatus.CONFIDENT &&
            productName.isNotBlank() &&
            (extractedQuantity.value == null || extractedQuantity.value > 0.0) &&
            extractedQuantity.status != FieldExtractionStatus.AMBIGUOUS &&
            extractedAmount.value != null &&
            extractedAmount.value >= BigDecimal.ZERO

    val quantityDisplay: String
        get() = extractedQuantity.value?.let { qty ->
            if (qty % 1.0 == 0.0) qty.toLong().toString() else qty.toString()
        } ?: "-"

    val amountDisplay: String
        get() = extractedAmount.value?.let { amt ->
            BillingCalculator.formatCurrency(amt)
        } ?: "Missing"

    val missingQuantity: Boolean
        get() = extractedQuantity.value == null

    val missingAmount: Boolean
        get() = extractedAmount.value == null

    val quantityInputPrefill: String
        get() = extractedQuantity.value?.let { qty ->
            if (qty % 1.0 == 0.0) qty.toLong().toString() else qty.toString()
        } ?: ""

    /**
     * Converts a confident ParsedProduct into a domain Product for the calibration table.
     * Preserves `quantity = null` when Quantity was not provided and attaches any identified part image.
     */
    fun toProductOrNull(customId: String = id): Product? {
        val qty = extractedQuantity.value
        val amt = extractedAmount.value ?: return null
        if (productName.isBlank() || (qty != null && qty <= 0.0) || amt < BigDecimal.ZERO) {
            return null
        }
        return Product(
            id = customId,
            productName = productName,
            quantity = qty,
            amount = amt.toDouble(),
            imageUri = imageIdentification.imageUri,
            imageSourceName = imageIdentification.matchedFilename,
            imageMatchStatus = imageIdentification.status,
            imageNeedsConfirmation = imageIdentification.requiresUserConfirmation,
            isUnitPrice = extractedAmount.isUnitPrice
        )
    }
}
