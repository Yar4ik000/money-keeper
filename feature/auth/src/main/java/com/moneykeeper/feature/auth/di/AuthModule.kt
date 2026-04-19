package com.moneykeeper.feature.auth.di

import com.moneykeeper.feature.auth.domain.MasterKeyDerivation
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideMasterKeyDerivation(): MasterKeyDerivation = MasterKeyDerivation()
}
