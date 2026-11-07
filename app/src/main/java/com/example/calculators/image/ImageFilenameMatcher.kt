package com.example.calculators.image

import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Phase 9: Extracts mechanical part names from uploaded image filenames and matches them
 * against voice-recognized or manually entered part names.
 *
 * Identification Priority:
 * 1. Read the original image filename.
 * 2. Extract the meaningful mechanical part or domain name from the filename.
 * 3. Perform exact matching first, followed by controlled partial/compound/fuzzy matching.
 * 4. Prevent false matches when multiple filenames are similar (e.g., "bush" vs "steelbush").
 */
object ImageFilenameMatcher {

    private val SUPPORTED_EXTENSIONS = listOf(".jpeg", ".jpg", ".png", ".webp")
    private val ATTACHMENT_HASH_SUFFIX_REGEX = Regex("_[0-9a-fA-F]{8}$")
    private val UNCLEAR_FILENAME_REGEX = Regex(
        "^(?:img|dsc|dcim|pxl|photo|image|pic|screenshot|whatsapp[\\s_-]*image)[\\s_-]*[0-9a-fA-F_-]+$|^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$|^\\d+$",
        RegexOption.IGNORE_CASE
    )
    private val SEPARATOR_REGEX = Regex("[_\\-\\s]+")
    private val CAMEL_CASE_BOUNDARY_REGEX = Regex("(?<=[a-z])(?=[A-Z])")

    /**
     * Common compound mechanical filenames that users often speak either as one word or two words.
     */
    private val COMPOUND_MECHANICAL_SPLITS: Map<String, List<String>> = mapOf(
        "connectingrod" to listOf("connecting rod", "connectingrod", "rod"),
        "steelbush" to listOf("steel bush", "steelbush"),
        "steelplate" to listOf("steel plate", "steelplate"),
        "lathework" to listOf("lathe work", "lathework", "lathe"),
        "olisteel" to listOf("olisteel", "oil seal", "oilseal", "oil steel"),
        "connesser" to listOf("connesser", "condenser", "capacitor"),
        "wireing" to listOf("wireing", "wiring", "motor wiring"),
        "666 paste" to listOf("666 paste", "paste", "anabond paste", "gasket paste"),
        "motor clutch centrifugal plates" to listOf(
            "motor clutch centrifugal plates",
            "clutch plates",
            "centrifugal plates",
            "clutch plate",
            "motor clutch"
        )
    )

    /**
     * Strips directory path, file extension, and optional 8-char attachment hash suffix.
     * Example: "Piston_272f9602.jpg" -> "Piston"
     * Example: "666_paste_3935fc0a.jpg" -> "666 paste"
     * Example: "Bearing__c92d9f7b.jpg" -> "Bearing"
     */
    fun extractCleanBaseName(rawFilename: String): String {
        val filenameOnly = rawFilename.substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
        if (filenameOnly.isEmpty()) return ""

        var withoutExt = filenameOnly
        val lower = filenameOnly.lowercase(Locale.ROOT)
        for (ext in SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                withoutExt = filenameOnly.dropLast(ext.length)
                break
            }
        }

