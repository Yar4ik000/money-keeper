package com.moneykeeper.core.domain.repository

import com.moneykeeper.core.domain.model.DepositEvent
import kotlinx.coroutines.flow.Flow

interface DepositEventRepository {
    fun observe(depositId: Long): Flow<List<DepositEvent>>
    suspend fun getAll(depositId: Long): List<DepositEvent>
    suspend fun insert(event: DepositEvent): Long
    suspend fun delete(id: Long)
    suspend fun deleteAll(depositId: Long)
}
