package com.sakuguard.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sakuguard.app.domain.model.CategoryType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: CategoryType,
    val iconKey: String,
    val colorHex: Long,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
)
