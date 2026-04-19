package com.moneykeeper.core.domain.repository

import com.moneykeeper.core.domain.model.Deposit
import kotlinx.coroutines.flow.Flow

interface DepositRepository {
    fun observeAll(): Flow<List<Deposit>>
    fun observeExpiringSoon(daysThreshold: Int): Flow<List<Deposit>>
    suspend fun getAllActive(): List<Deposit>
    suspend fun getByAccountId(accountId: Long): Deposit?
    suspend fun save(deposit: Deposit): Long
    suspend fun markClosed(id: Long)
}
