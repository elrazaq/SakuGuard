package com.sakuguard.app.domain.model

import java.time.YearMonth

enum class ReportPeriodPreset {
    THIS_MONTH,
    LAST_MONTH,
    LAST_3_MONTHS,
    LAST_6_MONTHS,
    LAST_12_MONTHS,
    CUSTOM,
}

data class ReportTimeBucket(
    val label: String,
    val startInclusive: Long,
    val endExclusive: Long,
)

data class ReportPeriodRange(
    val preset: ReportPeriodPreset,
    val startInclusive: Long,
    val endExclusive: Long,
    val previousStartInclusive: Long,
    val previousEndExclusive: Long,
    val buckets: List<ReportTimeBucket>,
    val isMultiMonth: Boolean,
    val isSingleMonth: Boolean,
    val singleMonth: YearMonth? = null,
)

data class ReportCashFlowBucket(
    val label: String,
    val income: Long,
    val expense: Long,
)

data class CategoryBreakdownItem(
    val categoryId: String?,
    val categoryName: String?,
    val iconKey: String?,
    val colorHex: Long?,
    val isArchived: Boolean,
    val amount: Long,
    val percentagePermille: Int,
)

data class WalletActivityItem(
    val wallet: Wallet,
    val income: Long,
    val expense: Long,
    val transferIn: Long,
    val transferOut: Long,
)

data class SpendingTrendSummary(
    val currentExpense: Long,
    val previousExpense: Long,
    val absoluteChange: Long,
    val percentageChangePermille: Int?,
    val buckets: List<ReportCashFlowBucket>,
)

data class GoalPeriodSummary(
    val totalAllocated: Long,
    val protectedFunds: Long,
    val completedCount: Int,
    val periodAllocated: Long,
    val periodWithdrawn: Long,
    val goals: List<GoalSummary>,
)

data class BudgetPerformanceSummary(
    val budgets: List<BudgetSummary> = emptyList(),
    val onTrackCount: Int = 0,
    val warningCount: Int = 0,
    val exceededCount: Int = 0,
    val isCurrentCycleFallback: Boolean = false,
    val isHistoricalMonth: Boolean = false,
)

data class ReportsSnapshot(
    val periodRange: ReportPeriodRange,
    val totalIncome: Long = 0L,
    val totalExpense: Long = 0L,
    val netCashFlow: Long = 0L,
    val cashFlowSeries: List<ReportCashFlowBucket> = emptyList(),
    val expenseBreakdown: List<CategoryBreakdownItem> = emptyList(),
    val incomeBreakdown: List<CategoryBreakdownItem> = emptyList(),
    val spendingTrend: SpendingTrendSummary,
    val walletActivities: List<WalletActivityItem> = emptyList(),
    val budgetPerformance: BudgetPerformanceSummary = BudgetPerformanceSummary(),
    val goalSummary: GoalPeriodSummary,
    val hasAnyData: Boolean = false,
)
