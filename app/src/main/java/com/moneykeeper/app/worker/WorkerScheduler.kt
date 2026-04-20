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

    fun scheduleAll(context: Context, hour: Int = 8, minute: Int = 0) {
        val wm = WorkManager.getInstance(context)

        wm.enqueueUniquePeriodicWork(
            DepositExpiryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DepositExpiryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(calculateDelayUntil(hour, minute), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().build())
                .build(),
        )

        wm.enqueueUniquePeriodicWork(
            RecurringTransactionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecurringTransactionWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(calculateDelayUntil(hour, minute) + 60_000L, TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    fun reschedule(context: Context, hour: Int, minute: Int) {
        val wm = WorkManager.getInstance(context)

        wm.enqueueUniquePeriodicWork(
            DepositExpiryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            PeriodicWorkRequestBuilder<DepositExpiryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(calculateDelayUntil(hour, minute), TimeUnit.MILLISECONDS)
                .build(),
        )

        wm.enqueueUniquePeriodicWork(
            RecurringTransactionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            PeriodicWorkRequestBuilder<RecurringTransactionWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(calculateDelayUntil(hour, minute) + 60_000L, TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    fun calculateDelayUntil(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        val todayTarget = now.toLocalDate().atTime(hour, minute)
        val target = if (now.isBefore(todayTarget)) todayTarget else todayTarget.plusDays(1)
        return ChronoUnit.MILLIS.between(now, target)
    }
}
