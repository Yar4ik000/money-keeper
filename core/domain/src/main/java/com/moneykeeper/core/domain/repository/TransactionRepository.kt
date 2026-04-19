package com.moneykeeper.core.domain.repository

import com.moneykeeper.core.domain.analytics.CategorySum
import com.moneykeeper.core.domain.analytics.MonthlyBarEntry
import com.moneykeeper.core.domain.analytics.PeriodSummaryByCurrency
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TransactionRepository {
    fun observe(
        accountId: Long? = null,
        categoryId: Long? = null,
        type: TransactionType? = null,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<TransactionWithMeta>>

    fun observeRecent(limit: Int): Flow<List<TransactionWithMeta>>
    fun observePeriodSummary(from: LocalDate, to: LocalDate): Flow<List<PeriodSummaryByCurrency>>
    fun observeExpensesByCategory(currency: String, from: LocalDate, to: LocalDate): Flow<List<CategorySum>>
    fun observeMonthlyTrend(currency: String, months: Int): Flow<List<MonthlyBarEntry>>

    suspend fun getById(id: Long): Transaction?
    suspend fun getByIds(ids: Set<Long>): List<Transaction>
    suspend fun save(transaction: Transaction): Long
    suspend fun delete(id: Long)
    suspend fun deleteByIds(ids: Set<Long>)
}
