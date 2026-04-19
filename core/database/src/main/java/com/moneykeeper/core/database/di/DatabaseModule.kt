package com.moneykeeper.core.database.di

import com.moneykeeper.core.database.AppDatabase
import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.database.dao.AccountDao
import com.moneykeeper.core.database.dao.BudgetDao
import com.moneykeeper.core.database.dao.CategoryDao
import com.moneykeeper.core.database.dao.DepositDao
import com.moneykeeper.core.database.dao.RecurringRuleDao
import com.moneykeeper.core.database.dao.TransactionDao
import androidx.room.withTransaction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * AppDatabase провайдится через DatabaseProvider — lazy holder.
     * Hilt вызовет этот метод только когда DAO-потребитель будет впервые запрошен.
     * Если feature-экран компонуется до unlock (нарушение гейтинга из §1.8/§3),
     * [DatabaseProvider.require] бросит понятный IllegalStateException.
     */
    @Provides
    fun provideAppDatabase(provider: DatabaseProvider): AppDatabase = provider.require()

    @Provides fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()
    @Provides fun provideDepositDao(db: AppDatabase): DepositDao = db.depositDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideRecurringRuleDao(db: AppDatabase): RecurringRuleDao = db.recurringRuleDao()
    @Provides fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideTransactionRunner(db: AppDatabase): com.moneykeeper.core.domain.repository.TransactionRunner =
        object : com.moneykeeper.core.domain.repository.TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction(block)
        }
}
