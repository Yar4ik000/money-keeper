package com.moneykeeper.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moneykeeper.core.database.entity.DepositEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DepositEventDao {

    @Query("SELECT * FROM deposit_events WHERE depositId = :depositId ORDER BY date DESC, id DESC")
    fun observe(depositId: Long): Flow<List<DepositEventEntity>>

    @Query("SELECT * FROM deposit_events WHERE depositId = :depositId ORDER BY date DESC, id DESC")
    suspend fun getAll(depositId: Long): List<DepositEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DepositEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<DepositEventEntity>)

    @Query("DELETE FROM deposit_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM deposit_events WHERE depositId = :depositId")
    suspend fun deleteAll(depositId: Long)

    @Query("SELECT * FROM deposit_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DepositEventEntity?
}
