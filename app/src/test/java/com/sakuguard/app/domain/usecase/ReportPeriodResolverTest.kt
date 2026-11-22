package com.sakuguard.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.sakuguard.app.domain.model.ReportPeriodPreset
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class ReportPeriodResolverTest {

    private val jakartaZone = ZoneId.of("Asia/Jakarta")
    private val refDate = LocalDate.of(2026, 11, 22)

    @Test
    fun `this month and last month resolve exact calendar-month and previous-month bounds`() {
        val thisMonth = ReportPeriodResolver.resolve(
            preset = ReportPeriodPreset.THIS_MONTH,
            referenceDate = refDate,
            zoneId = jakartaZone,
            locale = Locale.US,
        )
        assertThat(thisMonth.startInclusive).isEqualTo(LocalDate.of(2026, 11, 1).atStartOfDay(jakartaZone).toInstant().toEpochMilli())
        assertThat(thisMonth.endExclusive).isEqualTo(LocalDate.of(2026, 12, 1).atStartOfDay(jakartaZone).toInstant().toEpochMilli())
        assertThat(thisMonth.previousStartInclusive).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay(jakartaZone).toInstant().toEpochMilli())
        assertThat(thisMonth.previousEndExclusive).isEqualTo(thisMonth.startInclusive)
        assertThat(thisMonth.isSingleMonth).isTrue()
        assertThat(thisMonth.isMultiMonth).isFalse()

        val lastMonth = ReportPeriodResolver.resolve(
            preset = ReportPeriodPreset.LAST_MONTH,
            referenceDate = refDate,
            zoneId = jakartaZone,
            locale = Locale.US,
        )
        assertThat(lastMonth.startInclusive).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay(jakartaZone).toInstant().toEpochMilli())
        assertThat(lastMonth.previousStartInclusive).isEqualTo(LocalDate.of(2026, 9, 1).atStartOfDay(jakartaZone).toInstant().toEpochMilli())
    }

    @Test
    fun `multi-month presets resolve exact equivalent prior windows`() {
        val threeM = ReportPeriodResolver.resolve(ReportPeriodPreset.LAST_3_MONTHS, referenceDate = refDate, zoneId = jakartaZone)
        assertThat(threeM.startInclusive).isEqualTo(LocalDate.of(2026, 9, 1).atStartOfDay(jakartaZone).toInstant().toEpochMilli())
        assertThat(threeM.previousStartInclusive).isEqualTo(LocalDate.of(2026, 6, 1).atStartOfDay(jakartaZone).toInstant().toEpochMilli())
        assertThat(threeM.buckets).hasSize(3)
        assertThat(threeM.isMultiMonth).isTrue()

        val twelveM = ReportPeriodResolver.resolve(ReportPeriodPreset.LAST_12_MONTHS, referenceDate = refDate, zoneId = jakartaZone)
        assertThat(twelveM.startInclusive).isEqualTo(LocalDate.of(2025, 12, 1).atStartOfDay(jakartaZone).toInstant().toEpochMilli())
        assertThat(twelveM.previousStartInclusive).isEqualTo(LocalDate.of(2024, 12, 1).atStartOfDay(jakartaZone).toInstant().toEpochMilli())
        assertThat(twelveM.buckets).hasSize(12)
    }

    @Test
    fun `custom range caps buckets at 24 and computes exact prior duration`() {
        val start = LocalDate.of(2026, 5, 1).atStartOfDay(jakartaZone).toInstant().toEpochMilli()
        val endInc = LocalDate.of(2026, 5, 10).atStartOfDay(jakartaZone).toInstant().toEpochMilli()
        val custom = ReportPeriodResolver.resolve(
            preset = ReportPeriodPreset.CUSTOM,
            customStartMillis = start,
            customEndInclusiveMillis = endInc,
            referenceDate = refDate,
            zoneId = jakartaZone,
        )
        assertThat(custom.endExclusive).isEqualTo(LocalDate.of(2026, 5, 11).atStartOfDay(jakartaZone).toInstant().toEpochMilli())
        assertThat(custom.previousStartInclusive).isEqualTo(LocalDate.of(2026, 4, 21).atStartOfDay(jakartaZone).toInstant().toEpochMilli())
        assertThat(custom.buckets.size).isAtMost(24)

        val multiYearStart = LocalDate.of(2020, 1, 1).atStartOfDay(jakartaZone).toInstant().toEpochMilli()
        val multiYearEnd = LocalDate.of(2026, 11, 22).atStartOfDay(jakartaZone).toInstant().toEpochMilli()
        val longCustom = ReportPeriodResolver.resolve(
            preset = ReportPeriodPreset.CUSTOM,
            customStartMillis = multiYearStart,
            customEndInclusiveMillis = multiYearEnd,
            referenceDate = refDate,
            zoneId = jakartaZone,
        )
        assertThat(longCustom.buckets.size).isAtMost(24)
    }

    @Test
    fun `percentage helpers handle zero baseline and large Long values without overflow`() {
        assertThat(ReportPeriodResolver.calculateSignedDeltaPermille(500_000L, 0L)).isNull()
        assertThat(ReportPeriodResolver.calculateSignedDeltaPermille(250_000L, 1_000_000L)).isEqualTo(250)
        assertThat(ReportPeriodResolver.calculateSignedDeltaPermille(-500_000L, 1_000_000L)).isEqualTo(-500)

        val hugeTotal = 4_000_000_000_000_000_000L
        val hugePart = hugeTotal / 4L
        assertThat(ReportPeriodResolver.calculatePermille(hugePart, hugeTotal)).isEqualTo(250)
    }
}
