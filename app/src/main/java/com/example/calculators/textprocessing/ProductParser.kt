package com.example.calculators.textprocessing

import com.example.calculators.image.MechanicalPartImageMatcher

/**
 * Phase 8 & Phase 9: High-level coordinator that runs:
 * TextNormalizer -> ProductIdentifier -> QuantityExtractor -> AmountExtractor -> ContextResolver
 * -> MechanicalPartImageMatcher to produce a structured ParsingResult with matched mechanical part images.
 */
class ProductParser(
    val dictionary: ProductDictionary = ProductDictionary(),
    val imageMatcher: MechanicalPartImageMatcher = MechanicalPartImageMatcher(),
    private val productIdentifier: ProductIdentifier = ProductIdentifier(dictionary),
    private val contextResolver: ContextResolver = ContextResolver(productIdentifier)
) {

    fun parse(
        rawTranscript: String,
        preservedOriginal: String = rawTranscript
    ): ParsingResult {
        val processed = TextNormalizer.processTranscript(
            rawTranscript = rawTranscript,
            preservedOriginal = preservedOriginal
        )

        if (processed.isEmpty || processed.tokens.isEmpty()) {
            return ParsingResult(
                originalTranscript = preservedOriginal,
                processedTranscript = processed,
                parsedProducts = emptyList(),
                warnings = processed.warnings
            )
        }

        val resolvedProducts = contextResolver.resolve(
            rawTranscript = rawTranscript,
            processedTranscript = processed
        )

        // Phase 9: Automatically identify matching mechanical part images for each extracted part
        // (independent of whether Quantity or Amount was provided)
        val parsedProducts = resolvedProducts.map { parsed ->
            if (parsed.productName.isNotBlank()) {
                val imageResult = imageMatcher.identifyImageForPart(parsed.productName)
                parsed.copy(imageIdentification = imageResult)
            } else {
                parsed
            }
        }

        val resolutionWarnings = parsedProducts
            .filter { !it.isReadyForAutoInsert && it.statusMessage.isNotBlank() }
            .map { it.statusMessage }

        val imageWarnings = parsedProducts
            .filter { it.imageIdentification.requiresUserConfirmation && it.imageIdentification.statusMessage.isNotBlank() }
            .map { it.imageIdentification.statusMessage }

        return ParsingResult(
            originalTranscript = preservedOriginal,
            processedTranscript = processed,
            parsedProducts = parsedProducts,
            warnings = processed.warnings + resolutionWarnings + imageWarnings
        )
    }

    fun registerUserProduct(productName: String) {
        dictionary.registerUserDefinedProduct(productName)
    }
}
