package com.example.calculators.textprocessing

import java.math.BigDecimal

data class NormalizedNumberToken(
    val originalExpression: String,
    val normalizedValue: String,
    val numericValue: BigDecimal,
    val startTokenIndex: Int,
    val endTokenIndex: Int
)

data class NumberNormalizationResult(
    val normalizedText: String,
    val normalizedNumbers: List<NormalizedNumberToken>,
    val preservedProductWords: List<String>,
    val warnings: List<String>
)

object NumberNormalizer {

    // English single-word unit and teen numbers (0..19)
    private val englishUnitsAndTeens = mapOf(
        "zero" to 0L,
        "one" to 1L,
        "two" to 2L,
        "three" to 3L,
        "four" to 4L,
        "five" to 5L,
        "six" to 6L,
        "seven" to 7L,
        "eight" to 8L,
        "nine" to 9L,
        "ten" to 10L,
        "eleven" to 11L,
        "twelve" to 12L,
        "thirteen" to 13L,
        "fourteen" to 14L,
        "fifteen" to 15L,
        "sixteen" to 16L,
        "seventeen" to 17L,
        "eighteen" to 18L,
        "nineteen" to 19L
    )

    // English tens words (20..90)
    private val englishTens = mapOf(
        "twenty" to 20L,
        "thirty" to 30L,
        "forty" to 40L,
        "fifty" to 50L,
        "sixty" to 60L,
        "seventy" to 70L,
        "eighty" to 80L,
        "ninety" to 90L
    )

    // English scale words
    private val englishScales = mapOf(
        "hundred" to 100L,
        "thousand" to 1000L
    )

    // Tamil number words (standard literary and spoken billing variants)
    private val tamilNumbers = mapOf(
        "பூஜ்யம்" to 0L,
        "சுழியம்" to 0L,
        "ஒன்று" to 1L,
        "ஒன்னு" to 1L,
        "ஓர்" to 1L,
        "ஒரு" to 1L,
        "இரண்டு" to 2L,
        "ரெண்டு" to 2L,
        "மூன்று" to 3L,
        "மூணு" to 3L,
        "நான்கு" to 4L,
        "நாலு" to 4L,
        "ஐந்து" to 5L,
        "அஞ்சு" to 5L,
        "ஆறு" to 6L,
        "ஏழு" to 7L,
        "எட்டு" to 8L,
        "ஒன்பது" to 9L,
        "பத்து" to 10L,
        "பதினொன்று" to 11L,
        "பன்னிரண்டு" to 12L,
        "பதின்மூன்று" to 13L,
        "பதினான்கு" to 14L,
        "பதினைந்து" to 15L,
        "பதினாறு" to 16L,
        "பதினேழு" to 17L,
        "பதினெட்டு" to 18L,
        "பத்தொன்பது" to 19L,
        "இருபது" to 20L,
        "முப்பது" to 30L,
        "நாற்பது" to 40L,
        "ஐம்பது" to 50L,
        "அறுபது" to 60L,
        "எழுபது" to 70L,
        "எண்பது" to 80L,
        "தொண்ணூறு" to 90L,
        "நூறு" to 100L,
        "இருநூறு" to 200L,
        "முந்நூறு" to 300L,
        "முன்னூறு" to 300L,
        "நானூறு" to 400L,
        "ஐந்நூறு" to 500L,
        "ஐநூறு" to 500L,
        "ஆறுநூறு" to 600L,
        "அறுநூறு" to 600L,
        "எழுநூறு" to 700L,
        "எண்ணூறு" to 800L,
        "தொள்ளாயிரம்" to 900L,
        "ஆயிரம்" to 1000L,
        "இரண்டாயிரம்" to 2000L,
        "ஐந்தாயிரம்" to 5000L
    )

    private val tamilFractions = mapOf(
        "கால்" to BigDecimal("0.25"),
        "அரை" to BigDecimal("0.5"),
        "முக்கால்" to BigDecimal("0.75")
    )

    private val decimalMarkerWords = setOf("point", "புள்ளி")
    private val ignoredCurrencyWords = setOf(
        "rupees", "rupee", "rs", "inr", "ரூபாய்", "ரூ"
    )

