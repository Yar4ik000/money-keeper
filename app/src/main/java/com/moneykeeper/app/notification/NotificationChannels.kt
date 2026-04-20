package com.moneykeeper.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val DEPOSIT_EXPIRY       = "deposit_expiry"
    const val RECURRING_REMINDERS  = "recurring_reminders"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                DEPOSIT_EXPIRY,
                "Вклады",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Уведомления об истекающих вкладах" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                RECURRING_REMINDERS,
                "Напоминания",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Напоминания о запланированных транзакциях" }
        )
    }
}
