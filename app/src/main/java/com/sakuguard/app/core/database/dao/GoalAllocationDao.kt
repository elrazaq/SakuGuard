package com.sakuguard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sakuguard.app.core.database.entity.GoalAllocationEntity
import com.sakuguard.app.domain.model.GoalLedgerType
import kotlinx.coroutines.flow.Flow

data class GoalPeriodContributionTuple(
    val entryType: GoalLedgerType,
    val totalAmount: Long,
)

@Dao
interface GoalAllocationDao {
    @Query("SELECT * FROM goal_allocations ORDER BY createdAt ASC")
    fun observeAllocations(): Flow<List<GoalAllocationEntity>>

    @Query(
        """
        SELECT ga.entryType AS entryType, COALESCE(SUM(ga.amount), 0) AS totalAmount
        FROM goal_allocations ga
        INNER JOIN transactions t ON ga.transactionId = t.id
        WHERE t.transactionDate >= :startInclusive
          AND t.transactionDate < :endExclusive
        GROUP BY ga.entryType
        """,
    )
    fun observePeriodGoalContributions(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<GoalPeriodContributionTuple>>

    @Query("SELECT * FROM goal_allocations ORDER BY createdAt ASC")
    suspend fun getAllAllocations(): List<GoalAllocationEntity>

    @Query("SELECT * FROM goal_allocations WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getByTransactionId(transactionId: String): GoalAllocationEntity?

    @Query("SELECT * FROM goal_allocations WHERE linkedExpenseTransactionId = :expenseId LIMIT 1")
    suspend fun getByLinkedExpenseId(expenseId: String): GoalAllocationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: GoalAllocationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<GoalAllocationEntity>)

    @Query("DELETE FROM goal_allocations")
    suspend fun deleteAll()

    @Update
    suspend fun update(entry: GoalAllocationEntity)

    @Query("DELETE FROM goal_allocations WHERE id = :id")
    suspend fun deleteById(id: String)
}
