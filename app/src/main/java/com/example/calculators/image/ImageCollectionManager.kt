package com.example.calculators.image

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Phase 9–11: Manages the local collection of mechanical part images, including:
 * - The 24 uploaded mechanical machine-part images bundled in Android assets (`assets/mechanical_parts/`).
 * - User-selected images from Android device storage via the system file picker.
 * - Manual image reassignment, confirmation of uncertain matches, replacement, and removal.
 * - Persistent storage via `SharedPreferences` so custom part-to-image associations and device images
 *   survive closing and reopening the application.
 */
class ImageCollectionManager(
    initialImages: List<ImageAssociation> = defaultBundledMechanicalImages()
) {
    private val imagesById = LinkedHashMap<String, ImageAssociation>()
    private val suppressedPartNames = LinkedHashSet<String>()
    private var prefs: SharedPreferences? = null

    init {
        initialImages.forEach { assoc ->
            imagesById[assoc.id] = assoc
        }
    }

    /**
     * Binds this manager to persistent Android `SharedPreferences` storage and restores any
     * saved part-to-image assignments, suppressed part associations, or device-selected images.
     */
    @Synchronized
    fun bindPersistentStorage(
        context: Context,
        prefsName: String = DEFAULT_PREFS_NAME
    ) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        this.prefs = sharedPrefs
        restoreFromPreferences(sharedPrefs)
    }

    @Synchronized
    fun getAllImages(): List<ImageAssociation> {
        return imagesById.values.toList()
    }

    @Synchronized
    fun getImageById(imageId: String): ImageAssociation? {
        return imagesById[imageId]
    }

    @Synchronized
    fun getImageByUri(imageUri: String?): ImageAssociation? {
        if (imageUri.isNullOrBlank()) return null
        return imagesById.values.firstOrNull { it.imageUri == imageUri }
    }

    /**
     * Adds a new image from Android device storage or updates an existing entry if the same
     * filename or URI already exists (avoiding duplicates).
     */
    @Synchronized
    fun addOrUpdateImageFromStorage(
        imageUri: String,
        originalFilename: String,
        assignedPartName: String? = null
    ): ImageAssociation {
        val cleanFilename = originalFilename.substringAfterLast('/').substringAfterLast('\\').trim()
            .ifEmpty { "part_image_${imagesById.size + 1}.jpg" }

        val cleanDisplay = ImageFilenameMatcher.extractCleanBaseName(cleanFilename)
        val unclear = ImageFilenameMatcher.isUnclearFilename(cleanFilename)
        val primaryKeyword = ImageFilenameMatcher.extractPrimaryKeyword(cleanFilename)
        val aliases = ImageFilenameMatcher.extractNormalizedAliases(cleanFilename)

        val existing = imagesById.values.firstOrNull {
            it.imageUri == imageUri ||
                it.originalFilename.equals(cleanFilename, ignoreCase = true) ||
                (!unclear && primaryKeyword.isNotEmpty() && it.extractedKeyword == primaryKeyword)
        }

        val assignedSet = LinkedHashSet<String>().apply {
            existing?.userAssignedPartNames?.let { addAll(it) }
            if (!assignedPartName.isNullOrBlank()) {
                val trimmedPart = assignedPartName.trim()
                add(trimmedPart)
                suppressedPartNames.remove(ImageFilenameMatcher.normalizePartQuery(trimmedPart))
            }
        }

        val updatedAssociation = ImageAssociation(
            id = existing?.id ?: UUID.randomUUID().toString(),
            originalFilename = cleanFilename,
            displayName = if (!assignedPartName.isNullOrBlank() && unclear) {
                assignedPartName.trim()
            } else {
                cleanDisplay.ifEmpty { cleanFilename }
            },
            extractedKeyword = primaryKeyword,
            normalizedAliases = aliases,
            imageUri = imageUri,
            isFilenameUnclear = unclear,
            contentFallbackKeywords = existing?.contentFallbackKeywords ?: emptySet(),
            userAssignedPartNames = assignedSet,
            isConfirmedByUser = !assignedPartName.isNullOrBlank() || (existing?.isConfirmedByUser == true),
            isBundledAsset = false
        )

        imagesById[updatedAssociation.id] = updatedAssociation
        persistToPreferences()
        return updatedAssociation
    }

    /**
     * Replaces an existing image's URI and filename in the local collection.
     */
    @Synchronized
    fun replaceImage(
        imageId: String,
        newImageUri: String,
        newOriginalFilename: String
    ): ImageAssociation? {
        val existing = imagesById[imageId] ?: return null
        val cleanFilename = newOriginalFilename.substringAfterLast('/').substringAfterLast('\\').trim()
            .ifEmpty { existing.originalFilename }
        val unclear = ImageFilenameMatcher.isUnclearFilename(cleanFilename)
        val cleanDisplay = ImageFilenameMatcher.extractCleanBaseName(cleanFilename)
        val primaryKeyword = ImageFilenameMatcher.extractPrimaryKeyword(cleanFilename)
        val aliases = ImageFilenameMatcher.extractNormalizedAliases(cleanFilename)

        val updated = existing.copy(
            originalFilename = cleanFilename,
            displayName = cleanDisplay.ifEmpty { existing.displayName },
            extractedKeyword = primaryKeyword.ifEmpty { existing.extractedKeyword },
            normalizedAliases = if (aliases.isNotEmpty()) aliases else existing.normalizedAliases,
            imageUri = newImageUri,
            isFilenameUnclear = unclear,
            isBundledAsset = false
        )
        imagesById[imageId] = updated
        persistToPreferences()
        return updated
    }

    /**
     * Removes an image from the local collection.
     */
    @Synchronized
    fun removeImage(imageId: String): Boolean {
        val removed = imagesById.remove(imageId) != null
        if (removed) {
            persistToPreferences()
        }
        return removed
    }

    /**
     * Manually assigns or confirms an image for a specific mechanical part name.
     * Future voice or text entries for `partName` will automatically use this confirmed image.
     */
    @Synchronized
    fun assignImageToPart(partName: String, imageId: String): ImageAssociation? {
        val trimmedPart = partName.trim()
        if (trimmedPart.isEmpty()) return imagesById[imageId]
        val normalizedTarget = ImageFilenameMatcher.normalizePartQuery(trimmedPart)
        suppressedPartNames.remove(normalizedTarget)

        // Remove previous manual assignment for this part name from other images
        for ((id, assoc) in imagesById.entries) {
            if (id != imageId && assoc.userAssignedPartNames.any {
                    ImageFilenameMatcher.normalizePartQuery(it) == normalizedTarget
                }
            ) {
                imagesById[id] = assoc.copy(
                    userAssignedPartNames = assoc.userAssignedPartNames.filterNot {
                        ImageFilenameMatcher.normalizePartQuery(it) == normalizedTarget
                    }.toSet()
                )
            }
        }

        val target = imagesById[imageId] ?: return null
        val updated = target.copy(
            userAssignedPartNames = target.userAssignedPartNames + trimmedPart,
            isConfirmedByUser = true
        )
        imagesById[imageId] = updated
        persistToPreferences()
        return updated
    }

    /**
     * Removes an incorrect image association for `partName` so it won't auto-associate
     * unless manually reassigned by the user.
     */
    @Synchronized
    fun removePartImageAssociation(partName: String) {
        val trimmedPart = partName.trim()
        if (trimmedPart.isEmpty()) return
        val normalizedTarget = ImageFilenameMatcher.normalizePartQuery(trimmedPart)
        suppressedPartNames.add(normalizedTarget)

        for ((id, assoc) in imagesById.entries) {
            if (assoc.userAssignedPartNames.any {
                    ImageFilenameMatcher.normalizePartQuery(it) == normalizedTarget
                }
            ) {
                imagesById[id] = assoc.copy(
                    userAssignedPartNames = assoc.userAssignedPartNames.filterNot {
                        ImageFilenameMatcher.normalizePartQuery(it) == normalizedTarget
                    }.toSet()
                )
            }
        }
        persistToPreferences()
    }

    @Synchronized
    fun isPartAssociationSuppressed(partName: String): Boolean {
        val normalizedTarget = ImageFilenameMatcher.normalizePartQuery(partName)
        return normalizedTarget in suppressedPartNames
    }

    private fun persistToPreferences() {
        val sharedPrefs = prefs ?: return
        val editor = sharedPrefs.edit()
        editor.putStringSet(KEY_SUPPRESSED_PARTS, suppressedPartNames.toSet())

        // Persist userAssignedPartNames per imageId
        val assignedEntries = LinkedHashSet<String>()
        val customDeviceEntries = LinkedHashSet<String>()
        for (assoc in imagesById.values) {
            if (assoc.userAssignedPartNames.isNotEmpty()) {
                val joinedParts = assoc.userAssignedPartNames.joinToString(PART_DELIMITER)
                assignedEntries.add("${assoc.id}$FIELD_DELIMITER$joinedParts")
            }
            if (!assoc.isBundledAsset) {
                val joinedParts = assoc.userAssignedPartNames.joinToString(PART_DELIMITER)
                customDeviceEntries.add(
                    listOf(
                        assoc.id,
                        assoc.originalFilename,
                        assoc.imageUri,
                        joinedParts
                    ).joinToString(FIELD_DELIMITER)
                )
            }
        }
        editor.putStringSet(KEY_ASSIGNED_PARTS, assignedEntries)
        editor.putStringSet(KEY_CUSTOM_DEVICE_IMAGES, customDeviceEntries)
        editor.apply()
    }

    private fun restoreFromPreferences(sharedPrefs: SharedPreferences) {
        val savedSuppressed = sharedPrefs.getStringSet(KEY_SUPPRESSED_PARTS, emptySet()).orEmpty()
        suppressedPartNames.addAll(savedSuppressed)

        // Restore custom device images first
        val savedCustomImages = sharedPrefs.getStringSet(KEY_CUSTOM_DEVICE_IMAGES, emptySet()).orEmpty()
        for (encoded in savedCustomImages) {
            val fields = encoded.split(FIELD_DELIMITER)
            if (fields.size >= 3) {
                val customId = fields[0]
                val filename = fields[1]
                val uri = fields[2]
                val assignedParts = if (fields.size >= 4 && fields[3].isNotBlank()) {
                    fields[3].split(PART_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                } else {
                    emptySet()
                }
                val unclear = ImageFilenameMatcher.isUnclearFilename(filename)
                val cleanDisplay = ImageFilenameMatcher.extractCleanBaseName(filename)
                val primaryKeyword = ImageFilenameMatcher.extractPrimaryKeyword(filename)
                val aliases = ImageFilenameMatcher.extractNormalizedAliases(filename)
                imagesById[customId] = ImageAssociation(
                    id = customId,
                    originalFilename = filename,
                    displayName = if (assignedParts.isNotEmpty() && unclear) {
                        assignedParts.first()
                    } else {
                        cleanDisplay.ifEmpty { filename }
                    },
                    extractedKeyword = primaryKeyword,
                    normalizedAliases = aliases,
                    imageUri = uri,
                    isFilenameUnclear = unclear,
                    userAssignedPartNames = assignedParts,
                    isConfirmedByUser = assignedParts.isNotEmpty(),
                    isBundledAsset = false
                )
            }
        }

        // Restore assigned parts for bundled or existing images
        val savedAssignments = sharedPrefs.getStringSet(KEY_ASSIGNED_PARTS, emptySet()).orEmpty()
        for (encoded in savedAssignments) {
            val parts = encoded.split(FIELD_DELIMITER, limit = 2)
            if (parts.size == 2) {
                val imageId = parts[0]
                val assignedNames = parts[1].split(PART_DELIMITER)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                val existing = imagesById[imageId]
                if (existing != null && assignedNames.isNotEmpty()) {
                    imagesById[imageId] = existing.copy(
                        userAssignedPartNames = existing.userAssignedPartNames + assignedNames,
                        isConfirmedByUser = true
                    )
                }
            }
        }
    }

    companion object {
        const val ASSET_BASE_URI = "file:///android_asset/mechanical_parts/"
        const val DEFAULT_PREFS_NAME = "calculators_image_associations_prefs"
        private const val KEY_SUPPRESSED_PARTS = "suppressed_part_names"
        private const val KEY_ASSIGNED_PARTS = "assigned_part_mappings"
        private const val KEY_CUSTOM_DEVICE_IMAGES = "custom_device_images"
        private const val FIELD_DELIMITER = ":::"
        private const val PART_DELIMITER = "|||"

        private data class BundledImageSpec(
            val assetFilename: String,
            val originalUploadedFilename: String,
            val fallbackVisualKeywords: Set<String> = emptySet()
        )

        private val BUNDLED_SPECS = listOf(
            BundledImageSpec("Piston_272f9602.jpg", "Piston.jpg"),
            BundledImageSpec("666_paste_3935fc0a.jpg", "666 paste.jpg"),
            BundledImageSpec("Bearing__c92d9f7b.jpg", "Bearing .jpg"),
            BundledImageSpec("Head__95b2f7b7.jpg", "Head .jpg"),
            BundledImageSpec("Rings_9e5dceb8.png", "Rings.png"),
            BundledImageSpec("Gasket_b5e0fa24.jpg", "Gasket.jpg"),
            BundledImageSpec("bush_4ff22f82.jpg", "bush.jpg"),
            BundledImageSpec("Filter_decb2438.jpg", "Filter.jpg"),
            BundledImageSpec(
                "motor-clutch-centrifugal-plates_0213e051.jpg",
                "motor-clutch-centrifugal-plates.jpg"
            ),
            BundledImageSpec("Connectingrod_f6b2a37c.jpg", "Connectingrod.jpg"),
            BundledImageSpec("olisteel_30b48cf2.jpg", "olisteel.jpg"),
            BundledImageSpec("Lathework__d9f6f10b.jpg", "Lathework .jpg"),
            BundledImageSpec("Fan_7ed111d5.jpg", "Fan.jpg"),
            BundledImageSpec("steelbush_65c757f8.png", "steelbush.png"),
            BundledImageSpec("sheet_594ed9e8.jpg", "sheet.jpg"),
            BundledImageSpec("steel_plate_163d9de5.jpg", "steel plate.jpg"),
            BundledImageSpec("connesser_b86e6129.jpg", "connesser.jpg"),
            BundledImageSpec("clamp_0a8849cf.jpg", "clamp.jpg"),
            BundledImageSpec("welding_3e11cb44.jpg", "welding.jpg"),
            BundledImageSpec("wireing_499e41e6.jpg", "wireing.jpg"),
            // 4 uploaded images with unclear IMG_<uuid> filenames -> fallback visual classification keywords
            BundledImageSpec(
                "IMG_a80d976b-8427-433c-83b0-8a08e5af66fc_0c936aa9.jpg",
                "IMG_a80d976b-8427-433c-83b0-8a08e5af66fc.jpg",
                setOf("bolt", "nut", "nut bolt", "hex bolt")
            ),
            BundledImageSpec(
                "IMG_c0738fdd-28fe-4951-9704-0f14d55e5d8f_6b4acf36.jpg",
                "IMG_c0738fdd-28fe-4951-9704-0f14d55e5d8f.jpg",
                setOf("flat washer", "washer")
            ),
            BundledImageSpec(
                "IMG_e0f5296f-4303-4c65-937f-e8c6303263e8_ceab1193.jpg",
                "IMG_e0f5296f-4303-4c65-937f-e8c6303263e8.jpg",
                setOf("spring washer", "lock washer")
            ),
            BundledImageSpec(
                "IMG_cf7ce1bc-2900-4235-8bf3-54126f3d63e3_8231c086.jpg",
                "IMG_cf7ce1bc-2900-4235-8bf3-54126f3d63e3.jpg",
                setOf("oil glass", "sight glass", "oil sight glass", "oil level gauge")
            )
        )

        fun defaultBundledMechanicalImages(): List<ImageAssociation> {
            return BUNDLED_SPECS.map { spec ->
                val unclear = ImageFilenameMatcher.isUnclearFilename(spec.originalUploadedFilename)
                val cleanDisplay = ImageFilenameMatcher.extractCleanBaseName(spec.originalUploadedFilename)
                val primaryKeyword = ImageFilenameMatcher.extractPrimaryKeyword(spec.originalUploadedFilename)
                val aliases = ImageFilenameMatcher.extractNormalizedAliases(spec.originalUploadedFilename)

                ImageAssociation(
                    id = "bundled_${spec.assetFilename}",
                    originalFilename = spec.originalUploadedFilename,
                    displayName = cleanDisplay,
                    extractedKeyword = primaryKeyword,
                    normalizedAliases = aliases,
                    imageUri = "$ASSET_BASE_URI${spec.assetFilename}",
                    isFilenameUnclear = unclear,
                    contentFallbackKeywords = spec.fallbackVisualKeywords,
                    userAssignedPartNames = emptySet(),
                    isConfirmedByUser = false,
                    isBundledAsset = true
                )
            }
        }
    }
}