    private val numericLiteralRegex = Regex("^\\d+(\\.\\d+)?$")
    private val incompleteNumericRegex = Regex("^(\\d+\\.|\\.\\d+|\\d+\\.\\d+\\..*)$")

    /**
     * Recognizes supported Tamil and English number expressions and prepares normalized
     * numeric representations while preserving product words in their original script.
     */
    fun normalizeNumbers(tokens: List<TranscriptToken>): NumberNormalizationResult {
        if (tokens.isEmpty()) {
            return NumberNormalizationResult(
                normalizedText = "",
                normalizedNumbers = emptyList(),
                preservedProductWords = emptyList(),
                warnings = emptyList()
            )
        }

        val normalizedNumbers = mutableListOf<NormalizedNumberToken>()
        val preservedProductWords = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val outputTokens = mutableListOf<String>()

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            val raw = token.rawText
            val lower = raw.lowercase()

            // Skip standalone currency words so they don't pollute product words
            if (lower in ignoredCurrencyWords) {
                i++
                continue
            }

            // Check for malformed numeric literals like "12." or ".5"
            if (incompleteNumericRegex.matches(raw)) {
                warnings.add("Incomplete numeric expression: '$raw'")
                outputTokens.add(raw)
                i++
                continue
            }

            // Check for standalone trailing/unattached decimal marker word ("point" / "புள்ளி")
            if (lower in decimalMarkerWords) {
                warnings.add("Incomplete decimal expression at: '$raw'")
                outputTokens.add(raw)
                i++
                continue
            }

            if (isNumberToken(raw)) {
                // Collect contiguous run of number tokens, stopping at clause breaks (e.g. commas)
                val runStart = i
                var runEnd = i
                while (!tokens[runEnd].hasClauseBreakAfter && runEnd + 1 < tokens.size) {
                    val nextRaw = tokens[runEnd + 1].rawText
                    val nextLower = nextRaw.lowercase()
                    if (isNumberToken(nextRaw)) {
                        runEnd++
                    } else if (nextLower in decimalMarkerWords) {
                        if (runEnd + 2 < tokens.size && isSingleNumberValue(tokens[runEnd + 2].rawText) != null) {
                            runEnd += 2
                        } else {
                            warnings.add("Incomplete decimal expression after '${tokens[runEnd].rawText} $nextRaw'")
                            break
                        }
                    } else {
                        break
                    }
                }

                // Check if the product word immediately preceding this run already has a standalone quantity before it
                // (e.g., "one Paste one hundred" -> "Paste" is preceded by standalone "one")
                val productPrecededByStandaloneQuantity = isPrecededByQuantityAndProduct(tokens, runStart)

                val runTokens = tokens.subList(runStart, runEnd + 1)
                val parsedRun = parseContiguousNumberRun(
                    run = runTokens,
                    forceCombineOneHundred = productPrecededByStandaloneQuantity
                )
                for (numToken in parsedRun) {
                    normalizedNumbers.add(numToken)
                    outputTokens.add(numToken.normalizedValue)
                }
                i = runEnd + 1
            } else {
                // Preserve product word in its exact original script and casing
                preservedProductWords.add(raw)
                outputTokens.add(raw)
                i++
            }
        }

