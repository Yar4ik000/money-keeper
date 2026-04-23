package com.moneykeeper.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moneykeeper.core.database.entity.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rules")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE endDate IS NULL OR endDate >= :today")
    suspend fun getAllActive(today: String): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRuleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: RecurringRuleEntity): Long

    // Use @Update (SQL UPDATE) instead of @Insert(REPLACE) to avoid the DELETE→INSERT
    // cycle that would trigger ON DELETE SET NULL and wipe recurringRuleId on transactions.
    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Query("UPDATE recurring_rules SET lastGeneratedDate = :date WHERE id = :id")
    suspend fun updateLastGeneratedDate(id: Long, date: String)

    @Delete
    suspend fun delete(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE id NOT IN (SELECT DISTINCT recurringRuleId FROM transactions WHERE recurringRuleId IS NOT NULL)")
    suspend fun deleteOrphaned(): Int
}
