package com.sakuguard.app.data.repository

import com.sakuguard.app.core.database.dao.BudgetDao
import com.sakuguard.app.data.mapper.toDomain
import com.sakuguard.app.data.mapper.toEntity
import com.sakuguard.app.domain.model.Budget
import com.sakuguard.app.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
) : BudgetRepository {
    override fun observeBudgets(includeArchived: Boolean): Flow<List<Budget>> =
        budgetDao.observeBudgets(includeArchived).map { list -> list.map { it.toDomain() } }

    override suspend fun getAllBudgets(): List<Budget> =
        budgetDao.getAllBudgets().map { it.toDomain() }

    override suspend fun getBudgetById(id: String): Budget? =
        budgetDao.getBudgetById(id)?.toDomain()

    override suspend fun createBudget(budget: Budget) {
        budgetDao.insert(budget.toEntity())
    }

    override suspend fun updateBudget(budget: Budget) {
        budgetDao.update(budget.toEntity())
    }

    override suspend fun deleteBudgetById(budgetId: String) {
        budgetDao.deleteById(budgetId)
    }

    override suspend fun setArchived(budgetId: String, archived: Boolean, updatedAt: Long) {
        budgetDao.setArchived(budgetId, archived, updatedAt)
    }

    override suspend fun updateAlertState(budgetId: String, periodKey: String, level: Int, updatedAt: Long) {
        budgetDao.updateAlertState(budgetId, periodKey, level, updatedAt)
    }
}
