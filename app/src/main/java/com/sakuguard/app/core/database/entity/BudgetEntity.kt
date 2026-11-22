package com.sakuguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sakuguard.app.domain.model.BudgetPeriodType

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("categoryId"),
        Index(value = ["isArchived", "startDate", "endDateExclusive"]),
    ],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String,
    val limitAmount: Long,
    val periodType: BudgetPeriodType,
    val startDate: Long,
    val endDateExclusive: Long,
    val alert80Enabled: Boolean,
    val alert100Enabled: Boolean,
    val lastAlertedPeriodKey: String?,
    val lastAlertedLevel: Int,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
