package com.sakuguard.app.domain.repository

import com.sakuguard.app.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeBudgets(includeArchived: Boolean = true): Flow<List<Budget>>
    suspend fun getAllBudgets(): List<Budget>
    suspend fun getBudgetById(id: String): Budget?
    suspend fun createBudget(budget: Budget)
    suspend fun updateBudget(budget: Budget)
    suspend fun deleteBudgetById(budgetId: String)
    suspend fun setBudgetArchived(budgetId: String, archived: Boolean, updatedAt: Long)
    suspend fun updateAlertState(budgetId: String, periodKey: String, level: Int, updatedAt: Long)
}
