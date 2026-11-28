package com.sakuguard.app.data.repository

import androidx.room.withTransaction
import com.sakuguard.app.core.database.SakuGuardDatabase
import com.sakuguard.app.core.database.dao.GoalAllocationDao
import com.sakuguard.app.core.database.dao.TransactionDao
import com.sakuguard.app.data.mapper.toDomain
import com.sakuguard.app.data.mapper.toEntity
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.ReportCashFlowBucket
import com.sakuguard.app.domain.model.ReportTimeBucket
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.model.TransactionType
import com.sakuguard.app.domain.repository.CategoryTotal
import com.sakuguard.app.domain.repository.MonthlyActivityTotals
import com.sakuguard.app.domain.repository.TransactionPageQuery
import com.sakuguard.app.domain.repository.TransactionRepository
import com.sakuguard.app.domain.repository.WalletLedgerTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val database: SakuGuardDatabase,
    private val transactionDao: TransactionDao,
    private val goalAllocationDao: GoalAllocationDao,
) : TransactionRepository {
    override fun observeTransactions(): Flow<List<Transaction>> =
        transactionDao.observeTransactions().map { list -> list.map { it.toDomain() } }

    override fun observeRecentTransactions(limit: Int): Flow<List<Transaction>> =
        transactionDao.observeRecentTransactions(limit).map { list -> list.map { it.toDomain() } }

    override fun observeAllWalletLedgerTotals(): Flow<Map<String, WalletLedgerTotals>> =
        transactionDao.observeAllWalletLedgerTotals().map { list ->
            list.associate {
                it.walletId to WalletLedgerTotals(
                    walletId = it.walletId,
                    incomeSum = it.incomeSum,
                    expenseSum = it.expenseSum,
                    outgoingTransferSum = it.outgoingTransferSum,
                    incomingTransferSum = it.incomingTransferSum,
                )
            }
        }

    override fun observeMonthlyActivity(startInclusive: Long, endExclusive: Long): Flow<MonthlyActivityTotals> =
        transactionDao.observeMonthlyActivity(startInclusive, endExclusive).map {
            MonthlyActivityTotals(income = it.income, expenses = it.expenses)
        }

    override fun observeCategoryTotalsForPeriod(
        type: TransactionType,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<CategoryTotal>> =
        transactionDao.observeCategoryTotalsForPeriod(type, startInclusive, endExclusive).map { list ->
            list.map { CategoryTotal(categoryId = it.categoryId, totalAmount = it.totalAmount) }
        }

    override fun observeWalletActivityForPeriod(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<Map<String, WalletLedgerTotals>> =
        transactionDao.observeWalletActivityForPeriod(startInclusive, endExclusive).map { list ->
            list.associate {
                it.walletId to WalletLedgerTotals(
                    walletId = it.walletId,
                    incomeSum = it.incomeSum,
                    expenseSum = it.expenseSum,
                    outgoingTransferSum = it.outgoingTransferSum,
                    incomingTransferSum = it.incomingTransferSum,
                )
            }
        }

    override suspend fun getTimeSeriesBuckets(buckets: List<ReportTimeBucket>): List<ReportCashFlowBucket> =
        database.withTransaction {
            buckets.map { bucket ->
                val totals = transactionDao.getPeriodTotals(bucket.startInclusive, bucket.endExclusive)
                ReportCashFlowBucket(
                    label = bucket.label,
                    income = totals.income,
                    expense = totals.expenses,
                )
            }
        }

    override fun observeExpenseSumForCategoryPeriod(
        categoryId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<Long> = transactionDao.observeExpenseSumForCategoryPeriod(categoryId, startInclusive, endExclusive)

    override suspend fun getAllTransactions(): List<Transaction> =
        transactionDao.getAllTransactions().map { it.toDomain() }

    override suspend fun getTransactionById(id: String): Transaction? =
        transactionDao.getTransactionById(id)?.toDomain()

    override fun observeTransactionById(id: String): Flow<Transaction?> =
        transactionDao.observeTransactionById(id).map { it?.toDomain() }

    override suspend fun getEarliestTransactionDateForWallet(walletId: String): Long? =
        transactionDao.getEarliestTransactionDateForWallet(walletId)

    override suspend fun getWalletLedgerTotals(walletId: String): WalletLedgerTotals {
        val tuple = transactionDao.getWalletLedgerTotals(walletId)
        return WalletLedgerTotals(
            walletId = tuple.walletId,
            incomeSum = tuple.incomeSum,
            expenseSum = tuple.expenseSum,
            outgoingTransferSum = tuple.outgoingTransferSum,
            incomingTransferSum = tuple.incomingTransferSum,
        )
    }

    override suspend fun getExpenseSumForCategoryPeriod(
        categoryId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Long = transactionDao.getExpenseSumForCategoryPeriod(categoryId, startInclusive, endExclusive)

    override suspend fun getTransactionsPage(query: TransactionPageQuery): List<Transaction> {
        val hasCursor = query.cursorDate != null && query.cursorCreatedAt != null && query.cursorId != null
        return transactionDao.getTransactionsPage(
            type = query.type,
            walletId = query.walletId,
            categoryId = query.categoryId,
            startDate = query.startDate,
            endDateExclusive = query.endDateExclusive,
            searchQuery = query.searchQuery?.trim()?.takeIf { it.isNotEmpty() },
            hasCursor = hasCursor,
            cursorDate = query.cursorDate ?: 0L,
            cursorCreatedAt = query.cursorCreatedAt ?: 0L,
            cursorId = query.cursorId ?: "",
            limit = query.limit,
        ).map { it.toDomain() }
    }

    override suspend fun getAllocationByTransactionId(transactionId: String): GoalAllocationEntry? =
        goalAllocationDao.getByTransactionId(transactionId)?.toDomain()

    override suspend fun getAllocationByLinkedExpenseId(expenseTransactionId: String): GoalAllocationEntry? =
        goalAllocationDao.getByLinkedExpenseId(expenseTransactionId)?.toDomain()

    override suspend fun runInTransaction(block: suspend () -> Unit) {
        database.withTransaction { block() }
    }

    override suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insert(transaction.toEntity())
    }

    override suspend fun updateTransactionRecord(transaction: Transaction) {
        transactionDao.update(transaction.toEntity())
    }

    override suspend fun deleteTransactionById(transactionId: String) {
        transactionDao.deleteById(transactionId)
    }

    override suspend fun insertAllocationEntry(entry: GoalAllocationEntry) {
        goalAllocationDao.insert(entry.toEntity())
    }

    override suspend fun updateAllocationEntry(entry: GoalAllocationEntry) {
        goalAllocationDao.update(entry.toEntity())
    }

    override suspend fun deleteAllocationEntryById(entryId: String) {
        goalAllocationDao.deleteById(entryId)
    }
}
