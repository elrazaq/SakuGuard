package com.sakuguard.app.data.repository

import com.sakuguard.app.core.database.dao.CategoryDao
import com.sakuguard.app.data.mapper.toDomain
import com.sakuguard.app.data.mapper.toEntity
import com.sakuguard.app.domain.model.Category
import com.sakuguard.app.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
) : CategoryRepository {
    override fun observeCategories(includeArchived: Boolean): Flow<List<Category>> =
        categoryDao.observeCategories(includeArchived).map { list -> list.map { it.toDomain() } }

    override suspend fun getAllCategories(): List<Category> =
        categoryDao.getAllCategories().map { it.toDomain() }

    override suspend fun getCategoryById(id: String): Category? =
        categoryDao.getCategoryById(id)?.toDomain()

    override suspend fun upsertCategory(category: Category) {
        categoryDao.upsert(category.toEntity())
    }

    override suspend fun setArchived(categoryId: String, archived: Boolean) {
        categoryDao.setArchived(categoryId, archived)
    }
}
