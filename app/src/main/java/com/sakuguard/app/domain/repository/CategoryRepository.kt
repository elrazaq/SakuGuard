package com.sakuguard.app.domain.repository

import com.sakuguard.app.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(includeArchived: Boolean = true): Flow<List<Category>>
    suspend fun getAllCategories(): List<Category>
    suspend fun getCategoryById(id: String): Category?
    suspend fun upsertCategory(category: Category)
    suspend fun setArchived(categoryId: String, archived: Boolean)
}
