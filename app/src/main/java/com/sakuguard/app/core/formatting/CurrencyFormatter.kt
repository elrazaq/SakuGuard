package com.sakuguard.app.core.formatting

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Deterministic Indonesian Rupiah formatter for integer [Long] amounts.
 * Produces output in the form "Rp1.500.000" or "-Rp1.500.000".
 */
object CurrencyFormatter {
    private val indonesianSymbols = DecimalFormatSymbols(Locale("in", "ID")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private val threadLocalFormatter = ThreadLocal.withInitial {
        DecimalFormat("#,##0", indonesianSymbols)
    }

    fun formatIdr(amount: Long): String {
        val formatter = requireNotNull(threadLocalFormatter.get())
        val formattedNumber = formatter.format(abs(amount))
        return if (amount < 0) "-Rp$formattedNumber" else "Rp$formattedNumber"
    }
}
