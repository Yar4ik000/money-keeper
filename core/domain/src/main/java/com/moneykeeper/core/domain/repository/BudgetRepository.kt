package com.moneykeeper.core.domain.repository

import com.moneykeeper.core.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeAll(): Flow<List<Budget>>
    suspend fun save(budget: Budget): Long
    suspend fun delete(id: Long)
}
