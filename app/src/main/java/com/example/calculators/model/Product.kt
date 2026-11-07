package com.example.calculators.model

import com.example.calculators.image.ImageMatchStatus
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Represents a mechanical machine part and its calibration record.
 *
 * Rules:
 * - Quantity is optional (`null` when not provided; never automatically defaulted to 1).
 * - Amount is entered and validated independently.
 * - Preserves Tamil and English part/machine names in their original script without translation.
 * - Associates the identified or user-selected mechanical part image (`imageUri`).
 */
data class Product(
    val id: String,
    val productName: String,
    val quantity: Double? = null,
    val amount: Double,
    val calibrationNotes: String = "",
    val imageUri: String? = null,
    val imageSourceName: String = "",
    val imageMatchStatus: ImageMatchStatus = ImageMatchStatus.NO_MATCH,
    val imageNeedsConfirmation: Boolean = false,
    val isUnitPrice: Boolean = false
) {
    val lineAmount: BigDecimal
        get() = BillingCalculator.calculateLineAmount(this)

    val formattedQuantity: String
        get() {
            val currentQty = quantity ?: return "-"
            return formatQuantityNumber(currentQty)
        }

    val rawQuantityInput: String
        get() {
            val currentQty = quantity ?: return ""
            return formatQuantityNumber(currentQty)
        }

    val formattedAmount: String
        get() = BillingCalculator.formatCurrency(lineAmount)

    val rawAmountInput: String
        get() = BigDecimal.valueOf(amount)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString()

    private fun formatQuantityNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
        }
    }

    companion object {
        fun isValidProductName(name: String): Boolean {
            return name.trim().isNotEmpty()
        }

        /**
         * Validates an optional quantity input string:
         * - Blank/empty string is valid (`null` quantity).
         * - Non-blank string must parse to a finite positive number (`> 0.0`).
         */
        fun isValidOptionalQuantityInput(rawInput: String): Boolean {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) return true
            val parsed = trimmed.toDoubleOrNull() ?: return false
            return isValidPositiveQuantity(parsed)
        }

        fun isValidPositiveQuantity(quantity: Double?): Boolean {
            return quantity != null && !quantity.isNaN() && !quantity.isInfinite() && quantity > 0.0
        }

        /**
         * Quantity is optional: `null` is valid, or a positive finite number is valid.
         */
        fun isValidQuantity(quantity: Double?): Boolean {
            return quantity == null || isValidPositiveQuantity(quantity)
        }

        fun isValidAmount(amount: Double?): Boolean {
            return amount != null && !amount.isNaN() && !amount.isInfinite() && amount >= 0.0
        }
    }
}
