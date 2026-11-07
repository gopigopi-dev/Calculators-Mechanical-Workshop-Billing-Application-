package com.example.calculators.textprocessing

import kotlin.math.min

enum class ProductIdentificationStatus {
    EXACT_MATCH,
    FUZZY_MATCH,
    USER_DEFINED,
    UNIDENTIFIED
}

data class ProductMatchResult(
    val inputText: String,
    val resolvedProductName: String,
    val status: ProductIdentificationStatus,
    val confidence: Double
)

/**
 * Phase 6: Matches candidate product names against ProductDictionary using:
 * 1. Exact matching first (preserving language and script).
 * 2. Controlled same-script fuzzy matching (Levenshtein edit distance) for minor speech/spelling variations.
 * 3. User-defined preservation when a valid Tamil/English word is not in the dictionary.
 */
class ProductMatcher(
    private val dictionary: ProductDictionary = ProductDictionary()
) {

    fun matchProduct(candidateText: String): ProductMatchResult {
        val trimmed = candidateText.trim()
        if (trimmed.isEmpty() || !hasMeaningfulProductCharacters(trimmed)) {
            return ProductMatchResult(
                inputText = candidateText,
                resolvedProductName = trimmed,
                status = ProductIdentificationStatus.UNIDENTIFIED,
                confidence = 0.0
            )
        }

        val allKnown = dictionary.getAllProducts()

        // 1. Exact Match (case-insensitive for English, exact Unicode for Tamil, never cross-translating)
        val exactMatch = allKnown.firstOrNull { entry ->
            entry.equals(trimmed, ignoreCase = true)
        }
        if (exactMatch != null) {
            return ProductMatchResult(
                inputText = trimmed,
                resolvedProductName = exactMatch,
                status = ProductIdentificationStatus.EXACT_MATCH,
                confidence = 1.0
            )
        }

        // 2. Controlled Fuzzy Match (within the SAME script only so Tamil is never replaced by English or vice versa)
        val candidateScript = LanguageDetector.detectTokenScript(trimmed)
        var bestFuzzyMatch: String? = null
        var bestDistance = Int.MAX_VALUE

        for (entry in allKnown) {
            val entryScript = LanguageDetector.detectTokenScript(entry)
            if (entryScript != candidateScript) continue

            val maxAllowedDistance = maxEditDistanceForLength(min(trimmed.length, entry.length))
            if (maxAllowedDistance == 0) continue

            val distance = levenshteinDistance(trimmed.lowercase(), entry.lowercase())
            if (distance in 1..maxAllowedDistance && distance < bestDistance) {
                // Require matching first character to prevent false-positive fuzzy matches (e.g. "Salt" vs "Malt")
                if (trimmed.first().lowercaseChar() == entry.first().lowercaseChar()) {
                    bestDistance = distance
                    bestFuzzyMatch = entry
                }
            }
        }

        if (bestFuzzyMatch != null) {
            return ProductMatchResult(
                inputText = trimmed,
                resolvedProductName = bestFuzzyMatch,
                status = ProductIdentificationStatus.FUZZY_MATCH,
                confidence = 0.85
            )
        }

        // 3. Preserve original user-defined product name verbatim without inventing or translating
        return ProductMatchResult(
            inputText = trimmed,
            resolvedProductName = trimmed,
            status = ProductIdentificationStatus.USER_DEFINED,
            confidence = 0.75
        )
    }

    private fun hasMeaningfulProductCharacters(text: String): Boolean {
        return text.any { LanguageDetector.isTamilChar(it) || LanguageDetector.isEnglishLetter(it) }
    }

    private fun maxEditDistanceForLength(length: Int): Int {
        return when {
            length < 4 -> 0
            length <= 6 -> 1
            else -> 2
        }
    }

    companion object {
        fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
            val lhsLen = lhs.length
            val rhsLen = rhs.length
            var cost = IntArray(lhsLen + 1) { it }
            var newCost = IntArray(lhsLen + 1)

            for (i in 1..rhsLen) {
                newCost[0] = i
                for (j in 1..lhsLen) {
                    val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                    val costReplace = cost[j - 1] + match
                    val costInsert = cost[j] + 1
                    val costDelete = newCost[j - 1] + 1
                    newCost[j] = min(min(costInsert, costDelete), costReplace)
                }
                val swap = cost
                cost = newCost
                newCost = swap
            }
            return cost[lhsLen]
        }
    }
}
