package com.moneykeeper.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneykeeper.core.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY sortOrder ASC")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    // Суммы по валютам — приложение не конвертирует, показывает каждую валюту отдельно
    @Query("""
        SELECT currency, SUM(balance) AS total
        FROM accounts
        WHERE isArchived = 0
        GROUP BY currency
        ORDER BY currency
    """)
    fun observeTotalsByCurrency(): Flow<List<CurrencyTotalRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET balance = balance + :delta WHERE id = :id")
    suspend fun adjustBalance(id: Long, delta: BigDecimal)

    @Query("UPDATE accounts SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Delete
    suspend fun delete(account: AccountEntity)
}

// Проекция для агрегирующего запроса — маппится в MultiCurrencyTotal в репозитории
data class CurrencyTotalRow(val currency: String, val total: BigDecimal)
