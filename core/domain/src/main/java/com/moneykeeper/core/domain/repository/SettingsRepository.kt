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
    val onboardingCompleted: Boolean = false,
    /** Minutes before auto-lock when app goes to background. -1 = disabled, 0 = immediate. */
    val autoLockTimeoutMinutes: Int = -1,
    /** Allow screenshots and screen recording. When false, FLAG_SECURE is set. */
    val allowScreenshots: Boolean = true,
    /** Global default: spent% at which budget bar turns yellow. */
    val budgetWarningThreshold: Int = 70,
    /** Global default: spent% at which budget bar turns red and nav badge is shown. */
    val budgetCriticalThreshold: Int = 90,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
}
