package com.example.calculators.textprocessing

data class IdentifiedProductSpan(
    val rawProductText: String,
    val resolvedProductName: String,
    val status: ProductIdentificationStatus,
    val confidence: Double,
    val startTokenIndex: Int,
    val endTokenIndex: Int
)

/**
 * Phase 6: Identifies English, Tamil, and user-defined product names from transcript tokens,
 * while ignoring non-product connector words, quantity markers, and price markers.
 */
class ProductIdentifier(
    val dictionary: ProductDictionary = ProductDictionary(),
    private val matcher: ProductMatcher = ProductMatcher(dictionary)
) {

    fun identifyProductName(candidatePhrase: String): ProductMatchResult {
        val cleanedTokens = candidatePhrase
            .trim()
            .split(Regex("\\s+"))
            .filter { token ->
                token.isNotBlank() && !isStructuralNoiseWord(token)
            }
        val filteredPhrase = cleanedTokens.joinToString(" ").trim()
        return matcher.matchProduct(filteredPhrase)
    }

    fun isStructuralNoiseWord(token: String): Boolean {
        val lower = token.trim().lowercase()
        return lower in NOISE_AND_MARKER_WORDS
    }

    fun isUnitPriceMarker(token: String): Boolean {
        val lower = token.trim().lowercase()
        return lower in UNIT_PRICE_MARKER_WORDS
    }

    fun isExplicitAmountMarker(token: String): Boolean {
        val lower = token.trim().lowercase()
        return lower in AMOUNT_MARKER_WORDS
    }

    fun isExplicitQuantityMarker(token: String): Boolean {
        val lower = token.trim().lowercase()
        return lower in QUANTITY_MARKER_WORDS
    }

    companion object {
        val QUANTITY_MARKER_WORDS = setOf(
            "qty", "quantity", "quantities", "nos", "pieces", "piece", "packs", "pack",
            "அளவு", "எண்ணிக்கை"
        )

        val AMOUNT_MARKER_WORDS = setOf(
            "rupees", "rupee", "rs", "inr", "amount", "price", "total",
            "ரூபாய்", "ரூ", "விலை"
        )

        val UNIT_PRICE_MARKER_WORDS = setOf(
            "each", "per", "rate", "unit", "ஒன்றுக்கு", "வீதம்"
        )

        val CONNECTOR_WORDS = setOf(
            "and", "for", "at", "of", "the", "with", "then",
            "மற்றும்", "க்கு"
        )

        val NOISE_AND_MARKER_WORDS =
            QUANTITY_MARKER_WORDS + AMOUNT_MARKER_WORDS + UNIT_PRICE_MARKER_WORDS + CONNECTOR_WORDS
    }
}
