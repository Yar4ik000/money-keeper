package com.moneykeeper.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.moneykeeper.core.database.converter.Converters
import com.moneykeeper.core.database.dao.AccountDao
import com.moneykeeper.core.database.dao.BudgetDao
import com.moneykeeper.core.database.dao.CategoryDao
import com.moneykeeper.core.database.dao.DepositDao
import com.moneykeeper.core.database.dao.RecurringRuleDao
import com.moneykeeper.core.database.dao.TransactionDao
import com.moneykeeper.core.database.entity.AccountEntity
import com.moneykeeper.core.database.entity.BudgetEntity
import com.moneykeeper.core.database.entity.CategoryEntity
import com.moneykeeper.core.database.entity.DepositEntity
import com.moneykeeper.core.database.entity.RecurringRuleEntity
import com.moneykeeper.core.database.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        DepositEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringRuleEntity::class,
        BudgetEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
    // При каждом bump версии — добавлять AutoMigration(from = N, to = N+1) здесь
    autoMigrations = [],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun depositDao(): DepositDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        const val DB_NAME = "money_keeper.db"
        const val VERSION = 2

        // Ручные миграции — когда AutoMigration не справляется (переименование колонок, преобразование данных)
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}
