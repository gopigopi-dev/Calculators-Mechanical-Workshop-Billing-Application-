package com.example.calculators.textprocessing

import java.math.BigDecimal

/**
 * Resolves the contextual relationship between mechanical part/machine names, optional quantities,
 * and amounts across Tamil and English inputs.
 *
 * Supported patterns:
 * - Part with Amount only (Quantity omitted): "Spindle 200", "Head ₹200", "Bearing two hundred"
 * - Part before Quantity and Amount: "Spindle 2 200", "Head two two hundred"
 * - Quantity before Part name: "Two Head two hundred, one Paste one hundred"
 * - Amount before Part name: "₹200 Head 2", "₹200 Spindle"
 * - Multiple parts in a single sentence or across comma-separated clauses
 * - Detects missing amounts or ambiguous numeric fields without inventing values
 */
class ContextResolver(
    private val productIdentifier: ProductIdentifier = ProductIdentifier()
) {

    private sealed interface ClauseElement {
        val hasClauseBreakAfter: Boolean

        data class ProductElement(
            val rawWords: List<String>,
            override val hasClauseBreakAfter: Boolean
        ) : ClauseElement {
            val phrase: String
                get() = rawWords.joinToString(" ")
        }

        data class NumberItem(
            val numberToken: NormalizedNumberToken,
            val hasExplicitAmountMarker: Boolean,
            val hasExplicitQuantityMarker: Boolean,
            val isExplicitUnitPrice: Boolean,
            override val hasClauseBreakAfter: Boolean
        ) : ClauseElement
    }

    fun resolve(
        rawTranscript: String,
        processedTranscript: ProcessedTranscript
    ): List<ParsedProduct> {
        if (processedTranscript.tokens.isEmpty()) {
            return emptyList()
        }

        val elements = buildClauseElements(rawTranscript, processedTranscript)
        if (elements.isEmpty()) {
            return emptyList()
        }

        // Step 1: Split into primary clauses at explicit punctuation breaks (commas, semicolons, newlines)
        val primaryClauses = splitAtClauseBreaks(elements)

        // Step 2: Further segment any multi-part clause into single-part segments
        val singleProductSegments = primaryClauses.flatMap { clause ->
            segmentMultiProductClause(clause)
        }

        // Step 3: Resolve each segment into a ParsedProduct with confidence & validation status
        return singleProductSegments.mapNotNull { segment ->
            resolveSingleSegment(segment)
        }
    }

    private fun buildClauseElements(
        rawTranscript: String,
        processed: ProcessedTranscript
    ): List<ClauseElement> {
        val tokens = processed.tokens
        val numbersByStart = processed.normalizedNumbers.associateBy { it.startTokenIndex }
        val rawHasRupeeBeforeToken = detectRupeePrefixedTokens(rawTranscript)

        val elements = mutableListOf<ClauseElement>()
        var pendingWordTokens = mutableListOf<String>()
        var pendingWordBreak = false
        var nextNumberHasAmountMarker = false
        var nextNumberHasQuantityMarker = false
        var nextNumberIsUnitPrice = false

        fun flushPendingWords() {
            if (pendingWordTokens.isNotEmpty()) {
                elements.add(
                    ClauseElement.ProductElement(
                        rawWords = pendingWordTokens.toList(),
                        hasClauseBreakAfter = pendingWordBreak
                    )
                )
                pendingWordTokens = mutableListOf()
                pendingWordBreak = false
            }
        }

        var idx = 0
        while (idx < tokens.size) {
            val token = tokens[idx]
            val numberToken = numbersByStart[idx]

            if (numberToken != null) {
                flushPendingWords()

                // Check if the token right after this number is an explicit marker (e.g. "200 rupees", "100 each")
                val endIdx = numberToken.endTokenIndex
                var breakAfter = tokens[endIdx].hasClauseBreakAfter
                var postAmountMarker = false
                var postQuantityMarker = false
                var postUnitPriceMarker = false

                if (!breakAfter && endIdx + 1 < tokens.size && numbersByStart[endIdx + 1] == null) {
                    val nextWord = tokens[endIdx + 1].rawText
                    when {
                        productIdentifier.isUnitPriceMarker(nextWord) -> {
                            postUnitPriceMarker = true
                            breakAfter = tokens[endIdx + 1].hasClauseBreakAfter
                            idx = endIdx + 1
                        }
                        productIdentifier.isExplicitAmountMarker(nextWord) -> {
                            postAmountMarker = true
                            breakAfter = tokens[endIdx + 1].hasClauseBreakAfter
                            idx = endIdx + 1
                        }
                        productIdentifier.isExplicitQuantityMarker(nextWord) -> {
                            postQuantityMarker = true
                            breakAfter = tokens[endIdx + 1].hasClauseBreakAfter
                            idx = endIdx + 1
                        }
                        else -> {
                            idx = endIdx
                        }
                    }
                } else {
                    idx = endIdx
                }

                val hasRupeeSymbol = numberToken.originalExpression in rawHasRupeeBeforeToken
                elements.add(
                    ClauseElement.NumberItem(
                        numberToken = numberToken,
                        hasExplicitAmountMarker = nextNumberHasAmountMarker || postAmountMarker || hasRupeeSymbol,
                        hasExplicitQuantityMarker = nextNumberHasQuantityMarker || postQuantityMarker,
                        isExplicitUnitPrice = nextNumberIsUnitPrice || postUnitPriceMarker,
                        hasClauseBreakAfter = breakAfter
                    )
                )
                nextNumberHasAmountMarker = false
                nextNumberHasQuantityMarker = false
                nextNumberIsUnitPrice = false
                idx++
                continue
            }

            val word = token.rawText
            when {
                productIdentifier.isUnitPriceMarker(word) -> {
                    nextNumberIsUnitPrice = true
                    if (token.hasClauseBreakAfter && elements.isNotEmpty()) {
                        markLastElementBreak(elements)
                    }
                }
                productIdentifier.isExplicitAmountMarker(word) -> {
                    nextNumberHasAmountMarker = true
                    if (token.hasClauseBreakAfter && elements.isNotEmpty()) {
                        markLastElementBreak(elements)
                    }
                }
                productIdentifier.isExplicitQuantityMarker(word) -> {
                    nextNumberHasQuantityMarker = true
                    if (token.hasClauseBreakAfter && elements.isNotEmpty()) {
                        markLastElementBreak(elements)
                    }
                }
                productIdentifier.isStructuralNoiseWord(word) -> {
                    if (token.hasClauseBreakAfter) {
                        if (pendingWordTokens.isNotEmpty()) {
                            pendingWordBreak = true
                            flushPendingWords()
                        } else if (elements.isNotEmpty()) {
                            markLastElementBreak(elements)
                        }
                    }
                }
                else -> {
                    pendingWordTokens.add(word)
                    if (token.hasClauseBreakAfter) {
                        pendingWordBreak = true
                        flushPendingWords()
                    }
                }
            }
            idx++
        }

        flushPendingWords()
        return elements
    }

    private fun markLastElementBreak(elements: MutableList<ClauseElement>) {
        val lastIdx = elements.lastIndex
        if (lastIdx < 0) return
        elements[lastIdx] = when (val last = elements[lastIdx]) {
            is ClauseElement.ProductElement -> last.copy(hasClauseBreakAfter = true)
            is ClauseElement.NumberItem -> last.copy(hasClauseBreakAfter = true)
        }
    }

    private fun detectRupeePrefixedTokens(rawTranscript: String): Set<String> {
        val regex = Regex("(?:₹|Rs\\.?|INR)\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        return regex.findAll(rawTranscript).map { it.groupValues[1] }.toSet()
    }

    private fun splitAtClauseBreaks(elements: List<ClauseElement>): List<List<ClauseElement>> {
        val clauses = mutableListOf<List<ClauseElement>>()
        var current = mutableListOf<ClauseElement>()

        for (element in elements) {
            current.add(element)
            if (element.hasClauseBreakAfter) {
                clauses.add(current)
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) {
            clauses.add(current)
        }
        return clauses
    }

    /**
     * Segments a clause that may contain multiple part entries (without comma breaks) into
     * individual per-part segments using contextual number counting.
     */
    private fun segmentMultiProductClause(clause: List<ClauseElement>): List<List<ClauseElement>> {
        val productIndices = clause.indices.filter { clause[it] is ClauseElement.ProductElement }
        if (productIndices.size <= 1) {
            return listOf(clause)
        }

        val segments = mutableListOf<List<ClauseElement>>()
        var cursor = 0

        for (pIdxPos in productIndices.indices) {
            val prodIndex = productIndices[pIdxPos]
            val nextProdIndex = if (pIdxPos + 1 < productIndices.size) {
                productIndices[pIdxPos + 1]
            } else {
                clause.size
            }

            // Count how many numbers are already included before prodIndex in this segment
            val numbersBeforeProduct = (cursor until prodIndex).count { clause[it] is ClauseElement.NumberItem }
            val numbersNeededAfter = (2 - numbersBeforeProduct).coerceAtLeast(0)

            val availableAfterBeforeNextProd = nextProdIndex - (prodIndex + 1)
            val numbersToTakeAfter = if (pIdxPos == productIndices.lastIndex) {
                availableAfterBeforeNextProd
            } else {
                val numbersAfterNextProd = countNumbersImmediatelyAfter(clause, nextProdIndex)
                if (numbersAfterNextProd == 1 && availableAfterBeforeNextProd > numbersNeededAfter) {
                    numbersNeededAfter
                } else {
                    availableAfterBeforeNextProd.coerceAtMost(numbersNeededAfter.coerceAtLeast(2))
                }
            }

            val segmentEndExclusive = (prodIndex + 1 + numbersToTakeAfter).coerceAtMost(nextProdIndex)
            segments.add(clause.subList(cursor, segmentEndExclusive))
            cursor = segmentEndExclusive
        }

        if (cursor < clause.size && segments.isNotEmpty()) {
            val lastSeg = segments.removeAt(segments.lastIndex)
            segments.add(lastSeg + clause.subList(cursor, clause.size))
        }

        return segments
    }

    private fun countNumbersImmediatelyAfter(clause: List<ClauseElement>, prodIndex: Int): Int {
        var count = 0
        var i = prodIndex + 1
        while (i < clause.size && clause[i] is ClauseElement.NumberItem) {
            count++
            i++
        }
        return count
    }

    private fun resolveSingleSegment(segment: List<ClauseElement>): ParsedProduct? {
        if (segment.isEmpty()) return null

        val productElements = segment.filterIsInstance<ClauseElement.ProductElement>()
        val numberItems = segment.filterIsInstance<ClauseElement.NumberItem>()

        val combinedProductRaw = productElements.joinToString(" ") { it.phrase }.trim()
        val rawSegmentText = segment.joinToString(" ") { elem ->
            when (elem) {
                is ClauseElement.ProductElement -> elem.phrase
                is ClauseElement.NumberItem -> elem.numberToken.originalExpression
            }
        }

        // Case 1: Numbers exist but no part/machine name was provided
        if (combinedProductRaw.isEmpty()) {
            val firstQty = if (numberItems.size >= 2) {
                QuantityExtractor.extractQuantity(numberItems.getOrNull(0)?.numberToken)
            } else {
                ExtractedQuantity.omitted()
            }
            val amtToken = if (numberItems.size >= 2) {
                numberItems.getOrNull(1)?.numberToken
            } else {
                numberItems.getOrNull(0)?.numberToken
            }
            val extractedAmt = AmountExtractor.extractAmount(amtToken)
            return ParsedProduct(
                rawSegmentText = rawSegmentText,
                productName = "",
                productIdentificationStatus = ProductIdentificationStatus.UNIDENTIFIED,
                productStatus = FieldExtractionStatus.MISSING,
                extractedQuantity = firstQty,
                extractedAmount = extractedAmt,
                overallStatus = OverallParseStatus.INCOMPLETE,
                statusMessage = "Part or machine name is missing for: $rawSegmentText"
            )
        }

        val matchResult = productIdentifier.identifyProductName(combinedProductRaw)
        val productFieldStatus = when (matchResult.status) {
            ProductIdentificationStatus.EXACT_MATCH -> FieldExtractionStatus.CONFIDENT
            ProductIdentificationStatus.FUZZY_MATCH -> FieldExtractionStatus.FUZZY_MATCH
            ProductIdentificationStatus.USER_DEFINED -> FieldExtractionStatus.USER_DEFINED
            ProductIdentificationStatus.UNIDENTIFIED -> FieldExtractionStatus.MISSING
        }

        // Case 2: Part/machine name with 0 numbers -> Missing Amount
        if (numberItems.isEmpty()) {
            return ParsedProduct(
                rawSegmentText = rawSegmentText,
                productName = matchResult.resolvedProductName,
                productIdentificationStatus = matchResult.status,
                productStatus = productFieldStatus,
                extractedQuantity = ExtractedQuantity.omitted(),
                extractedAmount = ExtractedAmount(status = FieldExtractionStatus.MISSING),
                overallStatus = OverallParseStatus.INCOMPLETE,
                statusMessage = "Missing amount for '${matchResult.resolvedProductName}'"
            )
        }

        // Case 3: Part/machine name with 1 number
        // Rule: Quantity is optional and Amount can be entered independently!
        // Unless the single number was explicitly marked as a quantity-only value (e.g. "Spindle qty 2"),
        // treat the single number as the independent Amount with Quantity = null (omitted).
        if (numberItems.size == 1) {
            val onlyItem = numberItems[0]
            val isExplicitQuantityOnly =
                onlyItem.hasExplicitQuantityMarker && !onlyItem.hasExplicitAmountMarker

            return if (isExplicitQuantityOnly) {
                ParsedProduct(
                    rawSegmentText = rawSegmentText,
                    productName = matchResult.resolvedProductName,
                    productIdentificationStatus = matchResult.status,
                    productStatus = productFieldStatus,
                    extractedQuantity = QuantityExtractor.extractQuantity(onlyItem.numberToken),
                    extractedAmount = ExtractedAmount(status = FieldExtractionStatus.MISSING),
                    overallStatus = OverallParseStatus.INCOMPLETE,
                    statusMessage = "Missing amount for '${matchResult.resolvedProductName}'"
                )
            } else {
                val extractedAmount = AmountExtractor.extractAmount(
                    onlyItem.numberToken,
                    onlyItem.isExplicitUnitPrice
                )
                val isValidAmountEntry =
                    matchResult.status != ProductIdentificationStatus.UNIDENTIFIED &&
                        extractedAmount.status == FieldExtractionStatus.CONFIDENT

                ParsedProduct(
                    rawSegmentText = rawSegmentText,
                    productName = matchResult.resolvedProductName,
                    productIdentificationStatus = matchResult.status,
                    productStatus = productFieldStatus,
                    extractedQuantity = ExtractedQuantity.omitted(),
                    extractedAmount = extractedAmount,
                    overallStatus = if (isValidAmountEntry) {
                        OverallParseStatus.CONFIDENT
                    } else {
                        OverallParseStatus.INCOMPLETE
                    },
                    statusMessage = if (isValidAmountEntry) {
                        "Ready"
                    } else {
                        "Please verify amount for '${matchResult.resolvedProductName}'"
                    }
                )
            }
        }

        // Case 4: Part/machine name with 3 or more numbers -> Ambiguous
        if (numberItems.size >= 3) {
            val tentativeQty = QuantityExtractor.extractQuantity(numberItems.first().numberToken)
                .copy(status = FieldExtractionStatus.AMBIGUOUS)
            val tentativeAmt = AmountExtractor.extractAmount(
                numberItems.last().numberToken,
                numberItems.last().isExplicitUnitPrice
            ).copy(status = FieldExtractionStatus.AMBIGUOUS)

            return ParsedProduct(
                rawSegmentText = rawSegmentText,
                productName = matchResult.resolvedProductName,
                productIdentificationStatus = matchResult.status,
                productStatus = productFieldStatus,
                extractedQuantity = tentativeQty,
                extractedAmount = tentativeAmt,
                overallStatus = OverallParseStatus.AMBIGUOUS,
                statusMessage = "Multiple numeric interpretations possible for '${matchResult.resolvedProductName}'. Please confirm."
            )
        }

        // Case 5: Exactly 2 numbers associated with the part/machine name
        val firstNum = numberItems[0]
        val secondNum = numberItems[1]
        val productIndexInSegment = segment.indexOfFirst { it is ClauseElement.ProductElement }
        val firstNumIndexInSegment = segment.indexOfFirst { it === firstNum }
        val secondNumIndexInSegment = segment.indexOfFirst { it === secondNum }

        // Determine if Amount came before Quantity (e.g., "₹200 Head 2" or "200 rupees Head 2" or "200 Head 2")
        val firstIsExplicitAmount = firstNum.hasExplicitAmountMarker && !secondNum.hasExplicitAmountMarker
        val secondIsExplicitQuantity = secondNum.hasExplicitQuantityMarker && !firstNum.hasExplicitQuantityMarker
        val isAmountBeforeProductAndSmallQuantityAfter =
            firstNumIndexInSegment < productIndexInSegment &&
                secondNumIndexInSegment > productIndexInSegment &&
                firstNum.numberToken.numericValue >= BigDecimal.valueOf(100) &&
                secondNum.numberToken.numericValue <= BigDecimal.valueOf(25)

        val (quantityItem, amountItem) = if (
            firstIsExplicitAmount ||
            secondIsExplicitQuantity ||
            isAmountBeforeProductAndSmallQuantityAfter
        ) {
            secondNum to firstNum
        } else {
            firstNum to secondNum
        }

        val extractedQuantity = QuantityExtractor.extractQuantity(quantityItem.numberToken)
        val extractedAmount = AmountExtractor.extractAmount(
            amountItem.numberToken,
            amountItem.isExplicitUnitPrice
        )

        val isCompleteAndValid =
            matchResult.status != ProductIdentificationStatus.UNIDENTIFIED &&
                extractedQuantity.status == FieldExtractionStatus.CONFIDENT &&
                extractedAmount.status == FieldExtractionStatus.CONFIDENT

        val overallStatus = if (isCompleteAndValid) {
            OverallParseStatus.CONFIDENT
        } else {
            OverallParseStatus.AMBIGUOUS
        }

        return ParsedProduct(
            rawSegmentText = rawSegmentText,
            productName = matchResult.resolvedProductName,
            productIdentificationStatus = matchResult.status,
            productStatus = productFieldStatus,
            extractedQuantity = extractedQuantity,
            extractedAmount = extractedAmount,
            overallStatus = overallStatus,
            statusMessage = if (overallStatus == OverallParseStatus.CONFIDENT) {
                "Ready"
            } else {
                "Please verify details for '${matchResult.resolvedProductName}'"
            }
        )
    }
}
