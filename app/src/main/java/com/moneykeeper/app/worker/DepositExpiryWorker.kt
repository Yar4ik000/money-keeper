package com.moneykeeper.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moneykeeper.core.domain.calculator.DepositCalculator
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.DepositRepository
import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.domain.repository.SettingsRepository
import com.moneykeeper.app.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@HiltWorker
class DepositExpiryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val databaseProvider: DatabaseProvider,
    private val settingsRepo: SettingsRepository,
    private val depositRepo: DepositRepository,
    private val accountRepo: AccountRepository,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (databaseProvider.state.value !is DatabaseProvider.State.Initialized) return Result.retry()
        if (!settingsRepo.settings.first().depositNotificationsEnabled) return Result.success()

        val today = LocalDate.now()
        val deposits = depositRepo.getAllActive()

        deposits.forEach { deposit ->
            val endDate = deposit.endDate ?: return@forEach
            val daysLeft = ChronoUnit.DAYS.between(today, endDate).toInt()
            val maxThreshold = deposit.notifyDaysBefore.maxOrNull() ?: return@forEach
            if (daysLeft in 0..maxThreshold) {
                val account = accountRepo.getById(deposit.accountId) ?: return@forEach
                val projected = DepositCalculator.projectedBalance(deposit, endDate)
                notificationHelper.showDepositExpiry(
                    depositId     = deposit.id,
                    accountId     = deposit.accountId,
                    accountName   = account.name,
                    daysLeft      = daysLeft,
                    endDate       = endDate,
                    projectedAmount = projected,
                )
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "deposit_expiry_check"
    }
}
