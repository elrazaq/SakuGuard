package com.sakuguard.app.core.formatting

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CurrencyInputParserTest {

    @Test
    fun `parseNonNegativeIdr parses valid whole numbers`() {
        assertThat(CurrencyInputParser.parseNonNegativeIdr("0")).isEqualTo(0L)
        assertThat(CurrencyInputParser.parseNonNegativeIdr("1500000")).isEqualTo(1_500_000L)
        assertThat(CurrencyInputParser.parseNonNegativeIdr("  25000  ")).isEqualTo(25_000L)
    }

    @Test
    fun `parseNonNegativeIdr rejects blank, negative, decimal, non-digit, and overflow`() {
        assertThat(CurrencyInputParser.parseNonNegativeIdr("")).isNull()
        assertThat(CurrencyInputParser.parseNonNegativeIdr("   ")).isNull()
        assertThat(CurrencyInputParser.parseNonNegativeIdr("-1000")).isNull()
        assertThat(CurrencyInputParser.parseNonNegativeIdr("1500.50")).isNull()
        assertThat(CurrencyInputParser.parseNonNegativeIdr("1,500")).isNull()
        assertThat(CurrencyInputParser.parseNonNegativeIdr("12abc")).isNull()
        assertThat(CurrencyInputParser.parseNonNegativeIdr("99999999999999999999999")).isNull()
    }
}
