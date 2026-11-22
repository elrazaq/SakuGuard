package com.sakuguard.app.domain.engine

import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.model.Budget
import com.sakuguard.app.domain.model.BudgetHealthStatus
import com.sakuguard.app.domain.model.BudgetSummary
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.FinancialOverview
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.GoalLedgerType
import com.sakuguard.app.domain.model.GoalSummary
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.model.WalletSummary
import com.sakuguard.app.domain.repository.WalletLedgerTotals
import java.time.YearMonth
import java.time.ZoneId

data class MonthlyActivity(
    val income: Long,
    val expenses: Long,
    val netCashFlow: Long,
)

/**
 * Pure Kotlin, deterministic financial calculation engine using overflow-safe [Long] arithmetic.
 */
object FinancialEngine {

    fun calculateWalletBalance(
        wallet: Wallet,
        transactions: List<Transaction>,
    ): Long {
        var balance = wallet.openingBalance
        for (tx in transactions) {
            when (tx.type) {
                TransactionType.INCOME -> {
                    if (tx.walletId == wallet.id) {
                        balance = SafeMath.add(balance, tx.amount)
                    }
                }
                TransactionType.EXPENSE -> {
                    if (tx.walletId == wallet.id) {
                        balance = SafeMath.subtract(balance, tx.amount)
                    }
                }
                TransactionType.TRANSFER -> {
                    if (tx.walletId == wallet.id) {
                        balance = SafeMath.subtract(balance, tx.amount)
                    }
                    if (tx.destinationWalletId == wallet.id) {
                        balance = SafeMath.add(balance, tx.amount)
                    }
                }
                // Goal allocations & withdrawals never alter wallet balance directly;
                // DIRECT_EXPENSE creates a separate EXPENSE transaction handled above.
                TransactionType.GOAL_ALLOCATION,
                TransactionType.GOAL_WITHDRAWAL -> Unit
            }
        }
        return balance
    }

    fun calculateWalletAllocatedFunds(
        walletId: String,
        allocations: List<GoalAllocationEntry>,
    ): Long {
        var allocated = 0L
        for (entry in allocations) {
            if (entry.walletId == walletId) {
                allocated = when (entry.entryType) {
                    GoalLedgerType.ALLOCATION -> SafeMath.add(allocated, entry.amount)
                    GoalLedgerType.WITHDRAWAL -> SafeMath.subtract(allocated, entry.amount)
                }
            }
        }
        return allocated
    }

    fun calculateWalletFreeMoney(
        walletBalance: Long,
        allocatedFunds: Long,
    ): Long = SafeMath.subtract(walletBalance, allocatedFunds)

    fun summarizeWallet(
        wallet: Wallet,
        transactions: List<Transaction>,
        allocations: List<GoalAllocationEntry>,
    ): WalletSummary {
        val balance = calculateWalletBalance(wallet, transactions)
        val allocated = calculateWalletAllocatedFunds(wallet.id, allocations)
        val free = calculateWalletFreeMoney(balance, allocated)
        return WalletSummary(
            wallet = wallet,
            currentBalance = balance,
            allocatedFunds = allocated,
            freeMoney = free,
        )
    }

    fun calculateGoalAllocatedAmount(
        goalId: String,
        allocations: List<GoalAllocationEntry>,
    ): Long {
        var allocated = 0L
        for (entry in allocations) {
            if (entry.goalId == goalId) {
                allocated = when (entry.entryType) {
                    GoalLedgerType.ALLOCATION -> SafeMath.add(allocated, entry.amount)
                    GoalLedgerType.WITHDRAWAL -> SafeMath.subtract(allocated, entry.amount)
                }
            }
        }
        return allocated
    }

