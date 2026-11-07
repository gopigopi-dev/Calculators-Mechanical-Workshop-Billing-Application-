package com.example.calculators.image

/**
 * Phase 9: Coordinates mechanical part image identification across:
 * 1. User-assigned or user-confirmed manual associations (highest override when explicitly set by user).
 * 2. Filename-based keyword matching (Priority 1-3: exact filename match, then controlled partial/fuzzy match).
 * 3. Tamil & English mechanical part name phonetic/domain keyword bridging (without translating or altering
 *    the user's original Tamil or English part name).
 * 4. Lightweight visual content fallback ONLY when the filename is unclear (e.g., IMG_... files),
 *    always marking such matches for user confirmation.
 * 5. Never randomly assigns an image when no match is found.
 */
class MechanicalPartImageMatcher(
    private val collectionManager: ImageCollectionManager = ImageCollectionManager()
) {

    val imageCollectionManager: ImageCollectionManager
        get() = collectionManager

    /**
     * Bridges Tamil spoken mechanical part names (both Tamil phonetic transliterations and
     * standard Tamil mechanical terms) to filename lookup keywords while keeping the original
     * Tamil part name untouched in the calibration record.
     */
    private val tamilToMechanicalKeywordBridge: Map<String, Set<String>> = mapOf(
        "ஹெட்" to setOf("head"),
        "தலை" to setOf("head"),
        "பேஸ்ட்" to setOf("666 paste", "paste"),
        "பசை" to setOf("666 paste", "paste"),
        "பேரிங்" to setOf("bearing"),
        "பியரிங்" to setOf("bearing"),
        "தாங்கி" to setOf("bearing"),
        "தாங்கி உருளை" to setOf("bearing"),
        "பிஸ்டன்" to setOf("piston"),
        "ரிங்ஸ்" to setOf("rings"),
        "ரிங்" to setOf("rings"),
        "வளையம்" to setOf("rings"),
        "கேஸ்கட்" to setOf("gasket"),
        "புஷ்" to setOf("bush"),
        "ஸ்டீல் புஷ்" to setOf("steel bush", "steelbush"),
        "பில்டர்" to setOf("filter"),
        "ஃபில்டர்" to setOf("filter"),
        "வடிகட்டி" to setOf("filter"),
        "பேன்" to setOf("fan"),
        "ஃபேன்" to setOf("fan"),
        "விசிறி" to setOf("fan"),
        "கிளாம்ப்" to setOf("clamp"),
        "கனெக்டிங் ராட்" to setOf("connecting rod", "connectingrod"),
        "தண்டு" to setOf("connecting rod", "connectingrod"),
        "சுழல் தண்டு" to setOf("connecting rod", "connectingrod"),
        "வெல்டிங்" to setOf("welding"),
        "வைரிங்" to setOf("wireing", "wiring"),
        "வயரிங்" to setOf("wireing", "wiring"),
        "ஷீட்" to setOf("sheet"),
        "தகடு" to setOf("sheet"),
        "தகுது" to setOf("sheet"),
        "ஸ்டீல் பிளேட்" to setOf("steel plate"),
        "பிளேட்" to setOf("steel plate"),
        "லேத் ஒர்க்" to setOf("lathe work", "lathework"),
        "லேத்" to setOf("lathe work", "lathework"),
        "ஆயில் சீல்" to setOf("olisteel", "oil seal"),
        "ஆயில் ஸ்டீல்" to setOf("olisteel"),
        "கண்டென்சர்" to setOf("connesser", "condenser"),
        "கெப்பாசிட்டர்" to setOf("connesser", "capacitor"),
        "கிளட்ச்" to setOf("motor clutch centrifugal plates", "clutch plates"),
        "கிளட்ச் பிளேட்" to setOf("motor clutch centrifugal plates", "clutch plates"),
        "போல்ட்" to setOf("bolt", "nut bolt"),
        "நட்" to setOf("nut", "nut bolt"),
        "வாஷர்" to setOf("washer", "flat washer", "spring washer")
    )

    /**
     * Identifies the matching image for a given Tamil or English mechanical part name.
     */
    fun identifyImageForPart(partName: String): ImageIdentificationResult {
        val trimmed = partName.trim()
        if (trimmed.isEmpty()) {
            return ImageIdentificationResult.noMatch(partName)
        }

        // Check if the user explicitly removed image association for this part name
        if (collectionManager.isPartAssociationSuppressed(trimmed)) {
            return ImageIdentificationResult.noMatch(trimmed)
        }

        val normalizedQuery = ImageFilenameMatcher.normalizePartQuery(trimmed)
        val allImages = collectionManager.getAllImages()

        // 1. Check user-confirmed / manually assigned part-to-image associations first
        val userAssigned = allImages.firstOrNull { assoc ->
            assoc.userAssignedPartNames.any { assigned ->
                ImageFilenameMatcher.normalizePartQuery(assigned) == normalizedQuery
            }
        }
        if (userAssigned != null) {
            return ImageIdentificationResult(
                queriedPartName = trimmed,
                status = ImageMatchStatus.USER_ASSOCIATION_MATCH,
                matchedImage = userAssigned,
                candidateImages = listOf(userAssigned),
                confidence = 1.0,
                requiresUserConfirmation = false,
                statusMessage = "User-assigned image '${userAssigned.originalFilename}'"
            )
        }

        // 2. Collect any Tamil -> mechanical filename bridged keywords (preserving Tamil partName)
        val bridgedKeywords = LinkedHashSet<String>()
        val directBridge = tamilToMechanicalKeywordBridge[trimmed]
            ?: tamilToMechanicalKeywordBridge[normalizedQuery]
        if (directBridge != null) {
            bridgedKeywords.addAll(directBridge)
        } else {
            val queryWords = normalizedQuery.split(" ").filter { it.isNotBlank() }
            for (word in queryWords) {
                tamilToMechanicalKeywordBridge[word]?.let { bridgedKeywords.addAll(it) }
            }
        }

        // 3. Match against image filenames (Exact filename match -> Controlled partial/fuzzy match)
        val filenameResult = ImageFilenameMatcher.matchByFilename(
            rawPartName = trimmed,
            bridgedKeywords = bridgedKeywords,
            associations = allImages
        )
        if (filenameResult.status != ImageMatchStatus.NO_MATCH) {
            return filenameResult
        }

        // 4. Fallback ONLY for images with unclear filenames (e.g. IMG_<uuid>.jpg) using visual content keywords
        val allQueries = LinkedHashSet<String>().apply {
            add(normalizedQuery)
            bridgedKeywords.forEach { add(ImageFilenameMatcher.normalizePartQuery(it)) }
        }
        val contentFallbackMatches = allImages.filter { assoc ->
            assoc.isFilenameUnclear && assoc.contentFallbackKeywords.any { kw ->
                val normKw = ImageFilenameMatcher.normalizePartQuery(kw)
                normKw in allQueries || allQueries.any { q -> q.length >= 4 && (normKw.contains(q) || q.contains(normKw)) }
            }
        }

        if (contentFallbackMatches.isNotEmpty()) {
            val topCandidate = contentFallbackMatches.first()
            return ImageIdentificationResult(
                queriedPartName = trimmed,
                status = ImageMatchStatus.CONTENT_FALLBACK_UNCERTAIN,
                matchedImage = topCandidate,
                candidateImages = contentFallbackMatches,
                confidence = 0.65,
                requiresUserConfirmation = true,
                statusMessage = "Filename '${topCandidate.originalFilename}' is unclear; matched by visual part type. Please confirm."
            )
        }

        // 5. Never randomly assign an image if no match was found
        return ImageIdentificationResult.noMatch(trimmed)
    }
}
