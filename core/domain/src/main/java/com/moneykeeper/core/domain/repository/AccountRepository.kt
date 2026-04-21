package com.moneykeeper.core.domain.repository

import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.money.MultiCurrencyTotal
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface AccountRepository {
    fun observeActiveAccounts(): Flow<List<Account>>
    fun observeAllAccounts(): Flow<List<Account>>
    fun observeTotalsByCurrency(): Flow<MultiCurrencyTotal>
    suspend fun getById(id: Long): Account?
    suspend fun save(account: Account): Long
    suspend fun archive(id: Long)
    suspend fun unarchive(id: Long)
    suspend fun updateSortOrders(orderedIds: List<Long>)
    suspend fun delete(id: Long)
    suspend fun adjustBalance(id: Long, delta: BigDecimal)
}