    fun calculateGoalAllocatedInWallet(
        goalId: String,
        walletId: String,
        allocations: List<GoalAllocationEntry>,
    ): Long {
        var allocated = 0L
        for (entry in allocations) {
            if (entry.goalId == goalId && entry.walletId == walletId) {
                allocated = when (entry.entryType) {
                    GoalLedgerType.ALLOCATION -> SafeMath.add(allocated, entry.amount)
                    GoalLedgerType.WITHDRAWAL -> SafeMath.subtract(allocated, entry.amount)
                }
            }
        }
        return allocated
    }

    fun calculateGoalWalletBreakdown(
        goalId: String,
        allocations: List<GoalAllocationEntry>,
    ): Map<String, Long> {
        val breakdown = linkedMapOf<String, Long>()
        for (entry in allocations) {
            if (entry.goalId == goalId) {
                val current = breakdown[entry.walletId] ?: 0L
                val updated = when (entry.entryType) {
                    GoalLedgerType.ALLOCATION -> SafeMath.add(current, entry.amount)
                    GoalLedgerType.WITHDRAWAL -> SafeMath.subtract(current, entry.amount)
                }
                breakdown[entry.walletId] = updated
            }
        }
        return breakdown.filterValues { it > 0L }
    }

    fun summarizeGoal(
        goal: Goal,
        allocations: List<GoalAllocationEntry>,
    ): GoalSummary {
        val allocated = calculateGoalAllocatedAmount(goal.id, allocations)
        val remaining = SafeMath.subtract(goal.targetAmount, allocated).coerceAtLeast(0L)
        return GoalSummary(
            goal = goal,
            allocatedAmount = allocated,
            remainingAmount = remaining,
            walletBreakdown = calculateGoalWalletBreakdown(goal.id, allocations),
        )
    }

    /**
     * Overflow-safe integer progress in permille [0, 1000] for [target] > 0.
     */
    fun calculateGoalProgressPermille(
        allocated: Long,
        target: Long,
    ): Int {
        if (target <= 0L) {
            throw com.sakuguard.app.domain.model.DomainException.InvalidAmountException("Target amount must be positive")
        }
        if (allocated <= 0L) return 0
        if (allocated >= target) return 1000
        return java.math.BigInteger.valueOf(allocated)
            .multiply(java.math.BigInteger.valueOf(1000L))
            .divide(java.math.BigInteger.valueOf(target))
            .toInt()
            .coerceIn(0, 1000)
    }

    fun calculateFinancialOverview(
        wallets: List<Wallet>,
        transactions: List<Transaction>,
        goals: List<Goal>,
        allocations: List<GoalAllocationEntry>,
    ): FinancialOverview {
        val totalAssets = SafeMath.sum(wallets.map { calculateWalletBalance(it, transactions) })
        val allocatedByGoal = goals.associate { goal ->
            goal.id to calculateGoalAllocatedAmount(goal.id, allocations)
        }
        val allocatedFunds = SafeMath.sum(allocatedByGoal.values)
        val protectedFunds = SafeMath.sum(
            goals.filter {
                it.protectionMode == ProtectionMode.PROTECTED ||
                    it.protectionMode == ProtectionMode.STRICT
            }.map { allocatedByGoal[it.id] ?: 0L },
        )
        val freeMoney = SafeMath.subtract(totalAssets, allocatedFunds)
        return FinancialOverview(
            totalAssets = totalAssets,
            allocatedFunds = allocatedFunds,
            protectedFunds = protectedFunds,
            freeMoney = freeMoney,
        )
    }

    fun calculateMonthlyActivity(
        transactions: List<Transaction>,
        yearMonth: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): MonthlyActivity {
        var income = 0L
        var expenses = 0L
        for (tx in transactions) {
            val txMonth = YearMonth.from(DateFormatter.toLocalDate(tx.transactionDate, zoneId))
            if (txMonth != yearMonth) continue
            when (tx.type) {
                TransactionType.INCOME -> income = SafeMath.add(income, tx.amount)
                TransactionType.EXPENSE -> expenses = SafeMath.add(expenses, tx.amount)
                TransactionType.TRANSFER,
                TransactionType.GOAL_ALLOCATION,
                TransactionType.GOAL_WITHDRAWAL -> Unit
            }
        }
        val netCashFlow = SafeMath.subtract(income, expenses)
        return MonthlyActivity(
            income = income,
            expenses = expenses,
            netCashFlow = netCashFlow,
        )
    }

