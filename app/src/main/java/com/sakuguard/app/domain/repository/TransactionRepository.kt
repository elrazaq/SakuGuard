package com.sakuguard.app.domain.repository

import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>
    suspend fun getAllTransactions(): List<Transaction>
    suspend fun getTransactionById(id: String): Transaction?
    suspend fun getAllocationByTransactionId(transactionId: String): GoalAllocationEntry?
    suspend fun getAllocationByLinkedExpenseId(expenseTransactionId: String): GoalAllocationEntry?

    /**
     * Atomically validates and persists a new transaction (and any linked goal/expense entries).
     * Implementations must execute inside a single database transaction and roll back on failure.
     */
    suspend fun runInTransaction(block: suspend () -> Unit)

    suspend fun insertTransaction(transaction: Transaction)
    suspend fun updateTransactionRecord(transaction: Transaction)
    suspend fun deleteTransactionById(transactionId: String)

    suspend fun insertAllocationEntry(entry: GoalAllocationEntry)
    suspend fun updateAllocationEntry(entry: GoalAllocationEntry)
    suspend fun deleteAllocationEntryById(entryId: String)
}
