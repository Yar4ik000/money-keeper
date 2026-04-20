package com.moneykeeper.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moneykeeper.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface TransactionDao {

    @Query("""
        SELECT * FROM transactions
        WHERE (:accountId IS NULL OR accountId = :accountId OR toAccountId = :accountId)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:type IS NULL OR type = :type)
          AND date BETWEEN :from AND :to
        ORDER BY date DESC, createdAt DESC
    """)
    fun observe(
        accountId: Long?,
        categoryId: Long?,
        type: String?,
        from: String,
        to: String,
    ): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        ORDER BY date DESC, createdAt DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE recurringRuleId = :ruleId ORDER BY date DESC LIMIT 1")
    suspend fun getLastByRule(ruleId: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TransactionEntity>

    // Агрегация операций по категориям — только для одной валюты (смешивать ₽ и $ бессмысленно).
    // COALESCE(categoryId, 0): транзакции без категории объединяются под id=0 ("Без категории").
    @Query("""
        SELECT COALESCE(t.categoryId, 0) AS categoryId, SUM(t.amount) AS total, COUNT(*) AS count
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE t.type = :type
          AND a.currency = :currency
          AND t.date BETWEEN :from AND :to
        GROUP BY COALESCE(t.categoryId, 0)
    """)
    fun observeByCategory(currency: String, from: String, to: String, type: String): Flow<List<CategorySumRow>>

    // Агрегация операций по счетам — только для одной валюты
    @Query("""
        SELECT t.accountId AS accountId, SUM(t.amount) AS total, COUNT(*) AS count
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE t.type = :type
          AND a.currency = :currency
          AND t.date BETWEEN :from AND :to
        GROUP BY t.accountId
    """)
    fun observeByAccount(currency: String, from: String, to: String, type: String): Flow<List<AccountSumRow>>

    // Итоги доходов/расходов за период, раздельно по валютам
    @Query("""
        SELECT a.currency AS currency,
               SUM(CASE WHEN t.type = 'INCOME'  THEN t.amount ELSE 0 END) AS income,
               SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) AS expense
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE t.date BETWEEN :from AND :to
        GROUP BY a.currency
    """)
    fun observePeriodSummary(from: String, to: String): Flow<List<PeriodSummaryRow>>

    // Месячный тренд для графика в Аналитике — только одна валюта, группировка по YYYY-MM
    @Query("""
        SELECT substr(t.date, 1, 7) AS yearMonth,
               SUM(CASE WHEN t.type = 'INCOME'  THEN t.amount ELSE 0 END) AS income,
               SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) AS expense
        FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE a.currency = :currency
          AND t.date BETWEEN :from AND :to
        GROUP BY substr(t.date, 1, 7)
        ORDER BY yearMonth ASC
    """)
    fun observeMonthlyTrend(currency: String, from: String, to: String): Flow<List<MonthlyTrendRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: TransactionEntity): Long

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

// DAO-проекции — маппятся в domain-типы в репозитории
data class CategorySumRow(val categoryId: Long, val total: BigDecimal, val count: Int)
data class AccountSumRow(val accountId: Long, val total: BigDecimal, val count: Int)
data class PeriodSummaryRow(val currency: String, val income: BigDecimal, val expense: BigDecimal)
data class MonthlyTrendRow(val yearMonth: String, val income: BigDecimal, val expense: BigDecimal)
