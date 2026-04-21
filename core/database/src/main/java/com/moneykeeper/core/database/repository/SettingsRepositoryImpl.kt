package com.moneykeeper.core.database.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moneykeeper.core.domain.repository.AppSettings
import com.moneykeeper.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val DEPOSIT_NOTIFICATIONS    = booleanPreferencesKey("deposit_notifications_enabled")
        val RECURRING_REMINDERS      = booleanPreferencesKey("recurring_reminders_enabled")
        val DEFAULT_NOTIFY_DAYS      = intPreferencesKey("default_notify_days_before")
        val NOTIFICATION_HOUR        = intPreferencesKey("notification_hour")
        val NOTIFICATION_MINUTE      = intPreferencesKey("notification_minute")
        val THEME_MODE               = stringPreferencesKey("theme_mode")
        val CURRENCY_CODE            = stringPreferencesKey("currency_code")
        val ONBOARDING_COMPLETED     = booleanPreferencesKey("onboarding_completed")
        val AUTO_LOCK_TIMEOUT_MINUTES = intPreferencesKey("auto_lock_timeout_minutes")
        val ALLOW_SCREENSHOTS        = booleanPreferencesKey("allow_screenshots")
        val BUDGET_WARNING_THRESHOLD  = intPreferencesKey("budget_warning_threshold")
        val BUDGET_CRITICAL_THRESHOLD = intPreferencesKey("budget_critical_threshold")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            depositNotificationsEnabled = prefs[Keys.DEPOSIT_NOTIFICATIONS] ?: true,
            recurringRemindersEnabled   = prefs[Keys.RECURRING_REMINDERS] ?: true,
            defaultNotifyDaysBefore     = prefs[Keys.DEFAULT_NOTIFY_DAYS] ?: 7,
            notificationHour            = prefs[Keys.NOTIFICATION_HOUR] ?: 8,
            notificationMinute          = prefs[Keys.NOTIFICATION_MINUTE] ?: 0,
            themeMode                   = prefs[Keys.THEME_MODE] ?: "system",
            currencyCode                = prefs[Keys.CURRENCY_CODE] ?: "RUB",
            onboardingCompleted         = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            autoLockTimeoutMinutes      = prefs[Keys.AUTO_LOCK_TIMEOUT_MINUTES] ?: -1,
            allowScreenshots            = prefs[Keys.ALLOW_SCREENSHOTS] ?: true,
            budgetWarningThreshold      = prefs[Keys.BUDGET_WARNING_THRESHOLD] ?: 70,
            budgetCriticalThreshold     = prefs[Keys.BUDGET_CRITICAL_THRESHOLD] ?: 90,
        )
    }

    override suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEPOSIT_NOTIFICATIONS]     = settings.depositNotificationsEnabled
            prefs[Keys.RECURRING_REMINDERS]       = settings.recurringRemindersEnabled
            prefs[Keys.DEFAULT_NOTIFY_DAYS]       = settings.defaultNotifyDaysBefore
            prefs[Keys.NOTIFICATION_HOUR]         = settings.notificationHour
            prefs[Keys.NOTIFICATION_MINUTE]       = settings.notificationMinute
            prefs[Keys.THEME_MODE]                = settings.themeMode
            prefs[Keys.CURRENCY_CODE]             = settings.currencyCode
            prefs[Keys.ONBOARDING_COMPLETED]      = settings.onboardingCompleted
            prefs[Keys.AUTO_LOCK_TIMEOUT_MINUTES] = settings.autoLockTimeoutMinutes
            prefs[Keys.ALLOW_SCREENSHOTS]         = settings.allowScreenshots
            prefs[Keys.BUDGET_WARNING_THRESHOLD]  = settings.budgetWarningThreshold
            prefs[Keys.BUDGET_CRITICAL_THRESHOLD] = settings.budgetCriticalThreshold
        }
    }
}