        return NumberNormalizationResult(
            normalizedText = outputTokens.joinToString(" "),
            normalizedNumbers = normalizedNumbers,
            preservedProductWords = preservedProductWords,
            warnings = warnings
        )
    }

    /**
     * Checks whether `runStart` is immediately preceded by `<singleNumber> <productWord(s)>`
     * within the same clause (such as `"Two Head two hundred, one Paste one hundred"`).
     */
    private fun isPrecededByQuantityAndProduct(
        tokens: List<TranscriptToken>,
        runStart: Int
    ): Boolean {
        if (runStart < 2) return false
        // Walk backwards across non-number product words before runStart, stopping at clause break
        var wordIdx = runStart - 1
        if (isNumberToken(tokens[wordIdx].rawText) || tokens[wordIdx].hasClauseBreakAfter) {
            return false
        }
        while (wordIdx - 1 >= 0 &&
            !isNumberToken(tokens[wordIdx - 1].rawText) &&
            !tokens[wordIdx - 1].hasClauseBreakAfter
        ) {
            wordIdx--
        }
        val numIdx = wordIdx - 1
        if (numIdx < 0) return false
        if (tokens[numIdx].hasClauseBreakAfter) return false
        if (!isNumberToken(tokens[numIdx].rawText)) return false

        // Ensure that number before the product word was either a single standalone number
        // OR immediately followed a scale/hundred word (e.g. "Two Head two hundred one Paste one hundred")
        val prevIsNumberInSameClause =
            numIdx - 1 >= 0 &&
                !tokens[numIdx - 1].hasClauseBreakAfter &&
                isNumberToken(tokens[numIdx - 1].rawText)

        if (!prevIsNumberInSameClause) {
            return true
        }

        val prevRaw = tokens[numIdx - 1].rawText
        val prevVal = isSingleNumberValue(prevRaw)
        return prevVal != null && prevVal >= BigDecimal.valueOf(100)
    }

    private fun parseContiguousNumberRun(
        run: List<TranscriptToken>,
        forceCombineOneHundred: Boolean
    ): List<NormalizedNumberToken> {
        val results = mutableListOf<NormalizedNumberToken>()
        var idx = 0

        while (idx < run.size) {
            val current = run[idx]
            val currentRaw = current.rawText
            val currentLower = currentRaw.lowercase()

            // 1. Decimal expression: <number> ("point" | "புள்ளி") <number>
            if (idx + 2 < run.size && run[idx + 1].rawText.lowercase() in decimalMarkerWords) {
                val wholePart = isSingleNumberValue(currentRaw)
                val fracPart = isSingleNumberValue(run[idx + 2].rawText)
                if (wholePart != null && fracPart != null) {
                    val combinedStr = "${wholePart.toPlainString()}.${fracPart.toPlainString().replace(".", "")}"
                    val bd = combinedStr.toBigDecimalOrNull()
                    if (bd != null) {
                        val plain = formatNormalizedDecimal(bd)
                        val expr = "$currentRaw ${run[idx + 1].rawText} ${run[idx + 2].rawText}"
                        results.add(
                            NormalizedNumberToken(
                                originalExpression = expr,
                                normalizedValue = plain,
                                numericValue = bd,
                                startTokenIndex = current.index,
                                endTokenIndex = run[idx + 2].index
                            )
                        )
                        idx += 3
                        continue
                    }
                }
            }

            // 2. Special billing pattern: 2-token run ["one", "hundred"] -> [1, 100] when NOT preceded by `<qty> <product>`
            if (!forceCombineOneHundred &&
                run.size == 2 &&
                idx == 0 &&
                currentLower == "one" &&
                run[1].rawText.lowercase() in englishScales
            ) {
                val scaleVal = englishScales.getValue(run[1].rawText.lowercase())
                results.add(
                    NormalizedNumberToken(
                        originalExpression = currentRaw,
                        normalizedValue = "1",
                        numericValue = BigDecimal.ONE,
                        startTokenIndex = current.index,
                        endTokenIndex = current.index
                    )
                )
                results.add(
                    NormalizedNumberToken(
                        originalExpression = run[1].rawText,
                        normalizedValue = scaleVal.toString(),
                        numericValue = BigDecimal.valueOf(scaleVal),
                        startTokenIndex = run[1].index,
                        endTokenIndex = run[1].index
                    )
                )
                idx += 2
                continue
            }

            // 3. Multiplier + Scale word: e.g., ["two", "hundred"] -> 200, or ["one", "hundred"] -> 100 when preceded by another quantity
            val unitOrTeen = englishUnitsAndTeens[currentLower] ?: tamilNumbers[currentRaw]?.takeIf { it in 1L..19L }
            if (unitOrTeen != null && unitOrTeen in 1L..19L && idx + 1 < run.size) {
                val nextLower = run[idx + 1].rawText.lowercase()
                val nextScale = englishScales[nextLower] ?: tamilNumbers[run[idx + 1].rawText]?.takeIf { it == 100L || it == 1000L }

                val isTamilOneBeforeHundred =
                    !forceCombineOneHundred &&
                        (currentRaw in setOf("ஒன்று", "ஒன்னு")) &&
                        (run[idx + 1].rawText == "நூறு") &&
                        run.size == 2 &&
                        idx == 0

                if (nextScale != null && !isTamilOneBeforeHundred) {
                    var totalValue = unitOrTeen * nextScale
                    var endIdx = idx + 1
                    val exprParts = mutableListOf(currentRaw, run[idx + 1].rawText)

                    // Check if followed by tens/units (e.g., "two hundred fifty" -> 250)
                    if (endIdx + 1 < run.size) {
                        val afterScaleRaw = run[endIdx + 1].rawText
                        val afterScaleLower = afterScaleRaw.lowercase()
                        val tensVal = englishTens[afterScaleLower]
                        if (tensVal != null) {
                            totalValue += tensVal
                            endIdx++
                            exprParts.add(afterScaleRaw)
                            if (endIdx + 1 < run.size) {
                                val afterTensRaw = run[endIdx + 1].rawText
                                val subUnit = englishUnitsAndTeens[afterTensRaw.lowercase()]
                                if (subUnit != null && subUnit in 1L..9L) {
                                    totalValue += subUnit
                                    endIdx++
                                    exprParts.add(afterTensRaw)
                                }
                            }
                        }
                    }

                    results.add(
                        NormalizedNumberToken(
                            originalExpression = exprParts.joinToString(" "),
                            normalizedValue = totalValue.toString(),
                            numericValue = BigDecimal.valueOf(totalValue),
                            startTokenIndex = current.index,
                            endTokenIndex = run[endIdx].index
                        )
                    )
                    idx = endIdx + 1
                    continue
                }
            }

            // 4. English compound tens + units: e.g., ["twenty", "five"] -> 25
            val tensValue = englishTens[currentLower]
            if (tensValue != null) {
                var totalValue = tensValue
                var endIdx = idx
                val exprParts = mutableListOf(currentRaw)

                if (idx + 1 < run.size) {
                    val nextRaw = run[idx + 1].rawText
                    val nextUnit = englishUnitsAndTeens[nextRaw.lowercase()]
                    if (nextUnit != null && nextUnit in 1L..9L) {
                        totalValue += nextUnit
                        endIdx = idx + 1
                        exprParts.add(nextRaw)
                    }
                }

                results.add(
                    NormalizedNumberToken(
                        originalExpression = exprParts.joinToString(" "),
                        normalizedValue = totalValue.toString(),
                        numericValue = BigDecimal.valueOf(totalValue),
                        startTokenIndex = current.index,
                        endTokenIndex = run[endIdx].index
                    )
                )
                idx = endIdx + 1
                continue
            }

            // 5. Single number word or numeric literal
            val singleVal = isSingleNumberValue(currentRaw)
            if (singleVal != null) {
                val formatted = formatNormalizedDecimal(singleVal)
                results.add(
                    NormalizedNumberToken(
                        originalExpression = currentRaw,
                        normalizedValue = formatted,
                        numericValue = singleVal,
                        startTokenIndex = current.index,
                        endTokenIndex = current.index
                    )
                )
            }
            idx++
        }

        return results
    }

    fun isNumberToken(rawToken: String): Boolean {
        return isSingleNumberValue(rawToken) != null
    }

    fun isSingleNumberValue(rawToken: String): BigDecimal? {
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) return null

        if (numericLiteralRegex.matches(trimmed)) {
            return trimmed.toBigDecimalOrNull()
        }

        val lower = trimmed.lowercase()
        englishUnitsAndTeens[lower]?.let { return BigDecimal.valueOf(it) }
        englishTens[lower]?.let { return BigDecimal.valueOf(it) }
        englishScales[lower]?.let { return BigDecimal.valueOf(it) }
        tamilNumbers[trimmed]?.let { return BigDecimal.valueOf(it) }
        tamilFractions[trimmed]?.let { return it }

        return null
    }

    private fun formatNormalizedDecimal(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() <= 0) {
            stripped.toBigInteger().toString()
        } else {
            stripped.toPlainString()
        }
    }
}
