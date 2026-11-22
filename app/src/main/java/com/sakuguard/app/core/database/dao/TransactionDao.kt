package com.sakuguard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sakuguard.app.core.database.entity.TransactionEntity
import com.sakuguard.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

data class WalletLedgerTotalsTuple(
    val walletId: String,
    val incomeSum: Long,
    val expenseSum: Long,
    val outgoingTransferSum: Long,
    val incomingTransferSum: Long,
)

data class MonthlyActivityTuple(
    val income: Long,
    val expenses: Long,
)

data class CategoryExpenseTotalTuple(
    val categoryId: String,
    val totalAmount: Long,
)

data class CategoryTotalTuple(
    val categoryId: String?,
    val totalAmount: Long,
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC, createdAt DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC, createdAt DESC, id DESC LIMIT :limit")
    fun observeRecentTransactions(limit: Int = 5): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY transactionDate ASC, createdAt ASC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Query(
        """
        SELECT :walletId AS walletId,
            COALESCE((SELECT SUM(amount) FROM transactions WHERE walletId = :walletId AND type = 'INCOME'), 0) AS incomeSum,
            COALESCE((SELECT SUM(amount) FROM transactions WHERE walletId = :walletId AND type = 'EXPENSE'), 0) AS expenseSum,
            COALESCE((SELECT SUM(amount) FROM transactions WHERE walletId = :walletId AND type = 'TRANSFER'), 0) AS outgoingTransferSum,
            COALESCE((SELECT SUM(amount) FROM transactions WHERE destinationWalletId = :walletId AND type = 'TRANSFER'), 0) AS incomingTransferSum
        """,
    )
    suspend fun getWalletLedgerTotals(walletId: String): WalletLedgerTotalsTuple

    @Query(
        """
        SELECT w.id AS walletId,
            COALESCE((SELECT SUM(amount) FROM transactions WHERE walletId = w.id AND type = 'INCOME'), 0) AS incomeSum,
            COALESCE((SELECT SUM(amount) FROM transactions WHERE walletId = w.id AND type = 'EXPENSE'), 0) AS expenseSum,
            COALESCE((SELECT SUM(amount) FROM transactions WHERE walletId = w.id AND type = 'TRANSFER'), 0) AS outgoingTransferSum,
            COALESCE((SELECT SUM(amount) FROM transactions WHERE destinationWalletId = w.id AND type = 'TRANSFER'), 0) AS incomingTransferSum
        FROM wallets w
        """,
    )
    fun observeAllWalletLedgerTotals(): Flow<List<WalletLedgerTotalsTuple>>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expenses
        FROM transactions
        WHERE transactionDate >= :startInclusive AND transactionDate < :endExclusive
        """,
    )
    fun observeMonthlyActivity(startInclusive: Long, endExclusive: Long): Flow<MonthlyActivityTuple>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expenses
        FROM transactions
        WHERE transactionDate >= :startInclusive AND transactionDate < :endExclusive
        """,
    )
    suspend fun getPeriodTotals(startInclusive: Long, endExclusive: Long): MonthlyActivityTuple

    @Query(
        """
        SELECT categoryId AS categoryId, COALESCE(SUM(amount), 0) AS totalAmount
        FROM transactions
        WHERE type = :type
          AND transactionDate >= :startInclusive
          AND transactionDate < :endExclusive
        GROUP BY categoryId
        ORDER BY totalAmount DESC
        """,
    )
    fun observeCategoryTotalsForPeriod(
        type: TransactionType,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<CategoryTotalTuple>>

    @Query(
        """
        SELECT w.id AS walletId,
            COALESCE((SELECT SUM(amount) FROM transactions WHERE walletId = w.id AND type = 'INCOME' AND transactionDate >= :startInclusive AND transactionDate < :endExclusive), 0) AS incomeSum,
            COALESCE((SELECT SUM(amount) FROM transactions WHERE walletId = w.id AND type = 'EXPENSE' AND transactionDate >= :startInclusive AND transactionDate < :endExclusive), 0) AS expenseSum,
            COALESCE((SELECT SUM(amount) FROM transactions WHERE walletId = w.id AND type = 'TRANSFER' AND transactionDate >= :startInclusive AND transactionDate < :endExclusive), 0) AS outgoingTransferSum,
            COALESCE((SELECT SUM(amount) FROM transactions WHERE destinationWalletId = w.id AND type = 'TRANSFER' AND transactionDate >= :startInclusive AND transactionDate < :endExclusive), 0) AS incomingTransferSum
        FROM wallets w
        """,
    )
    fun observeWalletActivityForPeriod(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<WalletLedgerTotalsTuple>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0)
        FROM transactions
        WHERE type = 'EXPENSE'
          AND categoryId = :categoryId
          AND transactionDate >= :startInclusive
          AND transactionDate < :endExclusive
        """,
    )
    fun observeExpenseSumForCategoryPeriod(
        categoryId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0)
        FROM transactions
        WHERE type = 'EXPENSE'
          AND categoryId = :categoryId
          AND transactionDate >= :startInclusive
          AND transactionDate < :endExclusive
        """,
    )
    suspend fun getExpenseSumForCategoryPeriod(
        categoryId: String,
        startInclusive: Long,
        endExclusive: Long,
    ): Long

    @Query(
        """
        SELECT t.* FROM transactions t
        LEFT JOIN categories c ON t.categoryId = c.id
        LEFT JOIN wallets w ON t.walletId = w.id
        LEFT JOIN wallets dw ON t.destinationWalletId = dw.id
        WHERE (:type IS NULL OR t.type = :type)
          AND (:walletId IS NULL OR t.walletId = :walletId OR t.destinationWalletId = :walletId)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:startDate IS NULL OR t.transactionDate >= :startDate)
          AND (:endDateExclusive IS NULL OR t.transactionDate < :endDateExclusive)
          AND (:searchQuery IS NULL OR
               t.description LIKE '%' || :searchQuery || '%' OR
               t.notes LIKE '%' || :searchQuery || '%' OR
               c.name LIKE '%' || :searchQuery || '%' OR
               w.name LIKE '%' || :searchQuery || '%' OR
               dw.name LIKE '%' || :searchQuery || '%')
          AND (:hasCursor = 0 OR
               t.transactionDate < :cursorDate OR
               (t.transactionDate = :cursorDate AND t.createdAt < :cursorCreatedAt) OR
               (t.transactionDate = :cursorDate AND t.createdAt = :cursorCreatedAt AND t.id < :cursorId))
        ORDER BY t.transactionDate DESC, t.createdAt DESC, t.id DESC
        LIMIT :limit
        """,
    )
    suspend fun getTransactionsPage(
        type: TransactionType?,
        walletId: String?,
        categoryId: String?,
        startDate: Long?,
        endDateExclusive: Long?,
        searchQuery: String?,
        hasCursor: Boolean,
        cursorDate: Long,
        cursorCreatedAt: Long,
        cursorId: String,
        limit: Int,
    ): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)
}
