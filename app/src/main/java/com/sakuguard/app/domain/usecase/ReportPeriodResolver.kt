package com.sakuguard.app.domain.usecase

import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.ReportPeriodPreset
import com.sakuguard.app.domain.model.ReportPeriodRange
import com.sakuguard.app.domain.model.ReportTimeBucket
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

object ReportPeriodResolver {

    private const val MAX_TIME_BUCKETS = 24

    fun resolve(
        preset: ReportPeriodPreset,
        customStartMillis: Long? = null,
        customEndInclusiveMillis: Long? = null,
        referenceDate: LocalDate = LocalDate.now(ZoneId.systemDefault()),
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): ReportPeriodRange {
        val currentMonth = YearMonth.from(referenceDate)
        return when (preset) {
            ReportPeriodPreset.THIS_MONTH -> singleMonthRange(
                preset = preset,
                month = currentMonth,
                zoneId = zoneId,
                locale = locale,
            )
            ReportPeriodPreset.LAST_MONTH -> singleMonthRange(
                preset = preset,
                month = currentMonth.minusMonths(1),
                zoneId = zoneId,
                locale = locale,
            )
            ReportPeriodPreset.LAST_3_MONTHS -> multiMonthRange(
                preset = preset,
                startMonth = currentMonth.minusMonths(2),
                endMonthInclusive = currentMonth,
                monthCount = 3L,
                zoneId = zoneId,
                locale = locale,
            )
            ReportPeriodPreset.LAST_6_MONTHS -> multiMonthRange(
                preset = preset,
                startMonth = currentMonth.minusMonths(5),
                endMonthInclusive = currentMonth,
                monthCount = 6L,
                zoneId = zoneId,
                locale = locale,
            )
            ReportPeriodPreset.LAST_12_MONTHS -> multiMonthRange(
                preset = preset,
                startMonth = currentMonth.minusMonths(11),
                endMonthInclusive = currentMonth,
                monthCount = 12L,
                zoneId = zoneId,
                locale = locale,
            )
            ReportPeriodPreset.CUSTOM -> customRange(
                rawStart = customStartMillis,
                rawEndInclusive = customEndInclusiveMillis,
                referenceDate = referenceDate,
                zoneId = zoneId,
                locale = locale,
            )
        }
    }

    private fun singleMonthRange(
        preset: ReportPeriodPreset,
        month: YearMonth,
        zoneId: ZoneId,
        locale: Locale,
    ): ReportPeriodRange {
        val start = month.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val prevStart = month.minusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val buckets = buildWeeklyOrDailyBucketsForMonth(month, zoneId, locale)
        return ReportPeriodRange(
            preset = preset,
            startInclusive = start,
            endExclusive = end,
            previousStartInclusive = prevStart,
            previousEndExclusive = start,
            buckets = buckets,
            isMultiMonth = false,
            isSingleMonth = true,
            singleMonth = month,
        )
    }

    private fun multiMonthRange(
        preset: ReportPeriodPreset,
        startMonth: YearMonth,
        endMonthInclusive: YearMonth,
        monthCount: Long,
        zoneId: ZoneId,
        locale: Locale,
    ): ReportPeriodRange {
        val start = startMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = endMonthInclusive.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val prevStart = startMonth.minusMonths(monthCount).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val buckets = buildMonthlyBuckets(startMonth, endMonthInclusive, zoneId, locale)
        return ReportPeriodRange(
            preset = preset,
            startInclusive = start,
            endExclusive = end,
            previousStartInclusive = prevStart,
            previousEndExclusive = start,
            buckets = buckets,
            isMultiMonth = true,
            isSingleMonth = false,
            singleMonth = null,
        )
    }

    private fun customRange(
        rawStart: Long?,
        rawEndInclusive: Long?,
        referenceDate: LocalDate,
        zoneId: ZoneId,
        locale: Locale,
    ): ReportPeriodRange {
        val sLocal = rawStart?.let { DateFormatter.toLocalDate(it, zoneId) } ?: referenceDate.withDayOfMonth(1)
        val eLocalRaw = rawEndInclusive?.let { DateFormatter.toLocalDate(it, zoneId) } ?: referenceDate
        val (startLocal, endLocal) = if (eLocalRaw.isBefore(sLocal)) eLocalRaw to sLocal else sLocal to eLocalRaw

        val endExclusiveLocal = endLocal.plusDays(1)
        val days = ChronoUnit.DAYS.between(startLocal, endExclusiveLocal).coerceAtLeast(1L)
        val prevStartLocal = startLocal.minusDays(days)

        val start = startLocal.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = endExclusiveLocal.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val prevStart = prevStartLocal.atStartOfDay(zoneId).toInstant().toEpochMilli()

        val startMonth = YearMonth.from(startLocal)
        val endMonth = YearMonth.from(endLocal)
        val monthSpan = ChronoUnit.MONTHS.between(startMonth, endMonth) + 1L

        val buckets = when {
            days <= 31L -> buildDailyBuckets(startLocal, endExclusiveLocal, zoneId, locale)
            monthSpan <= MAX_TIME_BUCKETS -> buildMonthlyBuckets(startMonth, endMonth, zoneId, locale, start, end)
            else -> buildYearlyBuckets(startLocal.year, endLocal.year, zoneId, start, end)
        }

        val isSingleFullMonth = startLocal.dayOfMonth == 1 &&
            endLocal == startMonth.atEndOfMonth() &&
            startMonth == endMonth

        return ReportPeriodRange(
            preset = ReportPeriodPreset.CUSTOM,
            startInclusive = start,
            endExclusive = end,
            previousStartInclusive = prevStart,
            previousEndExclusive = start,
            buckets = buckets,
            isMultiMonth = monthSpan > 1L,
            isSingleMonth = isSingleFullMonth,
            singleMonth = if (isSingleFullMonth) startMonth else null,
        )
    }

