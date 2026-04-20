package com.moneykeeper.app.di

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.moneykeeper.app.worker.DepositExpiryWorker
import com.moneykeeper.app.worker.RecurringTransactionWorker
import com.moneykeeper.core.domain.usecase.CatchUpRecurringTransactionsUseCase
import com.moneykeeper.feature.auth.di.ApplicationScope
import com.moneykeeper.feature.auth.domain.PostUnlockCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Provider

@Module
@InstallIn(SingletonComponent::class)
object PostUnlockModule {

    @Provides
    @IntoSet
    fun providePostUnlockCallback(
        @ApplicationScope appScope: CoroutineScope,
        // Provider<> defers construction until onUnlocked() — prevents DB-not-ready crash
        // when this callback is injected into UnlockController before the DB is opened.
        catchUpProvider: Provider<CatchUpRecurringTransactionsUseCase>,
        @ApplicationContext context: Context,
    ): PostUnlockCallback = PostUnlockCallback {
        appScope.launch(Dispatchers.IO) {
            runCatching { catchUpProvider.get()() }
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniqueWork(
                DepositExpiryWorker.WORK_NAME + "_catchup",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DepositExpiryWorker>().build(),
            )
            wm.enqueueUniqueWork(
                RecurringTransactionWorker.WORK_NAME + "_catchup",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RecurringTransactionWorker>().build(),
            )
        }
    }
}
