package com.moneykeeper.feature.transactions.di

import com.moneykeeper.core.domain.repository.TransactionDeleter
import com.moneykeeper.feature.transactions.domain.TransactionSaver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TransactionModule {
    @Binds
    abstract fun bindTransactionDeleter(impl: TransactionSaver): TransactionDeleter
}
