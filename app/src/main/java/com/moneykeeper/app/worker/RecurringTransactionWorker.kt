package com.moneykeeper.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moneykeeper.core.domain.usecase.GenerateRecurringTransactionsUseCase
import com.moneykeeper.feature.auth.domain.MasterKeyHolder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RecurringTransactionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val masterKeyHolder: MasterKeyHolder,
    private val generateUseCase: GenerateRecurringTransactionsUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!masterKeyHolder.isSet()) return Result.retry()
        generateUseCase()
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "recurring_transaction_generator"
    }
}
