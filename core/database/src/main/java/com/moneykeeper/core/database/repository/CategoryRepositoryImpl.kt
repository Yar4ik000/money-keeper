package com.moneykeeper.core.database.repository

import com.moneykeeper.core.database.dao.CategoryDao
import com.moneykeeper.core.database.entity.toDomain
import com.moneykeeper.core.database.entity.toEntity
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.core.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepositoryImpl(private val dao: CategoryDao) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeByType(type: CategoryType): Flow<List<Category>> =
        dao.observeByType(type.name).map { it.map { e -> e.toDomain() } }

    override fun observeRootCategories(): Flow<List<Category>> =
        dao.observeRootCategories().map { it.map { e -> e.toDomain() } }

    override suspend fun getById(id: Long): Category? = dao.getById(id)?.toDomain()

    override suspend fun save(category: Category): Long = dao.upsert(category.toEntity())

    override suspend fun delete(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }
}
