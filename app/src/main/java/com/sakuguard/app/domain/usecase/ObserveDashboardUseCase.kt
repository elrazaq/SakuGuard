package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.engine.MonthlyActivity
import com.sakuguard.app.domain.engine.SafeMath
import com.sakuguard.app.domain.model.FinancialOverview
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.GoalSummary
import com.sakuguard.app.domain.model.WalletSummary
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class DashboardGoalItem(
    val summary: GoalSummary,
    val progressPermille: Int,
)

data class DashboardSnapshot(
    val overview: FinancialOverview = FinancialOverview(0L, 0L, 0L, 0L),
    val monthlyActivity: MonthlyActivity = MonthlyActivity(0L, 0L, 0L),
    val activeWallets: List<WalletSummary> = emptyList(),
    val activeGoals: List<DashboardGoalItem> = emptyList(),
    val recentTransactions: List<TransactionFeedItem> = emptyList(),
    val hasAnyData: Boolean = false,
)

/**
 * Observes bounded SQL aggregations and recent transactions (LIMIT 5) for the Dashboard.
 * Uses `combine` over Room Flows, converging to the latest committed state after atomic writes.
 */
class ObserveDashboardUseCase(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val goalRepository: GoalRepository,
    private val categoryRepository: CategoryRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    @Inject
    constructor(
        walletRepository: WalletRepository,
        transactionRepository: TransactionRepository,
        goalRepository: GoalRepository,
        categoryRepository: CategoryRepository,
    ) : this(walletRepository, transactionRepository, goalRepository, categoryRepository, Dispatchers.Default)
    operator fun invoke(
        zoneId: ZoneId = ZoneId.systemDefault(),
        yearMonth: YearMonth = YearMonth.now(zoneId),
    ): Flow<DashboardSnapshot> {
        val monthStart = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val monthEndExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        return combine(
            walletRepository.observeWallets(includeArchived = true),
            transactionRepository.observeAllWalletLedgerTotals(),
            transactionRepository.observeMonthlyActivity(monthStart, monthEndExclusive),
            transactionRepository.observeRecentTransactions(limit = 5),
            goalRepository.observeGoals(includeArchived = true),
            goalRepository.observeAllocations(),
            categoryRepository.observeCategories(includeArchived = true),
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val wallets = values[0] as List<com.sakuguard.app.domain.model.Wallet>
            @Suppress("UNCHECKED_CAST")
            val totalsByWallet = values[1] as Map<String, com.sakuguard.app.domain.repository.WalletLedgerTotals>
            val monthlyTotals = values[2] as com.sakuguard.app.domain.repository.MonthlyActivityTotals
            @Suppress("UNCHECKED_CAST")
            val recentTxs = values[3] as List<com.sakuguard.app.domain.model.Transaction>
            @Suppress("UNCHECKED_CAST")
            val goals = values[4] as List<com.sakuguard.app.domain.model.Goal>
            @Suppress("UNCHECKED_CAST")
            val allocations = values[5] as List<com.sakuguard.app.domain.model.GoalAllocationEntry>
            @Suppress("UNCHECKED_CAST")
            val categories = values[6] as List<com.sakuguard.app.domain.model.Category>

            val overview = FinancialEngine.calculateFinancialOverviewFromTotals(
                wallets = wallets,
                totalsByWallet = totalsByWallet,
                goals = goals,
                allocations = allocations,
            )
            val monthlyActivity = MonthlyActivity(
                income = monthlyTotals.income,
                expenses = monthlyTotals.expenses,
                netCashFlow = SafeMath.subtract(monthlyTotals.income, monthlyTotals.expenses),
            )
            val activeWallets = wallets
                .filterNot { it.isArchived }
                .map { wallet ->
                    val totals = totalsByWallet[wallet.id]
                        ?: com.sakuguard.app.domain.repository.WalletLedgerTotals(wallet.id, 0L, 0L, 0L, 0L)
                    FinancialEngine.summarizeWalletFromTotals(wallet, totals, allocations)
                }
            val activeGoals = goals
                .filter { it.status == GoalStatus.ACTIVE }
                .map { goal ->
                    val summary = FinancialEngine.summarizeGoal(goal, allocations)
                    DashboardGoalItem(
                        summary = summary,
                        progressPermille = FinancialEngine.calculateGoalProgressPermille(
                            allocated = summary.allocatedAmount,
                            target = goal.targetAmount,
                        ),
                    )
                }
            val walletMap = wallets.associateBy { it.id }
            val categoryMap = categories.associateBy { it.id }
            val recentTransactions = recentTxs.map { tx ->
                TransactionFeedItem(
                    transaction = tx,
                    wallet = walletMap[tx.walletId],
                    destinationWallet = tx.destinationWalletId?.let { walletMap[it] },
                    category = tx.categoryId?.let { categoryMap[it] },
                )
            }
            val hasAnyData = wallets.isNotEmpty() || goals.isNotEmpty() || recentTxs.isNotEmpty()

            DashboardSnapshot(
                overview = overview,
                monthlyActivity = monthlyActivity,
                activeWallets = activeWallets,
                activeGoals = activeGoals,
                recentTransactions = recentTransactions,
                hasAnyData = hasAnyData,
            )
        }.distinctUntilChanged().flowOn(computeDispatcher)
    }
}
