package com.sakuguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sakuguard.app.domain.model.GoalStatus
import com.sakuguard.app.domain.model.ProtectionMode

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetAmount: Long,
    val deadlineEpochMillis: Long?,
    val description: String,
    val iconKey: String?,
    val colorHex: Long?,
    val priority: Int,
    val status: GoalStatus,
    val protectionMode: ProtectionMode,
    val createdAt: Long,
    val completedAt: Long?,
    val updatedAt: Long,
)
