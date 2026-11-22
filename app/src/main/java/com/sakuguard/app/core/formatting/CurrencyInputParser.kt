package com.sakuguard.app.core.formatting

/**
 * Parses integer-only IDR monetary input into [Long].
 * Rejects blank strings, signs, decimals, non-digits, and [Long] overflow.
 */
object CurrencyInputParser {
    fun parseNonNegativeIdr(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { !it.isDigit() }) return null
        return trimmed.toLongOrNull()
    }
}
