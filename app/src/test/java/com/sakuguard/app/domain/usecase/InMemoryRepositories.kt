package com.sakuguard.app.domain.usecase

import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.Wallet
import com.sakuguard.app.domain.repository.CategoryRepository
import com.sakuguard.app.domain.repository.GoalRepository
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Atomic in-memory repositories for deterministic JVM invariant testing.
 */
class InMemoryLedgerStore : WalletRepository, CategoryRepository, GoalRepository, TransactionRepository {
    val wallets = MutableStateFlow<List<Wallet>>(emptyList())
    val categories = MutableStateFlow<List<Category>>(emptyList())
    val goals = MutableStateFlow<List<Goal>>(emptyList())
    val transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val allocations = MutableStateFlow<List<GoalAllocationEntry>>(emptyList())

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
    override suspend fun getAllTransactions(): List<Transaction> = transactions.value
    override suspend fun getTransactionById(id: String): Transaction? = transactions.value.firstOrNull { it.id == id }
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
        try {
            block()
        } catch (t: Throwable) {
            wallets.value = snapW
            categories.value = snapC
            goals.value = snapG
            transactions.value = snapT
            allocations.value = snapA
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
}
