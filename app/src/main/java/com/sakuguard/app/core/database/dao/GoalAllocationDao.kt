package com.sakuguard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sakuguard.app.core.database.entity.GoalAllocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalAllocationDao {
    @Query("SELECT * FROM goal_allocations ORDER BY createdAt ASC")
    fun observeAllocations(): Flow<List<GoalAllocationEntity>>

    @Query("SELECT * FROM goal_allocations ORDER BY createdAt ASC")
    suspend fun getAllAllocations(): List<GoalAllocationEntity>

    @Query("SELECT * FROM goal_allocations WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getByTransactionId(transactionId: String): GoalAllocationEntity?

    @Query("SELECT * FROM goal_allocations WHERE linkedExpenseTransactionId = :expenseId LIMIT 1")
    suspend fun getByLinkedExpenseId(expenseId: String): GoalAllocationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: GoalAllocationEntity)

    @Update
    suspend fun update(entry: GoalAllocationEntity)

    @Query("DELETE FROM goal_allocations WHERE id = :id")
    suspend fun deleteById(id: String)
}
