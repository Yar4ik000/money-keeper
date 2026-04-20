package com.moneykeeper.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

object WorkerScheduler {

    fun scheduleAll(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val depositCheckRequest = PeriodicWorkRequestBuilder<DepositExpiryWorker>(
            repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.DAYS,
        )
            .setInitialDelay(calculateDelayUntilMorning(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().build())
            .build()

        workManager.enqueueUniquePeriodicWork(
            DepositExpiryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            depositCheckRequest,
        )

        val recurringRequest = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(
            repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.DAYS,
        )
            .setInitialDelay(calculateDelayUntilMorning() + 60_000L, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            RecurringTransactionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            recurringRequest,
        )
    }

    private fun calculateDelayUntilMorning(): Long {
        val now = LocalDateTime.now()
        val target = if (now.hour < 8)
            now.toLocalDate().atTime(8, 0)
        else
            now.toLocalDate().plusDays(1).atTime(8, 0)
        return ChronoUnit.MILLIS.between(now, target)
    }
}
