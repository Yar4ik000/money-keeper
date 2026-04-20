package com.moneykeeper.app.di

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    @Provides
    fun provideWorkManagerConfiguration(
        workerFactory: HiltWorkerFactory,
    ): Configuration = Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()
}
