package com.example.calculators.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object BillingCalculator {

    private val currencySymbols = DecimalFormatSymbols(Locale.US)
    private val currencyDecimalFormat = DecimalFormat("#,##0.00", currencySymbols)

    /**
     * Calculates the line amount for a single calibration/part entry using BigDecimal.
     *
     * Rules:
     * - Treat product.amount as the final line amount by default (even when product.quantity is null).
     * - Never multiply Quantity * Amount automatically unless product.isUnitPrice is explicitly true
     *   AND a valid positive quantity was provided.
     */
    fun calculateLineAmount(product: Product): BigDecimal {
        if (product.amount.isNaN() || product.amount.isInfinite() || product.amount < 0.0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        }

        val baseAmount = BigDecimal.valueOf(product.amount)
        val qty = product.quantity
        val lineTotal = if (product.isUnitPrice && qty != null) {
            if (qty.isNaN() || qty.isInfinite() || qty <= 0.0) {
                BigDecimal.ZERO
            } else {
                baseAmount.multiply(BigDecimal.valueOf(qty))
            }
        } else {
            baseAmount
        }

        return lineTotal.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Calculates the Grand Total as the sum of all valid calibration/part line amounts,
     * including entries where quantity is null.
     */
    fun calculateGrandTotal(products: List<Product>): BigDecimal {
        if (products.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        }

        return products
            .fold(BigDecimal.ZERO) { acc, product ->
                acc.add(calculateLineAmount(product))
            }
            .setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Formats a BigDecimal monetary value consistently with Indian Rupee symbol (₹) and two decimal places.
     * Examples: ₹0.00, ₹400.00, ₹1,250.50
     */
    fun formatCurrency(amount: BigDecimal): String {
        val safeAmount = amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        return "₹${currencyDecimalFormat.format(safeAmount)}"
    }

    /**
     * Formats a Double monetary value consistently using BigDecimal precision.
     */
    fun formatCurrency(amount: Double): String {
        if (amount.isNaN() || amount.isInfinite() || amount < 0.0) {
            return "₹0.00"
        }
        return formatCurrency(BigDecimal.valueOf(amount))
    }

    /**
     * Calculates and formats the Grand Total for a list of calibration/part entries.
     */
    fun calculateFormattedGrandTotal(products: List<Product>): String {
        return formatCurrency(calculateGrandTotal(products))
    }
}
