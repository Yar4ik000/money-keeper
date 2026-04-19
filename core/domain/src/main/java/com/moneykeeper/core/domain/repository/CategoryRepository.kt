package com.moneykeeper.core.domain.repository

import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    fun observeByType(type: CategoryType): Flow<List<Category>>
    fun observeRootCategories(): Flow<List<Category>>
    suspend fun getById(id: Long): Category?
    suspend fun save(category: Category): Long
    suspend fun delete(id: Long)
}
