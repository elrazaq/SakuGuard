package com.sakuguard.app.domain.usecase

import com.sakuguard.app.core.formatting.DateFormatter
import com.sakuguard.app.domain.engine.SafeMath
import com.sakuguard.app.domain.model.Budget
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.repository.BudgetRepository
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.repository.MonthlyActivityTotals
import com.sakuguard.app.domain.repository.TransactionPageQuery
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletLedgerTotals
import com.sakuguard.app.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Atomic in-memory repositories for deterministic JVM invariant testing.
 */
class InMemoryLedgerStore :
    WalletRepository,
    CategoryRepository,
    GoalRepository,
    TransactionRepository,
    BudgetRepository {

    val wallets = MutableStateFlow<List<Wallet>>(emptyList())
    val categories = MutableStateFlow<List<Category>>(emptyList())
    val goals = MutableStateFlow<List<Goal>>(emptyList())
    val transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val allocations = MutableStateFlow<List<GoalAllocationEntry>>(emptyList())
    val budgets = MutableStateFlow<List<Budget>>(emptyList())

    override fun observeWallets(includeArchived: Boolean): Flow<List<Wallet>> =
        wallets.map { list -> if (includeArchived) list else list.filterNot { it.isArchived } }
    override suspend fun getAllWallets(): List<Wallet> = wallets.value
    override suspend fun getWalletById(id: String): Wallet? = wallets.value.firstOrNull { it.id == id }
    override suspend fun createWallet(wallet: Wallet) { wallets.value = wallets.value + wallet }
    override suspend fun updateWallet(wallet: Wallet) { wallets.value = wallets.value.map { if (it.id == wallet.id) wallet else it } }
    override suspend fun setArchived(walletId: String, archived: Boolean, updatedAt: Long) {
        wallets.value = wallets.value.map { if (it.id == walletId) it.copy(isArchived = archived, updatedAt = updatedAt) else it }
    }

    override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> =
        categories.map { list -> if (includeArchived) list else list.filterNot { it.isArchived } }
    override suspend fun getAllCategories(): List<Category> = categories.value
    override suspend fun getCategoryById(id: String): Category? = categories.value.firstOrNull { it.id == id }
    override suspend fun upsertCategory(category: Category) {
        categories.value = categories.value.filterNot { it.id == category.id } + category
    }
    override suspend fun setArchived(categoryId: String, archived: Boolean) {
        categories.value = categories.value.map { if (it.id == categoryId) it.copy(isArchived = archived) else it }
    }

    override fun observeGoals(includeArchived: Boolean): Flow<List<Goal>> =
        goals.map { list -> if (includeArchived) list else list.filterNot { it.status == GoalStatus.ARCHIVED } }
    override fun observeAllocations(): Flow<List<GoalAllocationEntry>> = allocations
    override suspend fun getAllGoals(): List<Goal> = goals.value
    override suspend fun getGoalById(id: String): Goal? = goals.value.firstOrNull { it.id == id }
    override suspend fun getAllAllocations(): List<GoalAllocationEntry> = allocations.value
    override suspend fun createGoal(goal: Goal) { goals.value = goals.value + goal }
    override suspend fun updateGoal(goal: Goal) { goals.value = goals.value.map { if (it.id == goal.id) goal else it } }
    override suspend fun updateProtectionMode(goalId: String, mode: ProtectionMode, updatedAt: Long) {
        goals.value = goals.value.map { if (it.id == goalId) it.copy(protectionMode = mode, updatedAt = updatedAt) else it }
    }
    override suspend fun updateStatus(goalId: String, status: GoalStatus, completedAt: Long?, updatedAt: Long) {
        goals.value = goals.value.map { if (it.id == goalId) it.copy(status = status, completedAt = completedAt, updatedAt = updatedAt) else it }
    }

    override fun observeTransactions(): Flow<List<Transaction>> = transactions
    override fun observeRecentTransactions(limit: Int): Flow<List<Transaction>> =
        transactions.map { list ->
            list.sortedWith(compareByDescending<Transaction> { it.transactionDate }.thenByDescending { it.createdAt }.thenByDescending { it.id })
                .take(limit)
        }

    override fun observeAllWalletLedgerTotals(): Flow<Map<String, WalletLedgerTotals>> =
        combine(wallets, transactions) { wList, tList ->
            wList.associate { w -> w.id to computeTotalsForWallet(w.id, tList) }
        }

    override fun observeMonthlyActivity(startInclusive: Long, endExclusive: Long): Flow<MonthlyActivityTotals> =
        transactions.map { list ->
            var inc = 0L
            var exp = 0L
            for (tx in list) {
                if (tx.transactionDate in startInclusive until endExclusive) {
                    if (tx.type == TransactionType.INCOME) inc = SafeMath.add(inc, tx.amount)
                    if (tx.type == TransactionType.EXPENSE) exp = SafeMath.add(exp, tx.amount)
                }
            }
            MonthlyActivityTotals(inc, exp)
        }

    override fun observeExpenseSumForCategoryPeriod(
        categoryId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<Long> = transactions.map { computeCategoryExpense(it, categoryId, startInclusive, endExclusive) }

    override suspend fun getAllTransactions(): List<Transaction> = transactions.value
    override suspend fun getTransactionById(id: String): Transaction? = transactions.value.firstOrNull { it.id == id }
    override suspend fun getWalletLedgerTotals(walletId: String): WalletLedgerTotals =
        computeTotalsForWallet(walletId, transactions.value)

    override suspend fun getExpenseSumForCategoryPeriod(
        categoryId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Long = computeCategoryExpense(transactions.value, categoryId, startInclusive, endExclusive)

    override suspend fun getTransactionsPage(query: TransactionPageQuery): List<Transaction> {
        val walletMap = wallets.value.associateBy { it.id }
        val catMap = categories.value.associateBy { it.id }
        val q = query.searchQuery?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return transactions.value
            .sortedWith(compareByDescending<Transaction> { it.transactionDate }.thenByDescending { it.createdAt }.thenByDescending { it.id })
            .filter { tx ->
                if (query.type != null && tx.type != query.type) return@filter false
                if (query.walletId != null && tx.walletId != query.walletId && tx.destinationWalletId != query.walletId) return@filter false
                if (query.categoryId != null && tx.categoryId != query.categoryId) return@filter false
                if (query.startDate != null && tx.transactionDate < query.startDate) return@filter false
                if (query.endDateExclusive != null && tx.transactionDate >= query.endDateExclusive) return@filter false
                if (q != null) {
                    val matches = listOfNotNull(
                        tx.description,
                        tx.notes,
                        tx.categoryId?.let { catMap[it]?.name },
                        walletMap[tx.walletId]?.name,
                        tx.destinationWalletId?.let { walletMap[it]?.name },
                    ).any { it.lowercase().contains(q) }
                    if (!matches) return@filter false
                }
                if (query.cursorDate != null && query.cursorCreatedAt != null && query.cursorId != null) {
                    val before = tx.transactionDate < query.cursorDate ||
                        (tx.transactionDate == query.cursorDate && tx.createdAt < query.cursorCreatedAt) ||
                        (tx.transactionDate == query.cursorDate && tx.createdAt == query.cursorCreatedAt && tx.id < query.cursorId)
                    if (!before) return@filter false
                }
                true
            }
            .take(query.limit)
    }

    override suspend fun getAllocationByTransactionId(transactionId: String): GoalAllocationEntry? =
        allocations.value.firstOrNull { it.transactionId == transactionId }
    override suspend fun getAllocationByLinkedExpenseId(expenseTransactionId: String): GoalAllocationEntry? =
        allocations.value.firstOrNull { it.linkedExpenseTransactionId == expenseTransactionId }

    override suspend fun runInTransaction(block: suspend () -> Unit) {
        val snapW = wallets.value
        val snapC = categories.value
        val snapG = goals.value
        val snapT = transactions.value
        val snapA = allocations.value
        val snapB = budgets.value
        try {
            block()
        } catch (t: Throwable) {
            wallets.value = snapW
            categories.value = snapC
            goals.value = snapG
            transactions.value = snapT
            allocations.value = snapA
            budgets.value = snapB
            throw t
        }
    }

    override suspend fun insertTransaction(transaction: Transaction) { transactions.value = transactions.value + transaction }
    override suspend fun updateTransactionRecord(transaction: Transaction) {
        transactions.value = transactions.value.map { if (it.id == transaction.id) transaction else it }
    }
    override suspend fun deleteTransactionById(transactionId: String) {
        transactions.value = transactions.value.filterNot { it.id == transactionId }
    }
    override suspend fun insertAllocationEntry(entry: GoalAllocationEntry) { allocations.value = allocations.value + entry }
    override suspend fun updateAllocationEntry(entry: GoalAllocationEntry) {
        allocations.value = allocations.value.map { if (it.id == entry.id) entry else it }
    }
    override suspend fun deleteAllocationEntryById(entryId: String) {
        allocations.value = allocations.value.filterNot { it.id == entryId }
    }

    override fun observeBudgets(includeArchived: Boolean): Flow<List<Budget>> =
        budgets.map { list -> if (includeArchived) list else list.filterNot { it.isArchived } }
    override suspend fun getAllBudgets(): List<Budget> = budgets.value
    override suspend fun getBudgetById(id: String): Budget? = budgets.value.firstOrNull { it.id == id }
    override suspend fun createBudget(budget: Budget) { budgets.value = budgets.value + budget }
    override suspend fun updateBudget(budget: Budget) {
        budgets.value = budgets.value.map { if (it.id == budget.id) budget else it }
    }
    override suspend fun deleteBudgetById(budgetId: String) {
        budgets.value = budgets.value.filterNot { it.id == budgetId }
    }
    override suspend fun setArchived(budgetId: String, archived: Boolean, updatedAt: Long) {
        budgets.value = budgets.value.map { if (it.id == budgetId) it.copy(isArchived = archived, updatedAt = updatedAt) else it }
    }
    override suspend fun updateAlertState(budgetId: String, periodKey: String, level: Int, updatedAt: Long) {
        budgets.value = budgets.value.map {
            if (it.id == budgetId) it.copy(lastAlertedPeriodKey = periodKey, lastAlertedLevel = level, updatedAt = updatedAt) else it
        }
    }

    private fun computeTotalsForWallet(walletId: String, txs: List<Transaction>): WalletLedgerTotals {
        var inc = 0L
        var exp = 0L
        var outX = 0L
        var inX = 0L
        for (tx in txs) {
            when (tx.type) {
                TransactionType.INCOME -> if (tx.walletId == walletId) inc = SafeMath.add(inc, tx.amount)
                TransactionType.EXPENSE -> if (tx.walletId == walletId) exp = SafeMath.add(exp, tx.amount)
                TransactionType.TRANSFER -> {
                    if (tx.walletId == walletId) outX = SafeMath.add(outX, tx.amount)
                    if (tx.destinationWalletId == walletId) inX = SafeMath.add(inX, tx.amount)
                }
                else -> Unit
            }
        }
        return WalletLedgerTotals(walletId, inc, exp, outX, inX)
    }

    private fun computeCategoryExpense(
        txs: List<Transaction>,
        categoryId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Long {
        var sum = 0L
        for (tx in txs) {
            if (tx.type == TransactionType.EXPENSE &&
                tx.categoryId == categoryId &&
                tx.transactionDate in startInclusive until endExclusive
            ) {
                sum = SafeMath.add(sum, tx.amount)
            }
        }
        return sum
    }
}
