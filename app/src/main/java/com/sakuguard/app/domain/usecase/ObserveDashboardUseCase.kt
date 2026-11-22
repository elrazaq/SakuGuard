package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.engine.FinancialEngine
import com.sakuguard.app.domain.engine.MonthlyActivity
import com.sakuguard.app.domain.model.FinancialOverview
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.GoalSummary
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.WalletSummary
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
 * Observes reactive repository flows and derives dashboard state via [FinancialEngine].
 * Uses `combine` over Room table Flows, converging to the latest committed state after atomic writes.
 */
class ObserveDashboardUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val goalRepository: GoalRepository,
    private val categoryRepository: CategoryRepository,
) {
    operator fun invoke(
        zoneId: ZoneId = ZoneId.systemDefault(),
        yearMonth: YearMonth = YearMonth.now(zoneId),
    ): Flow<DashboardSnapshot> = combine(
        walletRepository.observeWallets(includeArchived = true),
        transactionRepository.observeTransactions(),
        goalRepository.observeGoals(includeArchived = true),
        goalRepository.observeAllocations(),
        categoryRepository.observeCategories(includeArchived = true),
    ) { wallets, transactions, goals, allocations, categories ->
        val overview = FinancialEngine.calculateFinancialOverview(
            wallets = wallets,
            transactions = transactions,
            goals = goals,
            allocations = allocations,
        )
        val monthlyActivity = FinancialEngine.calculateMonthlyActivity(
            transactions = transactions,
            yearMonth = yearMonth,
            zoneId = zoneId,
        )
        val activeWallets = wallets
            .filterNot { it.isArchived }
            .map { FinancialEngine.summarizeWallet(it, transactions, allocations) }
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
        val recentTransactions = transactions
            .sortedWith(
                compareByDescending<Transaction> { it.transactionDate }
                    .thenByDescending { it.createdAt },
            )
            .take(5)
            .map { tx ->
                TransactionFeedItem(
                    transaction = tx,
                    wallet = walletMap[tx.walletId],
                    destinationWallet = tx.destinationWalletId?.let { walletMap[it] },
                    category = tx.categoryId?.let { categoryMap[it] },
                )
            }
        val hasAnyData = wallets.isNotEmpty() || goals.isNotEmpty() || transactions.isNotEmpty()

        DashboardSnapshot(
            overview = overview,
            monthlyActivity = monthlyActivity,
            activeWallets = activeWallets,
            activeGoals = activeGoals,
            recentTransactions = recentTransactions,
            hasAnyData = hasAnyData,
        )
    }
}
