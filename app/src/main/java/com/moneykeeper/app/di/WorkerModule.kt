package com.moneykeeper.app.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.moneykeeper.app.worker.WorkerScheduler
import com.moneykeeper.core.domain.repository.WorkScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    @Provides
    fun provideWorkManagerConfiguration(
        workerFactory: HiltWorkerFactory,
    ): Configuration = Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()

    @Provides
    @Singleton
    fun provideWorkScheduler(@ApplicationContext context: Context): WorkScheduler =
        WorkScheduler { hour, minute -> WorkerScheduler.reschedule(context, hour, minute) }
}
