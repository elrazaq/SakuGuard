package com.sakuguard.app.domain.usecase

import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.Budget
import com.sakuguard.app.domain.model.BudgetPeriodType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

data class ResolvedBudgetWindow(
    val startInclusive: Long,
    val endExclusive: Long,
    val periodKey: String,
    val isActiveInPeriod: Boolean,
)

object BudgetPeriodResolver {

    fun effectiveRecurringStart(
        periodType: BudgetPeriodType,
        rawStartDate: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val local = DateFormatter.toLocalDate(rawStartDate, zoneId)
        val normalized = when (periodType) {
            BudgetPeriodType.MONTHLY -> local.withDayOfMonth(1)
            BudgetPeriodType.WEEKLY -> local.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            BudgetPeriodType.CUSTOM -> local
        }
        return normalized.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun resolveWindow(
        budget: Budget,
        referenceDate: LocalDate = LocalDate.now(ZoneId.systemDefault()),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ResolvedBudgetWindow {
        return when (budget.periodType) {
            BudgetPeriodType.MONTHLY -> {
                val effectiveStart = effectiveRecurringStart(BudgetPeriodType.MONTHLY, budget.startDate, zoneId)
                val effectiveStartMonth = YearMonth.from(DateFormatter.toLocalDate(effectiveStart, zoneId))
                val targetMonth = YearMonth.from(referenceDate)
                val mStart = targetMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val mEnd = targetMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                ResolvedBudgetWindow(
                    startInclusive = mStart,
                    endExclusive = mEnd,
                    periodKey = targetMonth.toString(),
                    isActiveInPeriod = !targetMonth.isBefore(effectiveStartMonth),
                )
            }
            BudgetPeriodType.WEEKLY -> {
                val effectiveStart = effectiveRecurringStart(BudgetPeriodType.WEEKLY, budget.startDate, zoneId)
                val monday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val wStart = monday.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val wEnd = monday.plusWeeks(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val isoYear = monday.get(IsoFields.WEEK_BASED_YEAR)
                val isoWeek = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                ResolvedBudgetWindow(
                    startInclusive = wStart,
                    endExclusive = wEnd,
                    periodKey = "$isoYear-W$isoWeek",
                    isActiveInPeriod = wStart >= effectiveStart,
                )
            }
            BudgetPeriodType.CUSTOM -> {
                val refMillis = referenceDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endRefMillis = referenceDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val active = budget.startDate < endRefMillis && budget.endDateExclusive > refMillis
                ResolvedBudgetWindow(
                    startInclusive = budget.startDate,
                    endExclusive = budget.endDateExclusive,
                    periodKey = "custom",
                    isActiveInPeriod = active,
                )
            }
        }
    }

    /**
     * Checks all 6 combinations of (MONTHLY, WEEKLY, CUSTOM) for same-category effective overlap.
     */
    fun overlaps(
        a: Budget,
        b: Budget,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (a.categoryId != b.categoryId) return false
        if (a.isArchived || b.isArchived) return false

        val aRecurring = a.periodType == BudgetPeriodType.MONTHLY || a.periodType == BudgetPeriodType.WEEKLY
        val bRecurring = b.periodType == BudgetPeriodType.MONTHLY || b.periodType == BudgetPeriodType.WEEKLY

        return when {
            aRecurring && bRecurring -> true
            aRecurring && !bRecurring -> {
                val effStart = effectiveRecurringStart(a.periodType, a.startDate, zoneId)
                b.endDateExclusive > effStart
            }
            !aRecurring && bRecurring -> {
                val effStart = effectiveRecurringStart(b.periodType, b.startDate, zoneId)
                a.endDateExclusive > effStart
            }
            else -> a.startDate < b.endDateExclusive && b.startDate < a.endDateExclusive
        }
    }
}
