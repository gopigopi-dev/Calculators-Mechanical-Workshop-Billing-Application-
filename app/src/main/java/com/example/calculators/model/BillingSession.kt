package com.example.calculators.model

import java.math.BigDecimal
import java.math.RoundingMode

data class BillingSession(
    val sessionId: String = "CAL-0001",
    val products: List<Product> = emptyList(),
    val grandTotal: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
    val grandTotalDisplay: String = BillingCalculator.formatCurrency(BigDecimal.ZERO)
)
