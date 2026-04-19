package com.moneykeeper.core.database.repository

import com.moneykeeper.core.database.dao.BudgetDao
import com.moneykeeper.core.database.entity.toDomain
import com.moneykeeper.core.database.entity.toEntity
import com.moneykeeper.core.domain.model.Budget
import com.moneykeeper.core.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BudgetRepositoryImpl(private val dao: BudgetDao) : BudgetRepository {

    override fun observeAll(): Flow<List<Budget>> =
        dao.observeAll().map { it.map { e -> e.toDomain() } }

    override suspend fun save(budget: Budget): Long = dao.upsert(budget.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
