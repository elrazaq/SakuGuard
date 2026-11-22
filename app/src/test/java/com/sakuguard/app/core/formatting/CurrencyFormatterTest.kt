package com.sakuguard.app.core.formatting

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CurrencyFormatterTest {

    @Test
    fun `formatIdr formats zero`() {
        assertThat(CurrencyFormatter.formatIdr(0L)).isEqualTo("Rp0")
    }

    @Test
    fun `formatIdr formats positive millions with dot grouping`() {
        assertThat(CurrencyFormatter.formatIdr(1_500_000L)).isEqualTo("Rp1.500.000")
    }

    @Test
    fun `formatIdr formats negative amounts with leading minus`() {
        assertThat(CurrencyFormatter.formatIdr(-250_000L)).isEqualTo("-Rp250.000")
    }
}
