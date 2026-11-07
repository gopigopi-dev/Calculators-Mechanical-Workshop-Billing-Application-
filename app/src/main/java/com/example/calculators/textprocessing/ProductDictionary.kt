package com.example.calculators.textprocessing

/**
 * Extensible dictionary of Tamil and English mechanical machine parts and calibration entries.
 *
 * Rules:
 * - Preserves original language and script (never translates Tamil <-> English).
 * - Does not hardcode grocery items or restrict the application to a fixed sample list.
 * - Supports runtime registration of user-defined machine parts and calibration components
 *   for exact and controlled same-script fuzzy matching.
 */
class ProductDictionary(
    initialProducts: Collection<String> = DEFAULT_PRODUCTS
) {
    private val knownProducts = LinkedHashSet<String>()
    private val userDefinedProducts = LinkedHashSet<String>()

    init {
        initialProducts.forEach { addKnownProduct(it) }
    }

    @Synchronized
    fun addKnownProduct(productName: String) {
        val trimmed = productName.trim()
        if (trimmed.isNotEmpty()) {
            knownProducts.add(trimmed)
        }
    }

    @Synchronized
    fun registerUserDefinedProduct(productName: String) {
        val trimmed = productName.trim()
        if (trimmed.isNotEmpty()) {
            userDefinedProducts.add(trimmed)
            knownProducts.add(trimmed)
        }
    }

    @Synchronized
    fun getAllProducts(): Set<String> {
        return LinkedHashSet(knownProducts)
    }

    @Synchronized
    fun isUserDefined(productName: String): Boolean {
        val normalized = productName.trim()
        return userDefinedProducts.any { it.equals(normalized, ignoreCase = true) }
    }

    companion object {
        val DEFAULT_PRODUCTS: List<String> = emptyList()
    }
}
