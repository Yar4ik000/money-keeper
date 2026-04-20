package com.moneykeeper.core.database.di

import com.moneykeeper.core.database.dao.AccountDao
import com.moneykeeper.core.database.dao.BudgetDao
import com.moneykeeper.core.database.dao.CategoryDao
import com.moneykeeper.core.database.dao.DepositDao
import com.moneykeeper.core.database.dao.RecurringRuleDao
import com.moneykeeper.core.database.dao.TransactionDao
import android.content.Context
import com.moneykeeper.core.database.repository.AccountRepositoryImpl
import com.moneykeeper.core.database.repository.BudgetRepositoryImpl
import com.moneykeeper.core.database.repository.CategoryRepositoryImpl
import com.moneykeeper.core.database.repository.DepositRepositoryImpl
import com.moneykeeper.core.database.repository.RecurringRuleRepositoryImpl
import com.moneykeeper.core.database.repository.SettingsRepositoryImpl
import com.moneykeeper.core.database.repository.TransactionRepositoryImpl
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.BudgetRepository
import com.moneykeeper.core.domain.repository.CategoryRepository
import com.moneykeeper.core.domain.repository.DepositRepository
import com.moneykeeper.core.domain.repository.RecurringRuleRepository
import com.moneykeeper.core.domain.repository.SettingsRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides @Singleton
    fun provideAccountRepository(dao: AccountDao): AccountRepository =
        AccountRepositoryImpl(dao)

    @Provides @Singleton
    fun provideDepositRepository(dao: DepositDao): DepositRepository =
        DepositRepositoryImpl(dao)

    @Provides @Singleton
    fun provideCategoryRepository(dao: CategoryDao): CategoryRepository =
        CategoryRepositoryImpl(dao)

    @Provides @Singleton
    fun provideTransactionRepository(
        txDao: TransactionDao,
        accountDao: AccountDao,
        categoryDao: CategoryDao,
    ): TransactionRepository = TransactionRepositoryImpl(txDao, accountDao, categoryDao)

    @Provides @Singleton
    fun provideRecurringRuleRepository(
        ruleDao: RecurringRuleDao,
        txDao: TransactionDao,
        accountDao: AccountDao,
        categoryDao: CategoryDao,
    ): RecurringRuleRepository = RecurringRuleRepositoryImpl(ruleDao, txDao, accountDao, categoryDao)

    @Provides @Singleton
    fun provideBudgetRepository(dao: BudgetDao): BudgetRepository =
        BudgetRepositoryImpl(dao)

    @Provides @Singleton
    fun provideSettingsRepository(@ApplicationContext ctx: Context): SettingsRepository =
        SettingsRepositoryImpl(ctx)
}
