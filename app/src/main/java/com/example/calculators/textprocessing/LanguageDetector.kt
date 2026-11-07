package com.example.calculators.textprocessing

enum class DetectedLanguage(val displayName: String) {
    TAMIL("Tamil"),
    ENGLISH("English"),
    MIXED("Mixed (Tamil & English)"),
    NUMERIC_ONLY("Numeric"),
    UNKNOWN("Unknown")
}

enum class TokenScript {
    TAMIL,
    ENGLISH,
    NUMERIC,
    MIXED,
    OTHER
}

object LanguageDetector {

    /**
     * Detects the overall language composition of a transcript.
     * Does not assume every transcript contains only one language.
     */
    fun detectLanguage(text: String): DetectedLanguage {
        if (text.isBlank()) {
            return DetectedLanguage.UNKNOWN
        }

        var hasTamil = false
        var hasEnglish = false
        var hasDigits = false

        for (ch in text) {
            when {
                isTamilChar(ch) -> hasTamil = true
                isEnglishLetter(ch) -> hasEnglish = true
                ch.isDigit() -> hasDigits = true
            }
        }

        return when {
            hasTamil && hasEnglish -> DetectedLanguage.MIXED
            hasTamil -> DetectedLanguage.TAMIL
            hasEnglish -> DetectedLanguage.ENGLISH
            hasDigits -> DetectedLanguage.NUMERIC_ONLY
            else -> DetectedLanguage.UNKNOWN
        }
    }

    /**
     * Detects the script of an individual token.
     */
    fun detectTokenScript(token: String): TokenScript {
        if (token.isBlank()) return TokenScript.OTHER

        var hasTamil = false
        var hasEnglish = false
        var hasDigits = false

        for (ch in token) {
            when {
                isTamilChar(ch) -> hasTamil = true
                isEnglishLetter(ch) -> hasEnglish = true
                ch.isDigit() -> hasDigits = true
            }
        }

        return when {
            hasTamil && hasEnglish -> TokenScript.MIXED
            hasTamil -> TokenScript.TAMIL
            hasEnglish -> TokenScript.ENGLISH
            hasDigits -> TokenScript.NUMERIC
            else -> TokenScript.OTHER
        }
    }

    fun isTamilChar(ch: Char): Boolean {
        return ch in '\u0B80'..'\u0BFF'
    }

    fun isEnglishLetter(ch: Char): Boolean {
        return ch in 'A'..'Z' || ch in 'a'..'z'
    }
}
