package com.moneykeeper.core.domain.repository

fun interface WorkScheduler {
    fun rescheduleNotifications(hour: Int, minute: Int)
}
