package com.sakuguard.app.core.database

import com.sakuguard.app.core.database.entity.CategoryEntity
import com.sakuguard.app.domain.model.CategoryType

/**
 * Provides deterministic default categories for initial database seeding.
 */
object SakuDatabaseSeeder {
    fun defaultCategories(createdAt: Long = 0L): List<CategoryEntity> = listOf(
        // Income
        CategoryEntity("cat-inc-salary", "Salary", CategoryType.INCOME, "payments", 0xFF039855L, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-inc-freelance", "Freelance", CategoryType.INCOME, "work", 0xFF0F766EL, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-inc-bonus", "Bonus", CategoryType.INCOME, "redeem", 0xFF166534L, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-inc-business", "Business", CategoryType.INCOME, "storefront", 0xFF047857L, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-inc-investment", "Investment", CategoryType.INCOME, "trending_up", 0xFF0D9488L, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-inc-other", "Other Income", CategoryType.INCOME, "add_circle", 0xFF64748BL, isDefault = true, isArchived = false, createdAt = createdAt),
        // Expense
        CategoryEntity("cat-exp-food", "Food", CategoryType.EXPENSE, "restaurant", 0xFFD97706L, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-exp-transport", "Transportation", CategoryType.EXPENSE, "directions_car", 0xFF2563EBL, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-exp-shopping", "Shopping", CategoryType.EXPENSE, "shopping_bag", 0xFFDB2777L, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-exp-household", "Household", CategoryType.EXPENSE, "home", 0xFF0F766EL, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-exp-bills", "Bills", CategoryType.EXPENSE, "receipt_long", 0xFFDC2626L, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-exp-health", "Health", CategoryType.EXPENSE, "medical_services", 0xFF059669L, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-exp-education", "Education", CategoryType.EXPENSE, "school", 0xFF0284C7L, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-exp-entertainment", "Entertainment", CategoryType.EXPENSE, "movie", 0xFFEA580CL, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-exp-travel", "Travel", CategoryType.EXPENSE, "flight", 0xFF0891B2L, isDefault = true, isArchived = false, createdAt = createdAt),
        CategoryEntity("cat-exp-other", "Other Expenses", CategoryType.EXPENSE, "more_horiz", 0xFF64748BL, isDefault = true, isArchived = false, createdAt = createdAt),
    )
}
