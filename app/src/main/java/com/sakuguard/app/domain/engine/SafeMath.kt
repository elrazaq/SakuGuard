package com.sakuguard.app.domain.engine

import com.sakuguard.app.domain.model.DomainException

/**
 * Overflow-safe integer arithmetic for IDR [Long] monetary values.
 */
object SafeMath {
    fun add(a: Long, b: Long): Long = try {
        Math.addExact(a, b)
    } catch (e: ArithmeticException) {
        throw DomainException.ArithmeticOverflowException("Addition overflow: $a + $b")
    }

    fun subtract(a: Long, b: Long): Long = try {
        Math.subtractExact(a, b)
    } catch (e: ArithmeticException) {
        throw DomainException.ArithmeticOverflowException("Subtraction overflow: $a - $b")
    }

    fun sum(values: Iterable<Long>): Long {
        var total = 0L
        for (value in values) {
            total = add(total, value)
        }
        return total
    }
}
