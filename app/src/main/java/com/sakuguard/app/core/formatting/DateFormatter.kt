package com.sakuguard.app.core.formatting

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Deterministic date/time utilities using `java.time` (API 26+).
 */
object DateFormatter {

    fun toLocalDate(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

    fun startOfDayMillis(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long = toLocalDate(epochMillis, zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()

    /**
     * Material 3 DatePicker returns UTC midnight for the chosen calendar date.
     * Convert that UTC calendar date into local start-of-day epoch millis.
     */
    fun utcDatePickerMillisToLocalStartOfDay(
        utcMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val pickedDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return pickedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun localMillisToUtcDatePickerMillis(
        localMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val localDate = toLocalDate(localMillis, zoneId)
        return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun formatShortDate(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        return toLocalDate(epochMillis, zoneId).format(formatter)
    }

    fun formatMonthYear(
        epochMillis: Long,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        return toLocalDate(epochMillis, zoneId).format(formatter)
    }
}
