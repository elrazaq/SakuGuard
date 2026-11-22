package com.sakuguard.app.data.repository

import androidx.room.withTransaction
import com.sakuguard.app.core.database.SakuGuardDatabase
import com.sakuguard.app.core.database.dao.GoalAllocationDao
import com.sakuguard.app.core.database.dao.TransactionDao
import com.sakuguard.app.data.mapper.toDomain
import com.sakuguard.app.data.mapper.toEntity
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.Transaction
import com.sakuguard.app.domain.repository.TransactionRepository
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

    override suspend fun getAllTransactions(): List<Transaction> =
        transactionDao.getAllTransactions().map { it.toDomain() }

    override suspend fun getTransactionById(id: String): Transaction? =
        transactionDao.getTransactionById(id)?.toDomain()

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
