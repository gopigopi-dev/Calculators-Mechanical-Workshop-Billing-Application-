package com.example.calculators.textprocessing

data class TranscriptToken(
    val index: Int,
    val rawText: String,
    val script: TokenScript,
    val hasClauseBreakAfter: Boolean = false
)

object Tokenizer {

    private val whitespaceRegex = Regex("\\s+")
    private val clauseBreakSuffixRegex = Regex("[,;|]+$")

    /**
     * Divides a cleaned transcript into ordered meaningful tokens while preserving original script,
     * token order, and clause break markers (such as commas between product entries).
     */
    fun tokenize(cleanedText: String): List<TranscriptToken> {
        val trimmed = cleanedText.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }

        val rawPieces = trimmed.split(whitespaceRegex).filter { it.isNotBlank() }
        val result = mutableListOf<TranscriptToken>()

        for (piece in rawPieces) {
            val hasBreak = clauseBreakSuffixRegex.containsMatchIn(piece)
            val stripped = piece.replace(clauseBreakSuffixRegex, "").trim()
            if (stripped.isEmpty()) {
                // Standalone comma/delimiter token: mark previous token as having a clause break
                if (result.isNotEmpty()) {
                    val lastIndex = result.lastIndex
                    result[lastIndex] = result[lastIndex].copy(hasClauseBreakAfter = true)
                }
            } else {
                result.add(
                    TranscriptToken(
                        index = result.size,
                        rawText = stripped,
                        script = LanguageDetector.detectTokenScript(stripped),
                        hasClauseBreakAfter = hasBreak
                    )
                )
            }
        }

        return result
    }
}
