package com.moneykeeper.core.domain.repository

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val depositNotificationsEnabled: Boolean = true,
    val recurringRemindersEnabled: Boolean = true,
    val defaultNotifyDaysBefore: Int = 7,
    val notificationHour: Int = 8,
    val notificationMinute: Int = 0,
    val themeMode: String = "system",
    val currencyCode: String = "RUB",
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
}
