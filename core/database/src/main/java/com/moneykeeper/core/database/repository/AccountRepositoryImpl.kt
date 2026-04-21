package com.moneykeeper.core.database.repository

import com.moneykeeper.core.database.dao.AccountDao
import com.moneykeeper.core.database.entity.toDomain
import com.moneykeeper.core.database.entity.toEntity
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.money.CurrencyAmount
import com.moneykeeper.core.domain.money.MultiCurrencyTotal
import com.moneykeeper.core.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

class AccountRepositoryImpl(private val dao: AccountDao) : AccountRepository {

    override fun observeActiveAccounts(): Flow<List<Account>> =
        dao.observeActive().map { it.map { e -> e.toDomain() } }

    override fun observeAllAccounts(): Flow<List<Account>> =
        dao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeTotalsByCurrency(): Flow<MultiCurrencyTotal> =
        dao.observeTotalsByCurrency().map { rows ->
            MultiCurrencyTotal(rows.map { CurrencyAmount(it.currency, it.total) })
        }

    override suspend fun getById(id: Long): Account? = dao.getById(id)?.toDomain()

    override suspend fun save(account: Account): Long = dao.upsert(account.toEntity())

    override suspend fun archive(id: Long) = dao.archive(id)

    override suspend fun unarchive(id: Long) = dao.unarchive(id)

    override suspend fun updateSortOrders(orderedIds: List<Long>) =
        dao.updateSortOrders(orderedIds)

    override suspend fun delete(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    override suspend fun adjustBalance(id: Long, delta: BigDecimal) {
        val current = dao.getById(id)?.balance ?: return
        dao.setBalance(id, current + delta)
    }
}
