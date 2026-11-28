package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.engine.SafeMath
import com.sakuguard.app.domain.model.BudgetHealthStatus
import com.sakuguard.app.domain.model.BudgetPerformanceSummary
import com.sakuguard.app.domain.model.BudgetPeriodType
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.CategoryBreakdownItem
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.GoalPeriodSummary
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.ReportCashFlowBucket
import com.sakuguard.app.domain.model.ReportPeriodRange
import com.sakuguard.app.domain.model.ReportsSnapshot
import com.sakuguard.app.domain.model.SpendingTrendSummary
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletActivityItem
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.repository.CategoryTotal
import com.sakuguard.app.domain.repository.GoalPeriodContributionTotals
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.repository.MonthlyActivityTotals
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletLedgerTotals
import com.sakuguard.app.domain.repository.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

class ObserveReportsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val walletRepository: WalletRepository,
    private val categoryRepository: CategoryRepository,
    private val goalRepository: GoalRepository,
    private val observeBudgetsUseCase: ObserveBudgetsUseCase,
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    operator fun invoke(
        range: ReportPeriodRange,
        zoneId: ZoneId = ZoneId.systemDefault(),
        referenceDate: LocalDate = LocalDate.now(zoneId),
    ): Flow<ReportsSnapshot> {
        val currentMonth = YearMonth.from(referenceDate)
        val budgetRefDate = range.singleMonth?.let { month ->
            if (month == currentMonth) referenceDate else month.atEndOfMonth()
        } ?: referenceDate

        val timeSeriesFlow: Flow<List<ReportCashFlowBucket>> = transactionRepository
            .observeRecentTransactions(limit = 1)
            .mapLatest { transactionRepository.getTimeSeriesBuckets(range.buckets) }

        val corePeriodFlow = combine(
            transactionRepository.observeMonthlyActivity(range.startInclusive, range.endExclusive),
            transactionRepository.observeMonthlyActivity(range.previousStartInclusive, range.previousEndExclusive),
            transactionRepository.observeCategoryTotalsForPeriod(
                TransactionType.EXPENSE,
                range.startInclusive,
                range.endExclusive,
            ),
            transactionRepository.observeCategoryTotalsForPeriod(
                TransactionType.INCOME,
                range.startInclusive,
                range.endExclusive,
            ),
            transactionRepository.observeWalletActivityForPeriod(range.startInclusive, range.endExclusive),
            timeSeriesFlow,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            PeriodAggregates(
                currentTotals = values[0] as MonthlyActivityTotals,
                previousTotals = values[1] as MonthlyActivityTotals,
                expenseCategories = values[2] as List<CategoryTotal>,
                incomeCategories = values[3] as List<CategoryTotal>,
                walletTotals = values[4] as Map<String, WalletLedgerTotals>,
                series = values[5] as List<ReportCashFlowBucket>,
            )
        }

        val metadataAndGoalsFlow = combine(
            walletRepository.observeWallets(includeArchived = true),
            categoryRepository.observeCategories(includeArchived = true),
            goalRepository.observeGoals(includeArchived = true),
            goalRepository.observeAllocations(),
            goalRepository.observePeriodGoalContributions(range.startInclusive, range.endExclusive),
        ) { wallets, categories, goals, allocations, goalContribs ->
            MetadataAndGoals(
                wallets = wallets,
                categories = categories,
                goals = goals,
                allocations = allocations,
                goalContribs = goalContribs,
            )
        }

        return combine(
            corePeriodFlow,
            metadataAndGoalsFlow,
            observeBudgetsUseCase(zoneId = zoneId, referenceDate = budgetRefDate),
        ) { agg, meta, budgetsOverview ->
            val categoryMap = meta.categories.associateBy { it.id }

            val totalIncome = agg.currentTotals.income
            val totalExpense = agg.currentTotals.expenses
            val netCashFlow = SafeMath.subtract(totalIncome, totalExpense)

            val expenseBreakdown = mapBreakdown(agg.expenseCategories, categoryMap, totalExpense)
            val incomeBreakdown = mapBreakdown(agg.incomeCategories, categoryMap, totalIncome)

            val prevExpense = agg.previousTotals.expenses
            val absChange = SafeMath.subtract(totalExpense, prevExpense)
            val pctChangePermille = ReportPeriodResolver.calculateSignedDeltaPermille(absChange, prevExpense)

            val walletActivities = meta.wallets
                .mapNotNull { wallet ->
                    val wt = agg.walletTotals[wallet.id]
                    val inc = wt?.incomeSum ?: 0L
                    val exp = wt?.expenseSum ?: 0L
                    val tIn = wt?.incomingTransferSum ?: 0L
                    val tOut = wt?.outgoingTransferSum ?: 0L
                    val hasActivity = inc > 0L || exp > 0L || tIn > 0L || tOut > 0L
                    if (!hasActivity && wallet.isArchived) return@mapNotNull null
                    if (!hasActivity) return@mapNotNull null
                    WalletActivityItem(
                        wallet = wallet,
                        income = inc,
                        expense = exp,
                        transferIn = tIn,
                        transferOut = tOut,
                    )
                }
                .sortedByDescending { SafeMath.add(it.income, it.expense) }

            val relevantBudgets = if (range.isSingleMonth) {
                budgetsOverview.activeBudgets.filter { summary ->
                    when (summary.budget.periodType) {
                        BudgetPeriodType.MONTHLY -> summary.effectiveStart < range.endExclusive &&
                            summary.effectiveEndExclusive > range.startInclusive &&
                            summary.budget.startDate < range.endExclusive
                        BudgetPeriodType.CUSTOM -> summary.effectiveStart < range.endExclusive &&
                            summary.effectiveEndExclusive > range.startInclusive
                        BudgetPeriodType.WEEKLY -> summary.effectiveStart < range.endExclusive &&
                            summary.effectiveEndExclusive > range.startInclusive
                    }
                }
            } else {
                budgetsOverview.activeBudgets
            }

            val budgetPerf = BudgetPerformanceSummary(
                budgets = relevantBudgets.sortedByDescending { it.utilizationPermille },
                onTrackCount = relevantBudgets.count { it.healthStatus == BudgetHealthStatus.ON_TRACK },
                warningCount = relevantBudgets.count { it.healthStatus == BudgetHealthStatus.WARNING },
                exceededCount = relevantBudgets.count { it.healthStatus == BudgetHealthStatus.EXCEEDED },
                isCurrentCycleFallback = !range.isSingleMonth,
                isHistoricalMonth = range.singleMonth != null && range.singleMonth.isBefore(currentMonth),
            )

            val nonArchivedGoals = meta.goals.filterNot { it.status == GoalStatus.ARCHIVED }
            val goalSummaries = nonArchivedGoals.map { FinancialEngine.summarizeGoal(it, meta.allocations) }
            val totalAllocated = SafeMath.sum(goalSummaries.map { it.allocatedAmount })
            val protectedFunds = SafeMath.sum(
                goalSummaries.filter {
                    it.goal.protectionMode == ProtectionMode.PROTECTED ||
                        it.goal.protectionMode == ProtectionMode.STRICT
                }.map { it.allocatedAmount },
            )
            val completedCount = meta.goals.count { it.status == GoalStatus.COMPLETED }

            val goalSummary = GoalPeriodSummary(
                totalAllocated = totalAllocated,
                protectedFunds = protectedFunds,
                completedCount = completedCount,
                periodAllocated = meta.goalContribs.allocated,
                periodWithdrawn = meta.goalContribs.withdrawn,
                goals = goalSummaries,
            )

            val hasPeriodActivity = totalIncome > 0L ||
                totalExpense > 0L ||
                walletActivities.isNotEmpty() ||
                meta.goalContribs.allocated > 0L ||
                meta.goalContribs.withdrawn > 0L

            ReportsSnapshot(
                periodRange = range,
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                netCashFlow = netCashFlow,
                cashFlowSeries = if (range.isMultiMonth) agg.series else emptyList(),
                expenseBreakdown = expenseBreakdown,
                incomeBreakdown = incomeBreakdown,
                spendingTrend = SpendingTrendSummary(
                    currentExpense = totalExpense,
                    previousExpense = prevExpense,
                    absoluteChange = absChange,
                    percentageChangePermille = pctChangePermille,
                    buckets = agg.series,
                ),
                walletActivities = walletActivities,
                budgetPerformance = budgetPerf,
                goalSummary = goalSummary,
                hasAnyData = hasPeriodActivity,
            )
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    private fun mapBreakdown(
        totals: List<CategoryTotal>,
        categoryMap: Map<String, Category>,
        grandTotal: Long,
    ): List<CategoryBreakdownItem> = totals.filter { it.totalAmount > 0L }.map { item ->
        val cat = item.categoryId?.let { categoryMap[it] }
        CategoryBreakdownItem(
            categoryId = item.categoryId,
            categoryName = cat?.name,
            iconKey = cat?.iconKey,
            colorHex = cat?.colorHex,
            isArchived = cat?.isArchived ?: false,
            amount = item.totalAmount,
            percentagePermille = ReportPeriodResolver.calculatePermille(item.totalAmount, grandTotal),
        )
    }

    private data class PeriodAggregates(
        val currentTotals: MonthlyActivityTotals,
        val previousTotals: MonthlyActivityTotals,
        val expenseCategories: List<CategoryTotal>,
        val incomeCategories: List<CategoryTotal>,
        val walletTotals: Map<String, WalletLedgerTotals>,
        val series: List<ReportCashFlowBucket>,
    )

    private data class MetadataAndGoals(
        val wallets: List<Wallet>,
        val categories: List<Category>,
        val goals: List<Goal>,
        val allocations: List<GoalAllocationEntry>,
        val goalContribs: GoalPeriodContributionTotals,
    )
}
