package com.sakuguard.app.domain.repository

import com.sakuguard.app.domain.model.Goal
import com.sakuguard.app.domain.model.GoalAllocationEntry
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeGoals(includeArchived: Boolean = true): Flow<List<Goal>>
    fun observeAllocations(): Flow<List<GoalAllocationEntry>>
    suspend fun getAllGoals(): List<Goal>
    suspend fun getGoalById(id: String): Goal?
    suspend fun getAllAllocations(): List<GoalAllocationEntry>
    suspend fun createGoal(goal: Goal)
    suspend fun updateGoal(goal: Goal)
    suspend fun updateProtectionMode(goalId: String, mode: ProtectionMode, updatedAt: Long)
    suspend fun updateStatus(goalId: String, status: GoalStatus, completedAt: Long?, updatedAt: Long)
}
