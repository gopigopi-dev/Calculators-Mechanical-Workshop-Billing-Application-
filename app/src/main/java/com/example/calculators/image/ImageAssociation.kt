package com.example.calculators.image

/**
 * Phase 9: Represents a mechanical part image in the local image collection
 * and its filename/user keyword associations.
 */
data class ImageAssociation(
    val id: String,
    val originalFilename: String,
    val displayName: String,
    val extractedKeyword: String,
    val normalizedAliases: Set<String> = emptySet(),
    val imageUri: String,
    val isFilenameUnclear: Boolean = false,
    val contentFallbackKeywords: Set<String> = emptySet(),
    val userAssignedPartNames: Set<String> = emptySet(),
    val isConfirmedByUser: Boolean = false,
    val isBundledAsset: Boolean = false
) {
    /**
     * Human-readable label showing either the extracted mechanical part name
     * or user-assigned part name if the original filename was unclear (e.g. IMG_...).
     */
    val primaryLabel: String
        get() = when {
            userAssignedPartNames.isNotEmpty() -> userAssignedPartNames.first()
            displayName.isNotBlank() && !isFilenameUnclear -> displayName
            contentFallbackKeywords.isNotEmpty() -> "${contentFallbackKeywords.first()} (Unclear filename)"
            else -> originalFilename
        }
}
