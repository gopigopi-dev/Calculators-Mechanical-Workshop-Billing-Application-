package com.example.calculators.textprocessing

object TextNormalizer {

    // Matches commas inside digit groups like "1,250" or "1,00,000"
    private val digitGroupingCommaRegex = Regex("(?<=\\d),(?=\\d)")

    // Matches currency symbols or prefixes attached to numbers/words (₹, $)
    private val currencySymbolRegex = Regex("[₹$€£]")

    // Matches "/-" often produced by Indian English speech-to-text after amounts (e.g. "200/-")
    private val trailingSlashDashRegex = Regex("/-")

    // Matches punctuation characters except decimal points between digits and clause commas/semicolons
    private val nonDecimalPunctuationRegex = Regex("(?<!\\d)\\.(?!\\d)|[:!?\"'()\\[\\]{}<>\\\\/~`@#$%^&*_+=]+")

    // Matches unsupported control or non-printable symbol characters (excluding Tamil, Latin, digits, whitespace, '.', ',', ';', '|', '-')
    private val unsupportedCharRegex = Regex("[^\\u0B80-\\u0BFFa-zA-Z0-9.,;|\\s-]")

    // Matches repeated spaces, tabs, or newlines
    private val repeatedWhitespaceRegex = Regex("\\s+")

    /**
     * Cleans and normalizes raw transcript text while preserving Tamil and English product names
     * in their original script without translation.
     */
    fun cleanText(rawTranscript: String): Pair<String, List<String>> {
        val warnings = mutableListOf<String>()
        val trimmedInitial = rawTranscript.trim()
        if (trimmedInitial.isEmpty()) {
            return "" to emptyList()
        }

        // Step 1: Convert newlines to clause-break semicolons and remove grouping commas inside digits ("1,250.50" -> "1250.50")
        var working = trimmedInitial.replace("\n", "; ")
        working = digitGroupingCommaRegex.replace(working, "")

        // Step 2: Strip currency symbols and "/-" suffixes
        working = trailingSlashDashRegex.replace(working, " ")
        working = currencySymbolRegex.replace(working, " ")

        // Step 3: Remove unnecessary punctuation while keeping decimal points inside numbers and clause commas/semicolons
        working = nonDecimalPunctuationRegex.replace(working, " ")

        // Step 4: Replace standalone hyphens with spaces
        working = working.replace(Regex("(?<=\\s)-+|-(?=\\s)|^-+|-+$"), " ")

        // Step 5: Check and strip any remaining unsupported characters
        val unsupportedMatches = unsupportedCharRegex.findAll(working).map { it.value }.distinct().toList()
        if (unsupportedMatches.isNotEmpty()) {
            warnings.add("Removed unsupported characters: ${unsupportedMatches.joinToString(" ")}")
            working = unsupportedCharRegex.replace(working, " ")
        }

        // Step 6: Normalize repeated spaces and trim leading/trailing whitespace
        working = repeatedWhitespaceRegex.replace(working, " ").trim()

        // Also provide a clean display string without clause delimiters for cleanedTranscript
        val displayCleaned = working.replace(Regex("[,;|]+"), " ").replace(repeatedWhitespaceRegex, " ").trim()

        return displayCleaned to warnings
    }

    /**
     * Internal helper that preserves clause break delimiters (',' / ';') for Tokenizer so
     * multi-product clauses do not merge across commas.
     */
    fun cleanTextPreservingClauseBreaks(rawTranscript: String): Pair<String, List<String>> {
        val warnings = mutableListOf<String>()
        val trimmedInitial = rawTranscript.trim()
        if (trimmedInitial.isEmpty()) {
            return "" to emptyList()
        }

        var working = trimmedInitial.replace("\n", "; ")
        working = digitGroupingCommaRegex.replace(working, "")
        working = trailingSlashDashRegex.replace(working, " ")
        working = currencySymbolRegex.replace(working, " ")
        working = nonDecimalPunctuationRegex.replace(working, " ")
        working = working.replace(Regex("(?<=\\s)-+|-(?=\\s)|^-+|-+$"), " ")

        val unsupportedMatches = unsupportedCharRegex.findAll(working).map { it.value }.distinct().toList()
        if (unsupportedMatches.isNotEmpty()) {
            warnings.add("Removed unsupported characters: ${unsupportedMatches.joinToString(" ")}")
            working = unsupportedCharRegex.replace(working, " ")
        }

        working = repeatedWhitespaceRegex.replace(working, " ").trim()
        return working to warnings
    }

    /**
     * Full Phase 5 pipeline:
     * 1. Preserves originalTranscript verbatim.
     * 2. Cleans spacing, punctuation, and speech-recognition formatting differences.
     * 3. Detects Tamil, English, or Mixed script content.
     * 4. Tokenizes into ordered tokens (preserving clause boundaries).
     * 5. Normalizes Tamil and English number words, digits, and decimals while preserving product words.
     */
    fun processTranscript(rawTranscript: String, preservedOriginal: String = rawTranscript): ProcessedTranscript {
        if (rawTranscript.isBlank()) {
            return ProcessedTranscript(
                originalTranscript = preservedOriginal,
                cleanedTranscript = "",
                normalizedTranscript = "",
                detectedLanguage = DetectedLanguage.UNKNOWN,
                tokens = emptyList(),
                normalizedNumbers = emptyList(),
                preservedProductWords = emptyList(),
                warnings = emptyList()
            )
        }

        val (cleanedWithBreaks, cleanWarnings) = cleanTextPreservingClauseBreaks(rawTranscript)
        val cleanedDisplay = cleanedWithBreaks
            .replace(Regex("[,;|]+"), " ")
            .replace(repeatedWhitespaceRegex, " ")
            .trim()
        val detectedLanguage = LanguageDetector.detectLanguage(cleanedDisplay)
        val tokens = Tokenizer.tokenize(cleanedWithBreaks)
        val numberResult = NumberNormalizer.normalizeNumbers(tokens)

        val allWarnings = cleanWarnings + numberResult.warnings

        return ProcessedTranscript(
            originalTranscript = preservedOriginal,
            cleanedTranscript = cleanedDisplay,
            normalizedTranscript = numberResult.normalizedText,
            detectedLanguage = detectedLanguage,
            tokens = tokens,
            normalizedNumbers = numberResult.normalizedNumbers,
            preservedProductWords = numberResult.preservedProductWords,
            warnings = allWarnings
        )
    }
}
