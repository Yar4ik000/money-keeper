package com.moneykeeper.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moneykeeper.core.database.entity.DepositEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DepositDao {

    @Query("SELECT * FROM deposits")
    fun observeAll(): Flow<List<DepositEntity>>

    @Query("SELECT * FROM deposits WHERE isActive = 1")
    fun observeActive(): Flow<List<DepositEntity>>

    @Query("SELECT * FROM deposits WHERE isActive = 1")
    suspend fun getAllActive(): List<DepositEntity>

    @Query("SELECT * FROM deposits WHERE accountId = :accountId LIMIT 1")
    suspend fun getByAccountId(accountId: Long): DepositEntity?

    // Flow-версия для Dashboard-виджета «Заканчивающиеся вклады»
    @Query("""
        SELECT * FROM deposits
        WHERE isActive = 1 AND endDate <= :dateThreshold AND endDate >= :today
        ORDER BY endDate ASC
    """)
    fun observeExpiringSoon(today: String, dateThreshold: String): Flow<List<DepositEntity>>

    @Query("UPDATE deposits SET isActive = 0 WHERE id = :id")
    suspend fun markClosed(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(deposit: DepositEntity): Long

    @Delete
    suspend fun delete(deposit: DepositEntity)
}
