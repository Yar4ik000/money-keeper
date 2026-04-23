package com.moneykeeper.core.domain.repository

interface TransactionDeleter {
    suspend fun deleteMany(ids: Set<Long>)
    /** Delete selected transactions AND delete their recurring rules, stopping future generation. */
    suspend fun deleteManyStopSeries(ids: Set<Long>)
}
