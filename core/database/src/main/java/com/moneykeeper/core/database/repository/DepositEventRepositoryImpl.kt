package com.moneykeeper.core.database.repository

import com.moneykeeper.core.database.dao.DepositEventDao
import com.moneykeeper.core.database.entity.toDomain
import com.moneykeeper.core.database.entity.toEntity
import com.moneykeeper.core.domain.model.DepositEvent
import com.moneykeeper.core.domain.repository.DepositEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DepositEventRepositoryImpl(
    private val dao: DepositEventDao,
) : DepositEventRepository {

    override fun observe(depositId: Long): Flow<List<DepositEvent>> =
        dao.observe(depositId).map { it.map { e -> e.toDomain() } }

    override suspend fun getAll(depositId: Long): List<DepositEvent> =
        dao.getAll(depositId).map { it.toDomain() }

    override suspend fun insert(event: DepositEvent): Long =
        dao.insert(event.toEntity())

    override suspend fun delete(id: Long) =
        dao.delete(id)

    override suspend fun deleteAll(depositId: Long) =
        dao.deleteAll(depositId)
}
