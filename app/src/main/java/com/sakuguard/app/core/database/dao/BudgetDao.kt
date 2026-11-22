package com.sakuguard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sakuguard.app.core.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE (:includeArchived = 1 OR isArchived = 0) ORDER BY createdAt ASC")
    fun observeBudgets(includeArchived: Boolean): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY createdAt ASC")
    suspend fun getAllBudgets(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getBudgetById(id: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: BudgetEntity)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE budgets SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :budgetId")
    suspend fun setArchived(budgetId: String, archived: Boolean, updatedAt: Long)

    @Query(
        "UPDATE budgets SET lastAlertedPeriodKey = :periodKey, lastAlertedLevel = :level, updatedAt = :updatedAt WHERE id = :budgetId",
    )
    suspend fun updateAlertState(budgetId: String, periodKey: String, level: Int, updatedAt: Long)
}