    fun calculateWalletBalanceFromTotals(
        openingBalance: Long,
        totals: WalletLedgerTotals,
    ): Long {
        var balance = SafeMath.add(openingBalance, totals.incomeSum)
        balance = SafeMath.subtract(balance, totals.expenseSum)
        balance = SafeMath.subtract(balance, totals.outgoingTransferSum)
        balance = SafeMath.add(balance, totals.incomingTransferSum)
        return balance
    }

    fun summarizeWalletFromTotals(
        wallet: Wallet,
        totals: WalletLedgerTotals,
        allocations: List<GoalAllocationEntry>,
    ): WalletSummary {
        val balance = calculateWalletBalanceFromTotals(wallet.openingBalance, totals)
        val allocated = calculateWalletAllocatedFunds(wallet.id, allocations)
        val free = calculateWalletFreeMoney(balance, allocated)
        return WalletSummary(
            wallet = wallet,
            currentBalance = balance,
            allocatedFunds = allocated,
            freeMoney = free,
        )
    }

    fun calculateFinancialOverviewFromTotals(
        wallets: List<Wallet>,
        totalsByWallet: Map<String, WalletLedgerTotals>,
        goals: List<Goal>,
        allocations: List<GoalAllocationEntry>,
    ): FinancialOverview {
        val totalAssets = SafeMath.sum(
            wallets.map { wallet ->
                val totals = totalsByWallet[wallet.id] ?: WalletLedgerTotals(wallet.id, 0L, 0L, 0L, 0L)
                calculateWalletBalanceFromTotals(wallet.openingBalance, totals)
            },
        )
        val allocatedByGoal = goals.associate { goal ->
            goal.id to calculateGoalAllocatedAmount(goal.id, allocations)
        }
        val allocatedFunds = SafeMath.sum(allocatedByGoal.values)
        val protectedFunds = SafeMath.sum(
            goals.filter {
                it.protectionMode == ProtectionMode.PROTECTED ||
                    it.protectionMode == ProtectionMode.STRICT
            }.map { allocatedByGoal[it.id] ?: 0L },
        )
        val freeMoney = SafeMath.subtract(totalAssets, allocatedFunds)
        return FinancialOverview(
            totalAssets = totalAssets,
            allocatedFunds = allocatedFunds,
            protectedFunds = protectedFunds,
            freeMoney = freeMoney,
        )
    }

    fun summarizeBudget(
        budget: Budget,
        category: Category?,
        spentAmount: Long,
        effectiveStart: Long,
        effectiveEndExclusive: Long,
    ): BudgetSummary {
        val remaining = SafeMath.subtract(budget.limitAmount, spentAmount).coerceAtLeast(0L)
        val overspent = if (spentAmount > budget.limitAmount) {
            SafeMath.subtract(spentAmount, budget.limitAmount)
        } else {
            0L
        }
        val permille = calculateGoalProgressPermille(spentAmount, budget.limitAmount)
        val status = when {
            spentAmount > budget.limitAmount -> BudgetHealthStatus.EXCEEDED
            permille >= 800 -> BudgetHealthStatus.WARNING
            else -> BudgetHealthStatus.ON_TRACK
        }
        return BudgetSummary(
            budget = budget,
            category = category,
            spentAmount = spentAmount,
            remainingAmount = remaining,
            overspentAmount = overspent,
            utilizationPermille = permille,
            healthStatus = status,
            effectiveStart = effectiveStart,
            effectiveEndExclusive = effectiveEndExclusive,
        )
    }
}