    private fun buildWeeklyOrDailyBucketsForMonth(
        month: YearMonth,
        zoneId: ZoneId,
        locale: Locale,
    ): List<ReportTimeBucket> {
        val formatter = DateTimeFormatter.ofPattern("d", locale)
        val buckets = mutableListOf<ReportTimeBucket>()
        var cursor = month.atDay(1)
        val endExclusive = month.plusMonths(1).atDay(1)
        while (cursor.isBefore(endExclusive)) {
            val next = cursor.plusDays(7).let { if (it.isAfter(endExclusive)) endExclusive else it }
            val bStart = cursor.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val bEnd = next.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endDay = next.minusDays(1)
            buckets.add(
                ReportTimeBucket(
                    label = "${cursor.format(formatter)}-${endDay.format(formatter)}",
                    startInclusive = bStart,
                    endExclusive = bEnd,
                ),
            )
            cursor = next
        }
        return buckets
    }

    private fun buildDailyBuckets(
        startLocal: LocalDate,
        endExclusiveLocal: LocalDate,
        zoneId: ZoneId,
        locale: Locale,
    ): List<ReportTimeBucket> {
        val formatter = DateTimeFormatter.ofPattern("d/M", locale)
        val days = ChronoUnit.DAYS.between(startLocal, endExclusiveLocal).coerceAtLeast(1L)
        val step = if (days > MAX_TIME_BUCKETS) ((days + MAX_TIME_BUCKETS - 1) / MAX_TIME_BUCKETS) else 1L
        val buckets = mutableListOf<ReportTimeBucket>()
        var cursor = startLocal
        while (cursor.isBefore(endExclusiveLocal)) {
            val next = cursor.plusDays(step).let { if (it.isAfter(endExclusiveLocal)) endExclusiveLocal else it }
            buckets.add(
                ReportTimeBucket(
                    label = cursor.format(formatter),
                    startInclusive = cursor.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    endExclusive = next.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                ),
            )
            cursor = next
        }
        return buckets
    }

    private fun buildMonthlyBuckets(
        startMonth: YearMonth,
        endMonthInclusive: YearMonth,
        zoneId: ZoneId,
        locale: Locale,
        clampStart: Long? = null,
        clampEnd: Long? = null,
    ): List<ReportTimeBucket> {
        val formatter = DateTimeFormatter.ofPattern("MMM yy", locale)
        val buckets = mutableListOf<ReportTimeBucket>()
        var cursor = startMonth
        while (!cursor.isAfter(endMonthInclusive)) {
            val rawStart = cursor.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val rawEnd = cursor.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val bStart = clampStart?.let { maxOf(rawStart, it) } ?: rawStart
            val bEnd = clampEnd?.let { minOf(rawEnd, it) } ?: rawEnd
            if (bStart < bEnd) {
                buckets.add(
                    ReportTimeBucket(
                        label = cursor.format(formatter),
                        startInclusive = bStart,
                        endExclusive = bEnd,
                    ),
                )
            }
            cursor = cursor.plusMonths(1)
        }
        return buckets
    }

    private fun buildYearlyBuckets(
        startYear: Int,
        endYearInclusive: Int,
        zoneId: ZoneId,
        clampStart: Long,
        clampEnd: Long,
    ): List<ReportTimeBucket> {
        val buckets = mutableListOf<ReportTimeBucket>()
        for (year in startYear..endYearInclusive) {
            val rawStart = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val rawEnd = LocalDate.of(year + 1, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val bStart = maxOf(rawStart, clampStart)
            val bEnd = minOf(rawEnd, clampEnd)
            if (bStart < bEnd) {
                buckets.add(ReportTimeBucket(label = year.toString(), startInclusive = bStart, endExclusive = bEnd))
            }
        }
        return buckets
    }

    fun calculatePermille(part: Long, total: Long): Int {
        if (total <= 0L || part <= 0L) return 0
        return BigInteger.valueOf(part)
            .multiply(BigInteger.valueOf(1_000L))
            .divide(BigInteger.valueOf(total))
            .toInt()
    }

    fun calculateSignedDeltaPermille(delta: Long, baseline: Long): Int? {
        if (baseline <= 0L) return null
        return BigInteger.valueOf(delta)
            .multiply(BigInteger.valueOf(1_000L))
            .divide(BigInteger.valueOf(baseline))
            .toInt()
    }
}
