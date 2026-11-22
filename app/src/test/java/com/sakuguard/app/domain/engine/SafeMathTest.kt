package com.sakuguard.app.domain.engine

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.DomainException
import org.junit.Assert.assertThrows
import org.junit.Test

class SafeMathTest {

    @Test
    fun `add and subtract succeed for normal values`() {
        assertThat(SafeMath.add(1_500_000L, 500_000L)).isEqualTo(2_000_000L)
        assertThat(SafeMath.subtract(2_000_000L, 500_000L)).isEqualTo(1_500_000L)
    }

    @Test
    fun `add throws ArithmeticOverflowException on Long overflow`() {
        assertThrows(DomainException.ArithmeticOverflowException::class.java) {
            SafeMath.add(Long.MAX_VALUE, 1L)
        }
    }

    @Test
    fun `subtract throws ArithmeticOverflowException on Long underflow`() {
        assertThrows(DomainException.ArithmeticOverflowException::class.java) {
            SafeMath.subtract(Long.MIN_VALUE, 1L)
        }
    }

    @Test
    fun `sum throws ArithmeticOverflowException when cumulative sum overflows`() {
        assertThrows(DomainException.ArithmeticOverflowException::class.java) {
            SafeMath.sum(listOf(Long.MAX_VALUE - 10L, 20L))
        }
    }
}
