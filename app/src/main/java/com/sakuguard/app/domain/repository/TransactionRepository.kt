package com.sakuguard.app.domain.repository

import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.ReportCashFlowBucket
import com.sakuguard.app.domain.model.ReportTimeBucket
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

data class CategoryTotal(
    val categoryId: String?,
    val totalAmount: Long,
)

data class LedgerChangeSignal(
    val count: Int,
    val maxUpdatedAt: Long,
    val amountSum: Long,
)

data class WalletLedgerTotals(
    val walletId: String,
    val incomeSum: Long,
    val expenseSum: Long,
    val outgoingTransferSum: Long,
    val incomingTransferSum: Long,
)

data class MonthlyActivityTotals(
    val income: Long,
    val expenses: Long,
)

data class TransactionPageQuery(
    val type: TransactionType? = null,
    val walletId: String? = null,
    val categoryId: String? = null,
    val startDate: Long? = null,
    val endDateExclusive: Long? = null,
    val searchQuery: String? = null,
    val cursorDate: Long? = null,
    val cursorCreatedAt: Long? = null,
    val cursorId: String? = null,
    val limit: Int = 20,
)

interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>
    fun observeRecentTransactions(limit: Int = 5): Flow<List<Transaction>>
    fun observeAllWalletLedgerTotals(): Flow<Map<String, WalletLedgerTotals>>
    fun observeMonthlyActivity(startInclusive: Long, endExclusive: Long): Flow<MonthlyActivityTotals>
    fun observeCategoryTotalsForPeriod(
        type: TransactionType,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<CategoryTotal>>
    fun observeWalletActivityForPeriod(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<Map<String, WalletLedgerTotals>>
    suspend fun getTimeSeriesBuckets(buckets: List<ReportTimeBucket>): List<ReportCashFlowBucket>
    fun observeExpenseSumForCategoryPeriod(
        categoryId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<Long>

    suspend fun getAllTransactions(): List<Transaction>
    suspend fun getTransactionById(id: String): Transaction?
    fun observeTransactionById(id: String): Flow<Transaction?>
    fun observeLedgerChangeSignal(): Flow<LedgerChangeSignal>
    suspend fun countTransactionsForWallet(walletId: String): Int
    suspend fun countTransactionsForGoal(goalId: String): Int
    suspend fun getEarliestTransactionDateForWallet(walletId: String): Long?
    suspend fun getWalletLedgerTotals(walletId: String): WalletLedgerTotals
    suspend fun getExpenseSumForCategoryPeriod(
        categoryId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Long

    suspend fun getTransactionsPage(query: TransactionPageQuery): List<Transaction>

    suspend fun getAllocationByTransactionId(transactionId: String): GoalAllocationEntry?
    suspend fun getAllocationByLinkedExpenseId(expenseTransactionId: String): GoalAllocationEntry?

    suspend fun runInTransaction(block: suspend () -> Unit)

    suspend fun insertTransaction(transaction: Transaction)
    suspend fun updateTransactionRecord(transaction: Transaction)
    suspend fun deleteTransactionById(transactionId: String)

    suspend fun insertAllocationEntry(entry: GoalAllocationEntry)
    suspend fun updateAllocationEntry(entry: GoalAllocationEntry)
    suspend fun deleteAllocationEntryById(entryId: String)
}
