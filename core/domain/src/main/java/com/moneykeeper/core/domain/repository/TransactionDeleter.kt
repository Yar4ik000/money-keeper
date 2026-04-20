package com.moneykeeper.core.domain.repository

interface TransactionDeleter {
    suspend fun deleteMany(ids: Set<Long>)
}
