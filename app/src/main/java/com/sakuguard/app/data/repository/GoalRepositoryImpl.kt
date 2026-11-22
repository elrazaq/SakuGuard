package com.sakuguard.app.data.repository

import com.sakuguard.app.core.database.dao.GoalAllocationDao
import com.sakuguard.app.core.database.dao.GoalDao
import com.sakuguard.app.data.mapper.toDomain
import com.sakuguard.app.data.mapper.toEntity
import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.GoalLedgerType
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode
import com.sakuguard.app.domain.repository.GoalPeriodContributionTotals
import com.sakuguard.app.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GoalRepositoryImpl @Inject constructor(
    private val goalDao: GoalDao,
    private val goalAllocationDao: GoalAllocationDao,
) : GoalRepository {
    override fun observeGoals(includeArchived: Boolean): Flow<List<Goal>> =
        goalDao.observeGoals(includeArchived).map { list -> list.map { it.toDomain() } }

    override fun observeAllocations(): Flow<List<GoalAllocationEntry>> =
        goalAllocationDao.observeAllocations().map { list -> list.map { it.toDomain() } }

    override fun observePeriodGoalContributions(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<GoalPeriodContributionTotals> =
        goalAllocationDao.observePeriodGoalContributions(startInclusive, endExclusive).map { tuples ->
            GoalPeriodContributionTotals(
                allocated = tuples.firstOrNull { it.entryType == GoalLedgerType.ALLOCATION }?.totalAmount ?: 0L,
                withdrawn = tuples.firstOrNull { it.entryType == GoalLedgerType.WITHDRAWAL }?.totalAmount ?: 0L,
            )
        }

    override suspend fun getAllGoals(): List<Goal> =
        goalDao.getAllGoals().map { it.toDomain() }

    override suspend fun getGoalById(id: String): Goal? =
        goalDao.getGoalById(id)?.toDomain()

    override suspend fun getAllAllocations(): List<GoalAllocationEntry> =
        goalAllocationDao.getAllAllocations().map { it.toDomain() }

    override suspend fun createGoal(goal: Goal) {
        goalDao.insert(goal.toEntity())
    }

    override suspend fun updateGoal(goal: Goal) {
        goalDao.update(goal.toEntity())
    }

    override suspend fun updateProtectionMode(goalId: String, mode: ProtectionMode, updatedAt: Long) {
        goalDao.updateProtectionMode(goalId, mode, updatedAt)
    }

    override suspend fun updateStatus(goalId: String, status: GoalStatus, completedAt: Long?, updatedAt: Long) {
        goalDao.updateStatus(goalId, status, completedAt, updatedAt)
    }
}