        val withoutHash = withoutExt.replace(ATTACHMENT_HASH_SUFFIX_REGEX, "")
        return withoutHash
            .replace(SEPARATOR_REGEX, " ")
            .trim()
    }

    /**
     * Detects whether a filename is generic/camera-generated (such as IMG_<uuid>.jpg)
     * rather than a meaningful mechanical part name.
     */
    fun isUnclearFilename(rawFilename: String): Boolean {
        val filenameOnly = rawFilename.substringAfterLast('/').substringAfterLast('\\').trim()
        var withoutExt = filenameOnly
        val lower = filenameOnly.lowercase(Locale.ROOT)
        for (ext in SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                withoutExt = filenameOnly.dropLast(ext.length)
                break
            }
        }
        val withoutHash = withoutExt.replace(ATTACHMENT_HASH_SUFFIX_REGEX, "").trim()
        if (withoutHash.isEmpty()) return true
        return UNCLEAR_FILENAME_REGEX.matches(withoutHash)
    }

    /**
     * Extracts the primary normalized keyword from a filename, or empty string if the filename is unclear.
     */
    fun extractPrimaryKeyword(rawFilename: String): String {
        if (isUnclearFilename(rawFilename)) return ""
        val cleanBase = extractCleanBaseName(rawFilename)
        return normalizePartQuery(cleanBase)
    }

    /**
     * Extracts all normalized keyword aliases from a filename so compound words, singular/plural
     * variants, and multi-word mechanical names match accurately.
     */
    fun extractNormalizedAliases(rawFilename: String): Set<String> {
        if (isUnclearFilename(rawFilename)) return emptySet()
        val primary = extractPrimaryKeyword(rawFilename)
        if (primary.isEmpty()) return emptySet()

        val aliases = LinkedHashSet<String>()
        aliases.add(primary)

        // Add collapsed (no-space) form and camelCase split form
        val collapsed = primary.replace(" ", "")
        if (collapsed.isNotEmpty()) {
            aliases.add(collapsed)
        }

        val cleanBase = extractCleanBaseName(rawFilename)
        val camelSplit = normalizePartQuery(cleanBase.replace(CAMEL_CASE_BOUNDARY_REGEX, " "))
        if (camelSplit.isNotEmpty()) {
            aliases.add(camelSplit)
        }

        // Singular/plural mechanical part variations (e.g., "rings" <-> "ring", "plates" <-> "plate")
        if (primary.endsWith("s") && primary.length > 4) {
            aliases.add(primary.removeSuffix("s"))
        }

        // Known mechanical compound splits
        COMPOUND_MECHANICAL_SPLITS[primary]?.forEach { aliases.add(normalizePartQuery(it)) }
        COMPOUND_MECHANICAL_SPLITS[collapsed]?.forEach { aliases.add(normalizePartQuery(it)) }

        return aliases
    }

    /**
     * Normalizes a voice or text part query while preserving Tamil Unicode characters and
     * lowercasing English characters.
     */
    fun normalizePartQuery(query: String): String {
        return query
            .replace(SEPARATOR_REGEX, " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    /**
     * Matches a normalized part query (and optional phonetic/domain equivalent query)
     * against the available image associations using strict priority order.
     */
    fun matchByFilename(
        rawPartName: String,
        bridgedKeywords: Set<String> = emptySet(),
        associations: List<ImageAssociation>
    ): ImageIdentificationResult {
        val normalizedQuery = normalizePartQuery(rawPartName)
        if (normalizedQuery.isEmpty()) {
            return ImageIdentificationResult.noMatch(rawPartName)
        }

        val allSearchQueries = LinkedHashSet<String>().apply {
            add(normalizedQuery)
            val collapsed = normalizedQuery.replace(" ", "")
            if (collapsed.isNotEmpty()) add(collapsed)
            bridgedKeywords.forEach { kw ->
                val norm = normalizePartQuery(kw)
                if (norm.isNotEmpty()) {
                    add(norm)
                    val normCollapsed = norm.replace(" ", "")
                    if (normCollapsed.isNotEmpty()) add(normCollapsed)
                }
            }
        }

        val namedAssociations = associations.filter { !it.isFilenameUnclear && it.extractedKeyword.isNotBlank() }

        // STEP 1: Exact Primary Filename Keyword Match
        val exactPrimaryMatches = namedAssociations.filter { assoc ->
            assoc.extractedKeyword in allSearchQueries ||
                assoc.extractedKeyword.replace(" ", "") in allSearchQueries
        }
        if (exactPrimaryMatches.size == 1) {
            val matched = exactPrimaryMatches.first()
            return ImageIdentificationResult(
                queriedPartName = rawPartName,
                status = ImageMatchStatus.EXACT_FILENAME_MATCH,
                matchedImage = matched,
                candidateImages = listOf(matched),
                confidence = 1.0,
                requiresUserConfirmation = false,
                statusMessage = "Matched filename '${matched.originalFilename}'"
            )
        } else if (exactPrimaryMatches.size > 1) {
            return ImageIdentificationResult(
                queriedPartName = rawPartName,
                status = ImageMatchStatus.AMBIGUOUS_SIMILAR_FILENAMES,
                matchedImage = exactPrimaryMatches.first(),
                candidateImages = exactPrimaryMatches,
                confidence = 0.75,
                requiresUserConfirmation = true,
                statusMessage = "Multiple similar filenames matched '$rawPartName'. Please confirm."
            )
        }

        // STEP 2: Exact Alias Match (e.g., "Paste" -> "666 paste", "Connecting rod" -> "Connectingrod")
        val exactAliasMatches = namedAssociations.filter { assoc ->
            assoc.normalizedAliases.any { alias -> alias in allSearchQueries }
        }
        if (exactAliasMatches.size == 1) {
            val matched = exactAliasMatches.first()
            return ImageIdentificationResult(
                queriedPartName = rawPartName,
                status = ImageMatchStatus.EXACT_FILENAME_MATCH,
                matchedImage = matched,
                candidateImages = listOf(matched),
                confidence = 0.95,
                requiresUserConfirmation = false,
                statusMessage = "Matched filename keyword '${matched.displayName}'"
            )
        } else if (exactAliasMatches.size > 1) {
            return ImageIdentificationResult(
                queriedPartName = rawPartName,
                status = ImageMatchStatus.AMBIGUOUS_SIMILAR_FILENAMES,
                matchedImage = exactAliasMatches.first(),
                candidateImages = exactAliasMatches,
                confidence = 0.7,
                requiresUserConfirmation = true,
                statusMessage = "Multiple similar images found for '$rawPartName'. Please confirm."
            )
        }

        // STEP 3: Controlled Token / Partial / Fuzzy Match
        // We score each named association carefully and check if there is a clear winner or ambiguity.
        data class ScoredCandidate(
            val association: ImageAssociation,
            val score: Double
        )

        val scoredCandidates = mutableListOf<ScoredCandidate>()
        for (assoc in namedAssociations) {
            var bestScore = 0.0
            for (queryVariant in allSearchQueries) {
                val queryTokens = queryVariant.split(" ").filter { it.isNotBlank() }

                for (alias in assoc.normalizedAliases) {
                    val aliasTokens = alias.split(" ").filter { it.isNotBlank() }

                    // Multi-word token containment (only when query has all tokens of alias or vice-versa)
                    if (queryTokens.size > 1 && aliasTokens.isNotEmpty() && queryTokens.containsAll(aliasTokens)) {
                        // e.g., query "spindle bearing" contains "bearing"
                        val ratio = aliasTokens.size.toDouble() / queryTokens.size.toDouble()
                        bestScore = maxOf(bestScore, 0.82 + (0.10 * ratio))
                    } else if (aliasTokens.size > 1 && queryTokens.isNotEmpty() && aliasTokens.containsAll(queryTokens)) {
                        // e.g., query "steel" is contained in both "steel bush" and "steel plate"
                        val ratio = queryTokens.size.toDouble() / aliasTokens.size.toDouble()
                        bestScore = maxOf(bestScore, 0.72 + (0.15 * ratio))
                    }

                    // Controlled Levenshtein fuzzy match on full keyword or aliases
                    if (isControlledFuzzyMatch(queryVariant, alias)) {
                        bestScore = maxOf(bestScore, 0.85)
                    }
                }
            }
            if (bestScore >= 0.72) {
                scoredCandidates.add(ScoredCandidate(assoc, bestScore))
            }
        }

        if (scoredCandidates.isEmpty()) {
            return ImageIdentificationResult.noMatch(rawPartName)
        }

        val sorted = scoredCandidates.sortedByDescending { it.score }
        val top = sorted.first()
        val closeCompetitors = sorted.filter { (top.score - it.score) <= 0.08 }

        return if (closeCompetitors.size > 1) {
            ImageIdentificationResult(
                queriedPartName = rawPartName,
                status = ImageMatchStatus.AMBIGUOUS_SIMILAR_FILENAMES,
                matchedImage = top.association,
                candidateImages = closeCompetitors.map { it.association },
                confidence = top.score,
                requiresUserConfirmation = true,
                statusMessage = "Similar filenames (${closeCompetitors.joinToString { it.association.displayName }}) match '$rawPartName'. Please confirm."
            )
        } else {
            ImageIdentificationResult(
                queriedPartName = rawPartName,
                status = ImageMatchStatus.PARTIAL_OR_FUZZY_MATCH,
                matchedImage = top.association,
                candidateImages = listOf(top.association),
                confidence = top.score,
                requiresUserConfirmation = top.score < 0.80,
                statusMessage = "Matched '${top.association.displayName}' from filename"
            )
        }
    }

    private fun isControlledFuzzyMatch(a: String, b: String): Boolean {
        if (a == b) return true
        val minLen = min(a.length, b.length)
        if (minLen < 4) return false
        if (abs(a.length - b.length) > 2) return false
        if (a.first() != b.first()) return false

        val dist = levenshteinDistance(a, b)
        val maxAllowed = if (minLen <= 6) 1 else 2
        return dist <= maxAllowed
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
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
