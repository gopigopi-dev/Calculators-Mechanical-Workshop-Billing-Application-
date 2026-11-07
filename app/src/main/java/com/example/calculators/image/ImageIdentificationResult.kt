package com.example.calculators.image

/**
 * Phase 9: Status of mechanical part image identification.
 */
enum class ImageMatchStatus {
    EXACT_FILENAME_MATCH,
    USER_ASSOCIATION_MATCH,
    PARTIAL_OR_FUZZY_MATCH,
    CONTENT_FALLBACK_UNCERTAIN,
    AMBIGUOUS_SIMILAR_FILENAMES,
    NO_MATCH
}

/**
 * Phase 9: Result of matching a voice-recognized or manually entered mechanical part name
 * against the local mechanical part image collection.
 */
data class ImageIdentificationResult(
    val queriedPartName: String,
    val status: ImageMatchStatus,
    val matchedImage: ImageAssociation? = null,
    val candidateImages: List<ImageAssociation> = emptyList(),
    val confidence: Double = 0.0,
    val requiresUserConfirmation: Boolean = false,
    val statusMessage: String = ""
) {
    val hasImage: Boolean
        get() = matchedImage != null

    val imageUri: String?
        get() = matchedImage?.imageUri

    val matchedFilename: String
        get() = matchedImage?.originalFilename ?: ""

    companion object {
        fun noMatch(queriedPartName: String): ImageIdentificationResult {
            return ImageIdentificationResult(
                queriedPartName = queriedPartName,
                status = ImageMatchStatus.NO_MATCH,
                matchedImage = null,
                candidateImages = emptyList(),
                confidence = 0.0,
                requiresUserConfirmation = false,
                statusMessage = if (queriedPartName.isBlank()) {
                    ""
                } else {
                    "No matching image found for '$queriedPartName'"
                }
            )
        }
    }
}
