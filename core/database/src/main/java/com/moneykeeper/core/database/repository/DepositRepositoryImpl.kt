package com.moneykeeper.core.database.repository

import com.moneykeeper.core.database.dao.DepositDao
import com.moneykeeper.core.database.entity.toDomain
import com.moneykeeper.core.database.entity.toEntity
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.repository.DepositRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class DepositRepositoryImpl(private val dao: DepositDao) : DepositRepository {

    override fun observeAll(): Flow<List<Deposit>> =
        dao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeExpiringSoon(daysThreshold: Int): Flow<List<Deposit>> {
        val today = LocalDate.now().toString()
        val threshold = LocalDate.now().plusDays(daysThreshold.toLong()).toString()
        return dao.observeExpiringSoon(today, threshold).map { it.map { e -> e.toDomain() } }
    }

    override suspend fun getAllActive(): List<Deposit> =
        dao.getAllActive().map { it.toDomain() }

    override suspend fun getByAccountId(accountId: Long): Deposit? =
        dao.getByAccountId(accountId)?.toDomain()

    override suspend fun save(deposit: Deposit): Long = dao.upsert(deposit.toEntity())

    override suspend fun markClosed(id: Long) = dao.markClosed(id)
}
