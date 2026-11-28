package com.sakuguard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sakuguard.app.core.database.entity.GoalEntity
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE (:includeArchived = 1 OR status != 'ARCHIVED') ORDER BY priority DESC, createdAt ASC")
    fun observeGoals(includeArchived: Boolean): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals ORDER BY priority DESC, createdAt ASC")
    suspend fun getAllGoals(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoalById(id: String): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(goals: List<GoalEntity>)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("UPDATE goals SET protectionMode = :mode, updatedAt = :updatedAt WHERE id = :goalId")
    suspend fun updateProtectionMode(goalId: String, mode: ProtectionMode, updatedAt: Long)

    @Query("UPDATE goals SET status = :status, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :goalId")
    suspend fun updateStatus(goalId: String, status: GoalStatus, completedAt: Long?, updatedAt: Long)

    @Query("DELETE FROM goals WHERE id = :goalId")
    suspend fun deleteById(goalId: String)
}
